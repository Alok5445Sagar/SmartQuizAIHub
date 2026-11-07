package com.smartquiz.controller;

import com.smartquiz.exception.NotFoundException;
import com.smartquiz.exception.UnauthorizedException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.smartquiz.ai.AIService;
import com.smartquiz.dto.AIGenerationRequest;
import com.smartquiz.dto.AiGenerationJobResponse;
import com.smartquiz.dto.ChatHistoryResponse;
import com.smartquiz.dto.ChatRequest;
import com.smartquiz.dto.ChatResponse;
import com.smartquiz.dto.ChatSessionsResponse;
import com.smartquiz.dto.ChatStreamResponse;
import com.smartquiz.dto.SimilarityCheckRequest;
import com.smartquiz.dto.SimilarityCheckResponse;
import com.smartquiz.entity.User;
import com.smartquiz.repository.UserRepository;
import com.smartquiz.service.ChatMemoryService;
import com.smartquiz.service.AiGenerationJobService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai")
@Validated
public class AIController {
        private static final Logger log = LoggerFactory.getLogger(AIController.class);

    @Autowired
    @org.springframework.context.annotation.Lazy
    private AIService aiService;
    @Autowired private AiGenerationJobService generationJobService;
    @Autowired private UserRepository userRepository;
        @Autowired private ChatMemoryService chatMemoryService;
        @Autowired @org.springframework.beans.factory.annotation.Qualifier("chatStreamExecutor")
        private org.springframework.core.task.TaskExecutor chatStreamExecutor;
        @org.springframework.beans.factory.annotation.Value("${app.ai.stream-timeout:330s}")
        private java.time.Duration streamTimeout;

    /** Queues a generation job; poll the Location URL until status is SUCCEEDED or FAILED. */
    @PostMapping("/generate")
    public ResponseEntity<AiGenerationJobResponse> generateQuestions(
            @jakarta.validation.Valid @RequestBody AIGenerationRequest req,
            Authentication auth) {
        AiGenerationJobResponse job = generationJobService.submit(auth.getName(), req);
        return ResponseEntity.accepted()
                .location(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{jobId}").buildAndExpand(job.jobId()).toUri())
                .body(job);
    }

    @GetMapping("/generate/{jobId}")
    public AiGenerationJobResponse generationJob(@PathVariable String jobId, Authentication auth) {
        return generationJobService.get(auth.getName(), jobId);
    }

    @PostMapping("/similarity-check")
    public ResponseEntity<SimilarityCheckResponse> checkSimilarity(
            @jakarta.validation.Valid @RequestBody SimilarityCheckRequest req) {
                try {
                        SimilarityCheckResponse response = aiService.checkSimilarity(
                                        req.getQuestionStem(),
                                        req.getOptionA(), req.getOptionB(),
                                        req.getOptionC(), req.getOptionD(),
                                        req.getOptionE(), req.getOptionF(),
                                        req.getStackId(), req.getTopicId(),
                                        req.getExcludeQuestionId());

                        return ResponseEntity.ok(response);
                } catch (RuntimeException ex) {
                        log.warn("Similarity check failed, returning safe fallback response: {}", ex.getMessage());
                        return ResponseEntity.ok(new SimilarityCheckResponse(false, Collections.emptyList()));
                }
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest req,
            Authentication auth) {

                if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
                        return ResponseEntity.status(401).build();
                }

                User user = userRepository.findByEnterpriseId(auth.getName())
                                .orElseThrow(() -> new NotFoundException("User not found"));

                String sessionId = chatMemoryService.resolveSessionId(user.getEnterpriseId(), req.getSessionId());

                List<ChatRequest.ChatMessage> history = req.getHistory();
                if (history == null || history.isEmpty()) {
                        history = chatMemoryService.getRecentHistoryForPrompt(user.getEnterpriseId(), sessionId, 10);
                }

        String answer = aiService.answerGeneralQuery(
                req.getMessage(),
                                history,
                                user.getEnterpriseId(),
                                user.getRole() == User.Role.ADMIN ? "ADMIN" : "SME");

                chatMemoryService.saveUserMessage(user.getEnterpriseId(), sessionId, req.getMessage());
                chatMemoryService.saveAssistantMessage(user.getEnterpriseId(), sessionId, answer);

        return ResponseEntity.ok(new ChatResponse(
                answer,
                aiService.getActiveModelName(),
                System.currentTimeMillis(),
                sessionId));
    }

        @GetMapping("/chat/history")
        public ResponseEntity<ChatHistoryResponse> getChatHistory(
                        Authentication auth,
                        @RequestParam(name = "limit", defaultValue = "40") int limit,
                        @RequestParam(name = "sessionId", required = false) String sessionId) {

                if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
                        return ResponseEntity.status(401).build();
                }

                return ResponseEntity.ok(chatMemoryService.getRecentHistory(auth.getName(), sessionId, limit));
        }

        @GetMapping("/chat/sessions")
        public ResponseEntity<ChatSessionsResponse> getChatSessions(
                        Authentication auth,
                        @RequestParam(name = "limit", defaultValue = "5") int limit) {
                if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
                        return ResponseEntity.status(401).build();
                }

                return ResponseEntity.ok(chatMemoryService.getRecentSessions(auth.getName(), limit));
        }

        /**
         * Streams the assistant's answer token by token (SSE events: "message" deltas, then "done",
         * or "error" with {"message": ...}). The user message is saved before streaming starts; the assistant
         * message when it completes. If the stream breaks after some tokens, the partial answer is saved with a marker.
         */
        @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter chatStream(
                        @Valid @RequestBody ChatRequest req,
                        Authentication auth) {

                SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());

                if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
                        emitter.completeWithError(new UnauthorizedException("Unauthorized"));
                        return emitter;
                }

                User user = userRepository.findByEnterpriseId(auth.getName())
                                .orElseThrow(() -> new NotFoundException("User not found"));
                String enterpriseId = user.getEnterpriseId();
                String assistantRole = user.getRole() == User.Role.ADMIN ? "ADMIN" : "SME";
                String sessionId = chatMemoryService.resolveSessionId(enterpriseId, req.getSessionId());

                AtomicBoolean cancelled = new AtomicBoolean(false);
                emitter.onCompletion(() -> cancelled.set(true));
                emitter.onTimeout(() -> cancelled.set(true));
                emitter.onError(ex -> cancelled.set(true));

                try {
                        chatStreamExecutor.execute(() -> runChatStream(emitter, cancelled, req, enterpriseId, sessionId, assistantRole));
                } catch (org.springframework.core.task.TaskRejectedException ex) {
                        sendStreamError(emitter, "Too many chat streams are open right now. Please try again shortly.");
                }
                return emitter;
        }

        private void runChatStream(SseEmitter emitter, AtomicBoolean cancelled, ChatRequest req,
                                   String enterpriseId, String sessionId, String assistantRole) {
                List<ChatRequest.ChatMessage> history = req.getHistory();
                if (history == null || history.isEmpty()) {
                        history = chatMemoryService.getRecentHistoryForPrompt(enterpriseId, sessionId, 10);
                }
                chatMemoryService.saveUserMessage(enterpriseId, sessionId, req.getMessage());

                try {
                        String answer = aiService.streamGeneralQuery(
                                        req.getMessage(), history, enterpriseId, assistantRole,
                                        delta -> sendDelta(emitter, cancelled, delta),
                                        cancelled::get);
                        chatMemoryService.saveAssistantMessage(enterpriseId, sessionId, answer);
                        emitter.send(SseEmitter.event().name("done").data(new ChatStreamResponse("", true)));
                        emitter.complete();
                } catch (AIService.StreamCancelledException ex) {
                        // Client disconnected: keep what was generated so the session history stays coherent.
                        if (!ex.getPartialAnswer().isBlank()) {
                                chatMemoryService.saveAssistantMessage(enterpriseId, sessionId, ex.getPartialAnswer() + INTERRUPTED_MARKER);
                        }
                        log.debug("Chat stream for {} cancelled by client", enterpriseId);
                } catch (AIService.StreamInterruptedException ex) {
                        chatMemoryService.saveAssistantMessage(enterpriseId, sessionId, ex.getPartialAnswer() + INTERRUPTED_MARKER);
                        sendStreamError(emitter, "The AI response was interrupted. Please try again.");
                } catch (Exception ex) {
                        log.warn("Chat stream failed for {}: {}", enterpriseId, ex.getMessage());
                        sendStreamError(emitter, ex instanceof com.smartquiz.exception.ApiException
                                        ? ex.getMessage() : "The AI assistant could not answer right now.");
                }
        }

        static final String INTERRUPTED_MARKER = "\n\n[Response interrupted]";

        private void sendDelta(SseEmitter emitter, AtomicBoolean cancelled, String delta) {
                if (cancelled.get()) {
                        return;
                }
                try {
                        emitter.send(SseEmitter.event().name("message").data(new ChatStreamResponse(delta, false)));
                } catch (Exception ex) {
                        // Client is gone; stop pulling tokens from the model.
                        cancelled.set(true);
                }
        }

        private void sendStreamError(SseEmitter emitter, String message) {
                try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
                        emitter.complete();
                } catch (Exception ignored) {
                        emitter.completeWithError(new IllegalStateException(message));
                }
        }
        @GetMapping("/chat/suggestions")
        public ResponseEntity<Map<String, Object>> getChatSuggestions(Authentication auth) {
                if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
                        return ResponseEntity.status(401).build();
                }

                User user = userRepository.findByEnterpriseId(auth.getName())
                                .orElseThrow(() -> new NotFoundException("User not found"));

                List<String> suggestions;
                if (user.getRole() == User.Role.ADMIN) {
                        suggestions = List.of(
                                        "Show a concise reviewer assignment strategy for this sprint.",
                                        "How can I improve approval turnaround time while maintaining quality?",
                                        "Give me a checklist to audit question bank quality across stacks.",
                                        "What metrics should I track for SME and reviewer performance?");
                } else {
                        suggestions = List.of(
                                        "Help me create a strong medium-level Java MCQ.",
                                        "How do I avoid ambiguity while writing technical options?",
                                        "Give a quick rubric to self-review my draft questions.",
                                        "Suggest a topic-wise practice plan for interview readiness.");
                }

                return ResponseEntity.ok(Map.of(
                                "role", user.getRole().name(),
                                "suggestions", suggestions));
        }
}