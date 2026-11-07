package com.smartquiz.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.ai.AIService;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.AiGenerationJob;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.exception.ServiceUnavailableException;
import com.smartquiz.repository.AiGenerationJobRepository;
import com.smartquiz.security.JwtUtils;
import com.smartquiz.service.AiGenerationJobService;

/**
 * Generation runs on a worker thread, which cannot see uncommitted rows, so this class commits its
 * data (no test transaction) and uses unique users per test. The next test class gets a clean schema.
 */
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {"app.ai.job-threads=1", "app.ai.job-queue=1"})
@DisplayName("AI generation jobs")
class AIGenerationJobControllerTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AIService aiService;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private AiGenerationJobRepository jobRepository;
    @Autowired private AiGenerationJobService jobService;

    private TechnologyStack stack;
    private Topic topic;
    private CountDownLatch release;

    @BeforeEach
    void setUp() {
        Mockito.reset(aiService);
        stack = createStack("AI Job Stack");
        topic = createTopic("AI Job Topic", stack);
        release = new CountDownLatch(0);
        Mockito.when(aiService.generateQuestions(
                Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.anyString(), Mockito.anyList()))
            .thenAnswer(invocation -> {
                release.await(10, TimeUnit.SECONDS);
                int count = invocation.getArgument(3, Integer.class);
                String questionType = invocation.getArgument(6, String.class);
                return buildGeneratedRequests(count, questionType);
            });
    }

    @AfterEach
    void releaseWorkers() {
        release.countDown();
    }

    @Test
    @DisplayName("POST returns 202 with a job; polling reaches SUCCEEDED with saved mixed MCQ/MSQ drafts")
    void mixedTypesJobSucceeds() throws Exception {
        String token = smeToken();

        String body = mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(5, true, true)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.jobId", notNullValue()))
                .andExpect(jsonPath("$.requested").value(5))
                .andExpect(jsonPath("$.requestedByType.MCQ").value(3))
                .andExpect(jsonPath("$.requestedByType.MSQ").value(2))
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(body).get("jobId").asText();

        JsonNode done = awaitTerminal(token, jobId);

        assertThat(done.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(done.get("generated").asInt()).isEqualTo(5);
        assertThat(done.get("selectedTypes")).hasSize(2);
        assertThat(done.get("questions")).hasSize(5);
        assertThat(done.get("questions").get(0).get("questionType").asText()).isEqualTo("MCQ");
        assertThat(done.get("questions").get(4).get("questionType").asText()).isEqualTo("MSQ");
        assertThat(done.get("message").asText()).isEqualTo("5 question(s) generated and saved as Draft.");
        assertThat(done.get("finishedAt").isNull()).isFalse();

        long drafts = mcqRepository.findAll().stream()
                .filter(m -> m.getTopic().getTopicId().equals(topic.getTopicId()))
                .filter(m -> m.getStatus() == MCQ.Status.DRAFT && m.isAiGenerated())
                .count();
        assertThat(drafts).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Location header points to the job status URL")
    void locationHeaderPointsToJob() throws Exception {
        String token = smeToken();
        var response = mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, true, false)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse();
        String jobId = objectMapper.readTree(response.getContentAsString()).get("jobId").asText();
        assertThat(response.getHeader("Location")).endsWith("/api/ai/generate/" + jobId);

        mockMvc.perform(get("/api/ai/generate/" + jobId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId));
        awaitTerminal(token, jobId);
    }

    @Test
    @DisplayName("another user's job is reported as not found")
    void otherUserCannotSeeJob() throws Exception {
        String owner = smeToken();
        String jobId = submit(owner, request(1, true, false));
        awaitTerminal(owner, jobId);

        mockMvc.perform(get("/api/ai/generate/" + jobId).header("Authorization", smeToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/ai/generate/" + UUID.randomUUID()).header("Authorization", owner))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an AI service error marks the job FAILED with the user-facing reason")
    void aiFailureMarksJobFailed() throws Exception {
        Mockito.reset(aiService);
        Mockito.when(aiService.generateQuestions(
                Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.anyString(), Mockito.anyList()))
            .thenThrow(new ServiceUnavailableException("Local AI service (Ollama) is unavailable."));
        String token = smeToken();

        JsonNode done = awaitTerminal(token, submit(token, request(2, true, false)));

        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("error").asText()).isEqualTo("Local AI service (Ollama) is unavailable.");
        assertThat(done.get("questions")).isEmpty();
    }

    @Test
    @DisplayName("unexpected errors are reported with a generic message")
    void unexpectedFailureIsGeneric() throws Exception {
        Mockito.reset(aiService);
        Mockito.when(aiService.generateQuestions(
                Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.anyString(), Mockito.anyList()))
            .thenThrow(new IllegalStateException("internal detail that must not leak"));
        String token = smeToken();

        JsonNode done = awaitTerminal(token, submit(token, request(1, true, false)));

        assertThat(done.get("status").asText()).isEqualTo("FAILED");
        assertThat(done.get("error").asText()).isEqualTo("AI generation failed. Please try again.");
    }

    @Test
    @DisplayName("a user with an active job gets 409 for a second one")
    void secondActiveJobConflicts() throws Exception {
        release = new CountDownLatch(1);
        String token = smeToken();
        String first = submit(token, request(1, true, false));

        mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, true, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have an AI generation in progress. Wait for it to finish."));

        release.countDown();
        assertThat(awaitTerminal(token, first).get("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("a full queue returns 429 with Retry-After and leaves no active job behind")
    void fullQueueRejects() throws Exception {
        release = new CountDownLatch(1);
        String running = smeToken();
        String queued = smeToken();
        String rejected = smeToken();
        String runningJob = submit(running, request(1, true, false));
        awaitStatus(running, runningJob, "RUNNING");
        String queuedJob = submit(queued, request(1, true, false));
        mockMvc.perform(get("/api/ai/generate/" + queuedJob).header("Authorization", queued))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1));

        mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", rejected)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, true, false)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        release.countDown();
        assertThat(awaitTerminal(running, runningJob).get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(awaitTerminal(queued, queuedJob).get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(jobRepository.findAll()).noneMatch(j -> j.getStatus() == AiGenerationJob.Status.QUEUED
                || j.getStatus() == AiGenerationJob.Status.RUNNING);
    }

    @Test
    @DisplayName("validation errors are returned synchronously and create no job")
    void validationIsSynchronous() throws Exception {
        long before = jobRepository.count();
        mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", smeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(3, false, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select at least one question type: MCQ and/or MSQ"));
        mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", smeToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stackId", stack.getStackId(), "topicId", 999_999L, "difficulty", "Easy",
                                "numberOfQuestions", 1, "generateMCQ", true))))
                .andExpect(status().isNotFound());
        assertThat(jobRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("on startup, unfinished jobs are failed and week-old finished jobs are purged")
    void startupRecovery() {
        String owner = createSME("job.recovery." + UUID.randomUUID(), "Recovery SME").getEnterpriseId();
        AiGenerationJob running = jobRepository.save(job(owner, AiGenerationJob.Status.RUNNING, null));
        AiGenerationJob queued = jobRepository.save(job(owner, AiGenerationJob.Status.QUEUED, null));
        AiGenerationJob recent = jobRepository.save(job(owner, AiGenerationJob.Status.SUCCEEDED, LocalDateTime.now().minusDays(1)));
        AiGenerationJob old = jobRepository.save(job(owner, AiGenerationJob.Status.FAILED, LocalDateTime.now().minusDays(8)));

        jobService.recoverOnStartup();

        AiGenerationJob runningAfter = jobRepository.findById(running.getJobId()).orElseThrow();
        assertThat(runningAfter.getStatus()).isEqualTo(AiGenerationJob.Status.FAILED);
        assertThat(runningAfter.getErrorMessage()).isEqualTo("The server restarted before this generation finished. Please try again.");
        assertThat(runningAfter.getFinishedAt()).isNotNull();
        assertThat(jobRepository.findById(queued.getJobId()).orElseThrow().getStatus()).isEqualTo(AiGenerationJob.Status.FAILED);
        assertThat(jobRepository.findById(recent.getJobId()).orElseThrow().getStatus()).isEqualTo(AiGenerationJob.Status.SUCCEEDED);
        assertThat(jobRepository.findById(old.getJobId())).isEmpty();
    }

    private AiGenerationJob job(String owner, AiGenerationJob.Status status, LocalDateTime finishedAt) {
        AiGenerationJob job = new AiGenerationJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setEnterpriseId(owner);
        job.setStatus(status);
        job.setStackId(stack.getStackId());
        job.setTopicId(topic.getTopicId());
        job.setDifficulty("Easy");
        job.setRequestedMcq(1);
        job.setRequestedMsq(0);
        job.setCreatedAt(LocalDateTime.now().minusDays(9));
        job.setFinishedAt(finishedAt);
        return job;
    }

    private String smeToken() {
        String id = "job.sme." + UUID.randomUUID().toString().substring(0, 8);
        createSME(id, "Job SME");
        return "Bearer " + jwtUtils.generateToken(id, "SME");
    }

    private String request(int count, boolean mcq, boolean msq) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "stackId", stack.getStackId(),
                "topicId", topic.getTopicId(),
                "difficulty", "Medium",
                "numberOfQuestions", count,
                "generateMCQ", mcq,
                "generateMSQ", msq));
    }

    private String submit(String token, String body) throws Exception {
        String res = mockMvc.perform(post("/api/ai/generate")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("jobId").asText();
    }

    private JsonNode awaitTerminal(String token, String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            JsonNode job = fetch(token, jobId);
            String s = job.get("status").asText();
            if ("SUCCEEDED".equals(s) || "FAILED".equals(s)) {
                return job;
            }
            assertThat(System.currentTimeMillis()).as("job %s still %s", jobId, s).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private void awaitStatus(String token, String jobId, String wanted) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!wanted.equals(fetch(token, jobId).get("status").asText())) {
            assertThat(System.currentTimeMillis()).as("job %s never reached %s", jobId, wanted).isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    private JsonNode fetch(String token, String jobId) throws Exception {
        String res = mockMvc.perform(get("/api/ai/generate/" + jobId).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res);
    }

    private List<MCQDTO.CreateRequest> buildGeneratedRequests(int count, String questionType) {
        List<MCQDTO.CreateRequest> out = new ArrayList<>();
        boolean isMsq = "MSQ".equalsIgnoreCase(questionType);
        for (int i = 0; i < count; i++) {
            MCQDTO.CreateRequest req = new MCQDTO.CreateRequest();
            req.setQuestionStem("AI job " + questionType + " stem " + i + " " + UUID.randomUUID());
            req.setQuestionType(isMsq ? "MSQ" : "MCQ");
            req.setStackId(stack.getStackId());
            req.setTopicId(topic.getTopicId());
            req.setDifficulty("Medium");
            req.setOptionA("Option A " + i);
            req.setOptionB("Option B " + i);
            req.setOptionC("Option C " + i);
            req.setOptionD("Option D " + i);
            req.setOptionE(isMsq ? "Option E " + i : null);
            req.setCorrectAnswer(isMsq ? "A,C" : "A");
            req.setSendForReview(false);
            out.add(req);
        }
        return out;
    }
}
