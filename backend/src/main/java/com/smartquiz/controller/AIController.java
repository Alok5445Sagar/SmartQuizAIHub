package com.smartquiz.controller;

import com.smartquiz.exception.NotFoundException;
import com.smartquiz.exception.UnauthorizedException;
import com.smartquiz.exception.BusinessRuleException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.smartquiz.ai.AIService;
import com.smartquiz.dto.AIGenerationRequest;
import com.smartquiz.dto.ChatHistoryResponse;
import com.smartquiz.dto.ChatRequest;
import com.smartquiz.dto.ChatResponse;
import com.smartquiz.dto.ChatSessionsResponse;
import com.smartquiz.dto.ChatStreamResponse;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.dto.SimilarityCheckRequest;
import com.smartquiz.dto.SimilarityCheckResponse;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.TechnologyStackRepository;
import com.smartquiz.repository.TopicRepository;
import com.smartquiz.repository.UserRepository;
import com.smartquiz.service.ChatMemoryService;
import com.smartquiz.service.MCQService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai")
@Validated
public class AIController {
        private static final Logger log = LoggerFactory.getLogger(AIController.class);

    @Autowired
    @org.springframework.context.annotation.Lazy
    private AIService aiService;
    @Autowired private MCQService mcqService;
    @Autowired private TechnologyStackRepository stackRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private UserRepository userRepository;
        @Autowired private ChatMemoryService chatMemoryService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateQuestions(
            @jakarta.validation.Valid @RequestBody AIGenerationRequest req,
            Authentication auth) {

        TechnologyStack stack = stackRepository.findById(req.getStackId())
                .orElseThrow(() -> new NotFoundException("Stack not found"));
        Topic topic = topicRepository.findById(req.getTopicId())
                .orElseThrow(() -> new NotFoundException("Topic not found"));

        if (req.getNumberOfQuestions() < 1 || req.getNumberOfQuestions() > 20) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Number of questions must be between 1 and 20"));
        }

        boolean generateMCQ = req.isGenerateMCQEnabled();
        boolean generateMSQ = req.isGenerateMSQEnabled();
        if (!generateMCQ && !generateMSQ) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Select at least one question type: MCQ and/or MSQ"));
        }

        List<String> selectedTypes = new ArrayList<>();
        if (generateMCQ) selectedTypes.add("MCQ");
        if (generateMSQ) selectedTypes.add("MSQ");

        Map<String, Integer> typeCounts = splitCounts(req.getNumberOfQuestions(), selectedTypes);

        List<MCQDTO.CreateRequest> generated = new ArrayList<>();
        for (String questionType : selectedTypes) {
            int count = typeCounts.getOrDefault(questionType, 0);
            if (count <= 0) {
                continue;
            }
            List<MCQDTO.CreateRequest> byType = aiService.generateQuestions(
                    stack,
                    topic,
                    req.getDifficulty(),
                    count,
                    req.getStackId(),
                    req.getTopicId(),
                    questionType,
                    generated);
            generated.addAll(byType);
        }

        // Save all generated questions as DRAFT
        List<MCQDTO.Response> saved = new ArrayList<>();
        for (MCQDTO.CreateRequest createReq : generated) {
            try {
                createReq.setAiGenerated(true);  // ← add this line
                MCQDTO.Response response = mcqService.createMCQ(auth.getName(), createReq);
                saved.add(response);
            } catch (RuntimeException e) {
                // Skip duplicates that slipped through
            }
        }

        return ResponseEntity.ok(Map.of(
                "generated", saved.size(),
                "requested", req.getNumberOfQuestions(),
                                "requestedByType", typeCounts,
                                "selectedTypes", selectedTypes,
                "questions", saved,
                "message", saved.size() + " question(s) generated and saved as Draft."
        ));
    }

        private Map<String, Integer> splitCounts(int total, List<String> selectedTypes) {
                if (selectedTypes == null || selectedTypes.isEmpty() || total <= 0) {
                        return Collections.emptyMap();
                }

                int base = total / selectedTypes.size();
                int remainder = total % selectedTypes.size();
                Map<String, Integer> counts = new HashMap<>();
                for (int i = 0; i < selectedTypes.size(); i++) {
                        int current = base + (i < remainder ? 1 : 0);
                        counts.put(selectedTypes.get(i), current);
                }
                return counts;
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

        @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter chatStream(
                        @Valid @RequestBody ChatRequest req,
                        Authentication auth) {

                SseEmitter emitter = new SseEmitter(120000L);

                if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
                        emitter.completeWithError(new UnauthorizedException("Unauthorized"));
                        return emitter;
                }

                User user = userRepository.findByEnterpriseId(auth.getName())
                                .orElseThrow(() -> new NotFoundException("User not found"));

                String sessionId = chatMemoryService.resolveSessionId(user.getEnterpriseId(), req.getSessionId());

                new Thread(() -> {
                        try {
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

                                int chunkSize = 24;
                                for (int i = 0; i < answer.length(); i += chunkSize) {
                                        int end = Math.min(i + chunkSize, answer.length());
                                        String delta = answer.substring(i, end);
                                        emitter.send(SseEmitter.event()
                                                        .name("message")
                                                        .data(new ChatStreamResponse(delta, false)));
                                        Thread.sleep(25);
                                }

                                emitter.send(SseEmitter.event()
                                                .name("done")
                                                .data(new ChatStreamResponse("", true)));
                                emitter.complete();
                        } catch (Exception ex) {
                                try {
                                        emitter.completeWithError(ex);
                                } catch (Exception ignored) {
                                }
                        }
                }).start();

                return emitter;
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