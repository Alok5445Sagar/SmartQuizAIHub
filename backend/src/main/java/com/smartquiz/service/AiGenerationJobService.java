package com.smartquiz.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.smartquiz.ai.AIService;
import com.smartquiz.dto.AIGenerationRequest;
import com.smartquiz.dto.AiGenerationJobResponse;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.AiGenerationJob;
import com.smartquiz.entity.AiGenerationJob.Status;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.exception.ApiException;
import com.smartquiz.exception.BusinessRuleException;
import com.smartquiz.exception.ConflictException;
import com.smartquiz.exception.NotFoundException;
import com.smartquiz.exception.TooManyRequestsException;
import com.smartquiz.repository.AiGenerationJobRepository;
import com.smartquiz.repository.TechnologyStackRepository;
import com.smartquiz.repository.TopicRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Runs AI question generation in the background so the HTTP request returns at once (202 + job id)
 * and the browser polls for the result. Jobs run one at a time on {@code aiJobExecutor}; the model
 * itself is additionally serialized by {@link com.smartquiz.ai.AiCallGuard}.
 *
 * <p>Single-host design: on startup every unfinished job is marked FAILED, because its worker thread
 * died with the previous process. With several instances this needs an owner/heartbeat column instead.
 */
@Service
public class AiGenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationJobService.class);

    static final String RESTART_REASON = "The server restarted before this generation finished. Please try again.";
    static final String GENERIC_FAILURE = "AI generation failed. Please try again.";
    private static final List<Status> ACTIVE = List.of(Status.QUEUED, Status.RUNNING);
    private static final int MAX_ERROR_LENGTH = 500;

    private final AIService aiService;
    private final MCQService mcqService;
    private final TechnologyStackRepository stackRepository;
    private final TopicRepository topicRepository;
    private final AiGenerationJobRepository jobRepository;
    private final TaskExecutor jobExecutor;
    private final Duration retention;
    private final MeterRegistry meterRegistry;

    public AiGenerationJobService(@Lazy AIService aiService,
                                  MCQService mcqService,
                                  TechnologyStackRepository stackRepository,
                                  TopicRepository topicRepository,
                                  AiGenerationJobRepository jobRepository,
                                  @Qualifier("aiJobExecutor") TaskExecutor jobExecutor,
                                  @Value("${app.ai.job-retention:7d}") Duration retention,
                                  MeterRegistry meterRegistry) {
        this.aiService = aiService;
        this.mcqService = mcqService;
        this.stackRepository = stackRepository;
        this.topicRepository = topicRepository;
        this.jobRepository = jobRepository;
        this.jobExecutor = jobExecutor;
        this.retention = retention;
        this.meterRegistry = meterRegistry;
        if (jobExecutor instanceof ThreadPoolTaskExecutor pool) {
            Gauge.builder("smartquiz.ai.generation.jobs.queued", pool, p -> p.getThreadPoolExecutor().getQueue().size())
                    .description("Generation jobs waiting for the worker")
                    .register(meterRegistry);
        }
    }

    /** Validates the request, stores a QUEUED job and hands it to the worker. */
    public AiGenerationJobResponse submit(String enterpriseId, AIGenerationRequest req) {
        stackRepository.findById(req.getStackId()).orElseThrow(() -> new NotFoundException("Stack not found"));
        topicRepository.findById(req.getTopicId()).orElseThrow(() -> new NotFoundException("Topic not found"));

        List<String> selectedTypes = new ArrayList<>();
        if (req.isGenerateMCQEnabled()) selectedTypes.add("MCQ");
        if (req.isGenerateMSQEnabled()) selectedTypes.add("MSQ");
        if (selectedTypes.isEmpty()) {
            throw new BusinessRuleException("Select at least one question type: MCQ and/or MSQ");
        }
        if (jobRepository.existsByEnterpriseIdAndStatusIn(enterpriseId, ACTIVE)) {
            throw new ConflictException("You already have an AI generation in progress. Wait for it to finish.");
        }

        Map<String, Integer> counts = splitCounts(req.getNumberOfQuestions(), selectedTypes);
        AiGenerationJob job = new AiGenerationJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setEnterpriseId(enterpriseId);
        job.setStatus(Status.QUEUED);
        job.setStackId(req.getStackId());
        job.setTopicId(req.getTopicId());
        job.setDifficulty(req.getDifficulty());
        job.setRequestedMcq(counts.getOrDefault("MCQ", 0));
        job.setRequestedMsq(counts.getOrDefault("MSQ", 0));
        job.setCreatedAt(LocalDateTime.now());
        job = jobRepository.save(job);

        // The worker saves drafts through MCQService, whose methods check the caller's role.
        SecurityContext callerContext = SecurityContextHolder.createEmptyContext();
        callerContext.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        String jobId = job.getJobId();
        try {
            jobExecutor.execute(new DelegatingSecurityContextRunnable(() -> run(jobId), callerContext));
        } catch (TaskRejectedException e) {
            jobRepository.deleteById(jobId);
            countJob("rejected");
            throw new TooManyRequestsException("The AI generation queue is full. Try again in a minute.", 60);
        }
        return toResponse(job);
    }

    /** Owner-only lookup; another user's job is reported as not found so ids cannot be probed. */
    public AiGenerationJobResponse get(String enterpriseId, String jobId) {
        return jobRepository.findByJobIdAndEnterpriseId(jobId, enterpriseId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Generation job not found"));
    }

    void run(String jobId) {
        AiGenerationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != Status.QUEUED) {
            return;
        }
        job.setStatus(Status.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job = jobRepository.save(job);
        try {
            TechnologyStack stack = stackRepository.findById(job.getStackId())
                    .orElseThrow(() -> new NotFoundException("Stack not found"));
            Topic topic = topicRepository.findById(job.getTopicId())
                    .orElseThrow(() -> new NotFoundException("Topic not found"));

            List<MCQDTO.CreateRequest> generated = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : requestedByType(job).entrySet()) {
                List<MCQDTO.CreateRequest> byType = aiService.generateQuestions(stack, topic, job.getDifficulty(),
                        entry.getValue(), job.getStackId(), job.getTopicId(), entry.getKey(), generated);
                generated.addAll(byType);
                job.setGeneratedCount(generated.size());
                job = jobRepository.save(job);
            }

            List<Long> savedIds = new ArrayList<>();
            for (MCQDTO.CreateRequest createReq : generated) {
                try {
                    createReq.setAiGenerated(true);
                    savedIds.add(mcqService.createMCQ(job.getEnterpriseId(), createReq).getQuestionId());
                } catch (RuntimeException e) {
                    log.debug("Skipping generated question that could not be saved: {}", e.getMessage());
                }
            }
            job.setQuestionIds(savedIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
            job.setStatus(Status.SUCCEEDED);
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
            countJob("succeeded");
            timeJob(job, "succeeded");
        } catch (ApiException e) {
            fail(job, e.getMessage());
        } catch (RuntimeException e) {
            log.error("AI generation job {} failed", jobId, e);
            fail(job, GENERIC_FAILURE);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        LocalDateTime now = LocalDateTime.now();
        int failed = jobRepository.failAll(ACTIVE, RESTART_REASON, now);
        int purged = jobRepository.deleteFinishedBefore(now.minus(retention));
        if (failed > 0 || purged > 0) {
            log.info("AI generation jobs: {} unfinished marked failed, {} finished more than {} day(s) ago purged",
                    failed, purged, retention.toDays());
        }
    }

    private void fail(AiGenerationJob job, String reason) {
        String message = reason == null || reason.isBlank() ? GENERIC_FAILURE : reason;
        job.setErrorMessage(message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
        job.setStatus(Status.FAILED);
        job.setFinishedAt(LocalDateTime.now());
        jobRepository.save(job);
        countJob("failed");
        timeJob(job, "failed");
    }

    /** smartquiz.ai.generation.jobs: finished or rejected jobs by outcome (succeeded, failed, rejected). */
    private void countJob(String outcome) {
        meterRegistry.counter("smartquiz.ai.generation.jobs", "outcome", outcome).increment();
    }

    /** Run time of a job from start to finish (queue time excluded). */
    private void timeJob(AiGenerationJob job, String outcome) {
        if (job.getStartedAt() != null && job.getFinishedAt() != null) {
            Timer.builder("smartquiz.ai.generation.jobs.duration").tag("outcome", outcome).register(meterRegistry)
                    .record(Duration.between(job.getStartedAt(), job.getFinishedAt()));
        }
    }

    private AiGenerationJobResponse toResponse(AiGenerationJob job) {
        Map<String, Integer> byType = requestedByType(job);
        List<MCQDTO.Response> questions = job.getStatus() == Status.SUCCEEDED ? loadQuestions(job) : List.of();
        Long queuePosition = job.getStatus() == Status.QUEUED
                ? jobRepository.countByStatusAndCreatedAtBefore(Status.QUEUED, job.getCreatedAt()) + 1
                : null;
        String message = switch (job.getStatus()) {
            case QUEUED -> "Waiting for the AI model to become free.";
            case RUNNING -> "Generating questions with the AI model.";
            case SUCCEEDED -> questions.size() + " question(s) generated and saved as Draft.";
            case FAILED -> job.getErrorMessage();
        };
        return new AiGenerationJobResponse(
                job.getJobId(),
                job.getStatus().name(),
                job.getRequestedMcq() + job.getRequestedMsq(),
                byType,
                List.copyOf(byType.keySet()),
                job.getGeneratedCount(),
                questions.size(),
                queuePosition,
                questions,
                message,
                job.getStatus() == Status.FAILED ? job.getErrorMessage() : null,
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt());
    }

    private List<MCQDTO.Response> loadQuestions(AiGenerationJob job) {
        if (job.getQuestionIds() == null || job.getQuestionIds().isBlank()) {
            return List.of();
        }
        List<MCQDTO.Response> out = new ArrayList<>();
        for (String id : job.getQuestionIds().split(",")) {
            try {
                out.add(mcqService.getMCQById(Long.valueOf(id.trim())));
            } catch (NotFoundException deletedSince) {
                // The author may have deleted a draft after the job finished.
            }
        }
        return out;
    }

    private static Map<String, Integer> requestedByType(AiGenerationJob job) {
        Map<String, Integer> byType = new LinkedHashMap<>();
        if (job.getRequestedMcq() > 0) byType.put("MCQ", job.getRequestedMcq());
        if (job.getRequestedMsq() > 0) byType.put("MSQ", job.getRequestedMsq());
        return byType;
    }

    /** Splits the total across the selected types; earlier types get the remainder (5 → MCQ 3, MSQ 2). */
    static Map<String, Integer> splitCounts(int total, List<String> selectedTypes) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int base = total / selectedTypes.size();
        int remainder = total % selectedTypes.size();
        for (int i = 0; i < selectedTypes.size(); i++) {
            counts.put(selectedTypes.get(i), base + (i < remainder ? 1 : 0));
        }
        return counts;
    }
}
