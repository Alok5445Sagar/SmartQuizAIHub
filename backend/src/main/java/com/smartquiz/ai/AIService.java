package com.smartquiz.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.smartquiz.dto.ChatRequest;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.dto.SimilarityCheckResponse;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.repository.MCQRepository;

@Service
@org.springframework.context.annotation.Lazy
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final Pattern ID_PATTERN = Pattern.compile("\\b(?:id|question[_\\s-]*id)\\s*[:#]?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MCQ_LINE_OPTION_PATTERN = Pattern.compile("(?m)^\\s*([A-D])\\s*[\\).:-]\\s*(.+)$");
        private static final Set<String> SIMILARITY_STOP_WORDS = new HashSet<>(Arrays.asList(
            "what", "which", "when", "where", "why", "how", "who",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "to", "of", "in", "on", "for", "by", "with", "from", "as", "at", "into", "about",
            "and", "or", "not", "if", "then", "than",
            "following", "statement", "statements", "correct", "incorrect", "true", "false",
            "best", "most", "least", "option", "choose", "select", "describe", "describes",
            "explain", "example", "examples", "main", "primary", "key", "difference",
            "java", "spring", "boot", "python", "javascript", "typescript", "react", "node", "sql", "api"
        ));

    @Value("${app.ai.model:qwen2.5:3b}")
    private String model;

    @Value("${app.ai.num-ctx:8192}")
    private int numCtx;

    @Value("${app.ai.similarity-threshold:0.70}")
    private double similarityThreshold;

    @Value("${app.ai.generate-use-ai-similarity:false}")
    private boolean generateUseAiSimilarity;

    @Value("${app.ai.generate-retry-multiplier:2}")
    private int generateRetryMultiplier;

    @Value("${app.ai.generate-min-attempts:2}")
    private int generateMinAttempts;

    @Value("${app.ai.max-output-tokens:1200}")
    private int maxOutputTokens;

    @Value("${app.ai.retry.max-attempts:4}")
    private int retryMaxAttempts;

    @Value("${app.ai.retry.initial-backoff-ms:600}")
    private long retryInitialBackoffMs;

    @Value("${app.ai.max-request-bytes:30000}")
    private int maxRequestBytes;

    @Autowired
    private MCQRepository mcqRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;

    private ObjectMapper lenientObjectMapper;

    @Autowired
    public AIService(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.chatClient = chatClientBuilder.build();

        this.lenientObjectMapper = objectMapper.copy()
            .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature(), true);
    }
    // â”€â”€ AI QUESTION GENERATION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public List<MCQDTO.CreateRequest> generateQuestions(
            TechnologyStack stack, Topic topic, String difficulty, int count,
            Long creatorStackId, Long creatorTopicId) {
        return generateQuestions(
                stack,
                topic,
                difficulty,
                count,
                creatorStackId,
                creatorTopicId,
                "MCQ",
                Collections.emptyList());
    }

    public List<MCQDTO.CreateRequest> generateQuestions(
            TechnologyStack stack, Topic topic, String difficulty, int count,
            Long creatorStackId, Long creatorTopicId,
            String questionType,
            List<MCQDTO.CreateRequest> alreadyAcceptedInRequest) {

        String normalizedQuestionType = normalizeQuestionType(questionType);

        List<String> existingStems = getExistingStemsForPrompt(stack.getStackId(), topic.getTopicId(), 30);
        List<String> requestAvoidStems = buildRetryAvoidStems(existingStems, alreadyAcceptedInRequest, 120);

        String prompt = buildGenerationPrompt(
            stack.getStackName(), topic.getTopicName(), difficulty, count, requestAvoidStems, normalizedQuestionType);
        String rawResponse = callModel(prompt);

        List<MCQDTO.CreateRequest> generated = parseGeneratedQuestions(
                rawResponse, stack.getStackId(), topic.getTopicId(), difficulty, normalizedQuestionType);

        Collection<MCQ.Status> generationStatuses = List.of(
            MCQ.Status.DRAFT,
            MCQ.Status.READY_FOR_REVIEW,
            MCQ.Status.UNDER_REVIEW,
            MCQ.Status.APPROVED,
            MCQ.Status.REJECTED);
        List<MCQ> existingForGeneration = mcqRepository.findNonDraftByStackAndTopic(
            stack.getStackId(), topic.getTopicId(), null, generationStatuses);

        // Screen each generated question for similarity
        List<MCQDTO.CreateRequest> screened = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = Math.max(generateMinAttempts, count * generateRetryMultiplier);

        for (MCQDTO.CreateRequest req : generated) {
            SimilarityCheckResponse check = checkSimilarityForGeneration(
                    req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                    req.getOptionC(), req.getOptionD(), null, null,
                    existingForGeneration);

            if (!check.isHasSimilar()
                    && !hasSimilarInBatch(req, screened)
                    && !hasSimilarInBatch(req, alreadyAcceptedInRequest)) {
                screened.add(req);
            } else {
                log.info("Generated question screened out due to similarity â‰¥30%. Requesting replacement.");
            }
        }

        // If we don't have enough after screening, generate more.
        while (screened.size() < count && attempts < maxAttempts) {
            attempts++;
            int stillNeeded = count - screened.size();
            if (stillNeeded > 0) {
                List<MCQDTO.CreateRequest> avoidCandidates = mergeAccepted(screened, alreadyAcceptedInRequest);
                List<String> retryAvoidStems = buildRetryAvoidStems(existingStems, avoidCandidates, 80);
                int retryRequestCount = Math.min(stillNeeded + Math.max(1, stillNeeded), 20);
                String retryPrompt = buildGenerationPrompt(
                    stack.getStackName(), topic.getTopicName(), difficulty, retryRequestCount, retryAvoidStems, normalizedQuestionType);
                String retryResponse = callModel(retryPrompt);
                List<MCQDTO.CreateRequest> retryGenerated = parseGeneratedQuestions(
                        retryResponse, stack.getStackId(), topic.getTopicId(), difficulty, normalizedQuestionType);

                for (MCQDTO.CreateRequest req : retryGenerated) {
                    if (screened.size() >= count) break;
                    SimilarityCheckResponse check = checkSimilarityForGeneration(
                            req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                            req.getOptionC(), req.getOptionD(), null, null,
                            existingForGeneration);
                    if (!check.isHasSimilar()
                            && !hasSimilarInBatch(req, screened)
                            && !hasSimilarInBatch(req, alreadyAcceptedInRequest)) {
                        screened.add(req);
                    } else {
                        log.info("Retry-generated question screened out due to similarity with existing or in-batch questions.");
                    }
                }
            }
        }

        // Fallback: ask for one question at a time with stronger avoid-list constraints.
        int singleFillAttempts = 0;
        int maxSingleFillAttempts = Math.max(6, count * 5);
        while (screened.size() < count && singleFillAttempts < maxSingleFillAttempts) {
            singleFillAttempts++;
            List<MCQDTO.CreateRequest> avoidCandidates = mergeAccepted(screened, alreadyAcceptedInRequest);
            List<String> singleAvoidStems = buildRetryAvoidStems(existingStems, avoidCandidates, 120);
            String singlePrompt = buildGenerationPrompt(
                    stack.getStackName(), topic.getTopicName(), difficulty, 1, singleAvoidStems, normalizedQuestionType);
            String singleResponse = callModel(singlePrompt);
            List<MCQDTO.CreateRequest> singleGenerated = parseGeneratedQuestions(
                    singleResponse, stack.getStackId(), topic.getTopicId(), difficulty, normalizedQuestionType);

            for (MCQDTO.CreateRequest req : singleGenerated) {
                SimilarityCheckResponse check = checkSimilarityForGeneration(
                        req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                        req.getOptionC(), req.getOptionD(), null, null,
                        existingForGeneration);
                if (!check.isHasSimilar()
                    && !hasSimilarInBatch(req, screened)
                    && !hasSimilarInBatch(req, alreadyAcceptedInRequest)) {
                    screened.add(req);
                    break;
                }
            }
        }

        if (screened.size() < count) {
            throw new RuntimeException(
                    "Could not generate " + count + " unique non-similar questions for this topic right now. "
                            + "Please try again or change topic/difficulty.");
        }

        return screened;
    }

    public String answerGeneralQuery(String question, List<ChatRequest.ChatMessage> history, String userId, String assistantRole) {
        String sanitizedQuestion = question == null ? "" : question.trim().replaceAll("\\s+", " ");
        if (sanitizedQuestion.isBlank()) {
            throw new RuntimeException("Question cannot be empty.");
        }

        if (sanitizedQuestion.length() > 2000) {
            sanitizedQuestion = sanitizedQuestion.substring(0, 2000);
        }

        String systemPrompt = buildRoleAwareSystemPrompt(assistantRole);

        List<ChatRequest.ChatMessage> safeHistory = history == null ? Collections.emptyList() : history;
        List<ChatRequest.ChatMessage> trimmedHistory = safeHistory.stream()
                .filter(m -> m != null && !isBlank(m.getRole()) && !isBlank(m.getContent()))
                .map(m -> new ChatRequest.ChatMessage(
                        normalizeRole(m.getRole()),
                        trimToLength(m.getContent(), 800)))
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .collect(Collectors.toList());

        if (trimmedHistory.size() > 8) {
            trimmedHistory = trimmedHistory.subList(trimmedHistory.size() - 8, trimmedHistory.size());
        }

        StringBuilder userPrompt = new StringBuilder();
        if (!isBlank(userId)) {
            userPrompt.append("User: ").append(trimToLength(userId, 120)).append("\n\n");
        }

        if (!trimmedHistory.isEmpty()) {
            userPrompt.append("Conversation so far:\n");
            for (ChatRequest.ChatMessage msg : trimmedHistory) {
                userPrompt.append(msg.getRole())
                        .append(": ")
                        .append(msg.getContent().trim().replaceAll("\\s+", " "))
                        .append("\n");
            }
            userPrompt.append("\n");
        }

        userPrompt.append("Current user query: ").append(sanitizedQuestion);

        try {
            String answer = callAssistantChat(
                    systemPrompt,
                    userPrompt.toString(),
                    Math.min(700, Math.max(256, maxOutputTokens)),
                    0.4d);
            return stripCodeFences(trimToLength(answer, 4000));
        } catch (RuntimeException ex) {
            log.warn("General chat AI call failed; serving offline fallback. Reason: {}", safeSnippet(ex.getMessage()));
            return buildOfflineFallbackAnswer(sanitizedQuestion, assistantRole);
        }
    }

    private String buildOfflineFallbackAnswer(String question, String assistantRole) {
        String lower = question == null ? "" : question.toLowerCase();
        String role = assistantRole == null ? "SME" : assistantRole.trim().toUpperCase();

        String topicGuidance;
        if (containsAny(lower, "java", "jvm", "spring", "oop", "stream api", "hibernate")) {
            topicGuidance = "Java/Spring tip: focus on core concepts first (OOP, collections, exceptions, threading), then framework layers (MVC, data, security), and finish with one practical example or mini-project.";
        } else if (containsAny(lower, "sql", "database", "normalization", "join", "index", "transaction")) {
            topicGuidance = "Database tip: clarify the schema, choose correct keys/indexes, and validate with sample queries. For performance, check query plan and avoid unnecessary full-table scans.";
        } else if (containsAny(lower, "react", "javascript", "frontend", "ui", "css", "html")) {
            topicGuidance = "Frontend tip: break UI into reusable components, keep state minimal and predictable, and optimize rendering and network calls for responsiveness.";
        } else if (containsAny(lower, "api", "rest", "microservice", "backend", "http", "authentication")) {
            topicGuidance = "Backend/API tip: define clear contracts, validate inputs, handle errors consistently, and secure endpoints with proper authentication/authorization.";
        } else if (containsAny(lower, "cloud", "azure", "aws", "gcp", "docker", "kubernetes", "devops")) {
            topicGuidance = "Cloud/DevOps tip: start with architecture boundaries, automate build/deploy pipelines, and add logging/monitoring early for easier operations.";
        } else if (containsAny(lower, "dsa", "algorithm", "data structure", "complexity", "leetcode")) {
            topicGuidance = "DSA tip: identify input constraints, choose the right data structure, and reason about time/space complexity before coding.";
        } else if (containsAny(lower, "interview", "prepare", "study", "learning", "roadmap")) {
            topicGuidance = "Preparation tip: use a weekly plan with concept revision, timed practice, and retrospective notes on mistakes and improvements.";
        } else {
            topicGuidance = "General tip: define the problem clearly, split it into smaller parts, evaluate options with trade-offs, and implement the simplest robust solution first.";
        }

        String roleHint = "ADMIN".equals(role)
                ? "Admin note: if this is workflow-related, track turnaround time, review load, and quality metrics to improve decision-making."
                : "SME note: if this is question-authoring related, keep wording precise and avoid ambiguous answer options.";

        return trimToLength(
            "[Fallback mode] Live AI is temporarily unavailable.\n\n"
                + "I can still help with this topic using built-in guidance.\n\n"
                        + topicGuidance + "\n\n"
                        + roleHint + "\n\n"
                        + "If you want, I can give a step-by-step breakdown tailored to your exact question: \""
                        + trimToLength(question, 220) + "\".",
                4000);
    }

    private boolean containsAny(String text, String... terms) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String term : terms) {
            if (term != null && !term.isBlank() && text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String buildRoleAwareSystemPrompt(String assistantRole) {
        String role = assistantRole == null ? "SME" : assistantRole.trim().toUpperCase();
        if ("ADMIN".equals(role)) {
            return """
                    You are Smart Quiz Hub Admin Assistant.
                    Your responsibilities:
                    - Help admins with governance, reviewer assignment strategy, and workflow bottlenecks.
                    - Provide concise operational recommendations for quality, coverage, and approvals.
                    - Explain trade-offs and include actionable next steps.

                    Constraints:
                    - Do not reveal secrets, credentials, tokens, or internal system configuration.
                    - If uncertain, explicitly mention uncertainty and suggest a safe verification path.
                    - Use clear bullet points for operational guidance.
                    """;
        }

        return """
                You are Smart Quiz Hub SME Assistant.
                Your responsibilities:
                - Help SMEs draft, improve, and review technical quiz questions.
                - Explain concepts with practical examples and interview-oriented clarity.
                - Keep guidance concise, accurate, and easy to apply.

                Constraints:
                - Do not reveal secrets, credentials, tokens, or internal system configuration.
                - If uncertain, explicitly mention uncertainty and suggest a safe verification path.
                - Prefer structured bullets or steps for readability.
                """;
    }

    public String getActiveModelName() {
        return isBlank(model) ? "unknown" : model;
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if ("assistant".equals(normalized) || "bot".equals(normalized) || "model".equals(normalized)) {
            return "assistant";
        }
        return "user";
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static final String DEFAULT_SYSTEM_PROMPT = "You are a quiz assistant. Follow the user's response format exactly.";

    /** Calls the model with the quiz-assistant system prompt (generation, similarity, translation). */
    private String callModel(String prompt) {
        return callAssistantChat(DEFAULT_SYSTEM_PROMPT, prompt, maxOutputTokens, 0.2d);
    }

    private String callAssistantChat(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        String boundedPrompt = enforceRequestSizeLimit(userPrompt);
        int maxAttempts = Math.max(1, retryMaxAttempts);
        long backoffMs = Math.max(200L, retryInitialBackoffMs);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                OllamaOptions options = OllamaOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .topP(0.9)
                        .numPredict(maxTokens)
                        .numCtx(numCtx)
                        .build();

                String modelResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user(boundedPrompt)
                        .options(options)
                        .call()
                        .content();

                if (modelResponse == null || modelResponse.isBlank()) {
                    throw new RuntimeException("Ollama returned an empty response.");
                }
                return modelResponse;
            } catch (Exception e) {
                RuntimeException mapped = mapOllamaError(e);
                if (!isRetryableError(e) || attempt == maxAttempts) {
                    throw mapped;
                }
                log.warn("Transient AI failure (attempt {}/{}). Retrying in {} ms. Cause: {}",
                        attempt, maxAttempts, backoffMs, safeSnippet(e.getMessage()));
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("AI retry interrupted while waiting for backoff.");
                }
                backoffMs = Math.min(backoffMs * 2L, 8000L);
            }
        }
        throw new RuntimeException("AI service error: retry loop exhausted unexpectedly.");
    }

    private RuntimeException mapOllamaError(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        log.error("Ollama call failed (model={}): {}", model, e.getMessage());

        if (msg.contains("connection refused") || msg.contains("failed to connect")
                || msg.contains("i/o error") || msg.contains("no route")) {
            return new RuntimeException("Local AI service (Ollama) is unavailable. Make sure Ollama is running and reachable.");
        }
        if (msg.contains("timed out") || msg.contains("timeout")) {
            return new RuntimeException("Local AI model timed out. Try fewer questions or increase the AI read timeout.");
        }
        if (msg.contains("404") || (msg.contains("model") && msg.contains("not found"))) {
            return new RuntimeException("AI model '" + model + "' is not available in Ollama. Run: ollama pull " + model);
        }
        return new RuntimeException("AI service error: " + e.getMessage());
    }

    private boolean isRetryableError(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        // Retry only transient transport problems; a missing model or bad request will not fix itself.
        return msg.contains("connection reset")
                || msg.contains("503")
                || msg.contains("502")
                || msg.contains("500")
                || msg.contains("empty response");
    }

    private String enforceRequestSizeLimit(String prompt) {
        if (prompt == null) {
            return "";
        }
        int limitBytes = Math.max(4096, maxRequestBytes);
        if (prompt.getBytes(StandardCharsets.UTF_8).length <= limitBytes) {
            return prompt;
        }
        String bounded = prompt;
        while (bounded.getBytes(StandardCharsets.UTF_8).length > limitBytes && bounded.length() > 512) {
            bounded = bounded.substring(0, bounded.length() - 256);
        }
        log.warn("AI prompt truncated to {} bytes.", bounded.getBytes(StandardCharsets.UTF_8).length);
        return bounded + "\n\n[Truncated to respect request-size limit.]";
    }

    private List<String> buildRetryAvoidStems(List<String> existingStems,
                                               List<MCQDTO.CreateRequest> acceptedSoFar,
                                               int limit) {
        List<String> merged = new ArrayList<>();
        if (existingStems != null) {
            merged.addAll(existingStems);
        }
        if (acceptedSoFar != null) {
            for (MCQDTO.CreateRequest req : acceptedSoFar) {
                if (req.getQuestionStem() != null && !req.getQuestionStem().isBlank()) {
                    merged.add(req.getQuestionStem().trim().replaceAll("\\s+", " "));
                }
            }
        }
        return merged.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .limit(Math.max(0, limit))
                .collect(Collectors.toList());
    }

    private List<MCQDTO.CreateRequest> mergeAccepted(List<MCQDTO.CreateRequest> first, List<MCQDTO.CreateRequest> second) {
        List<MCQDTO.CreateRequest> merged = new ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return merged;
    }

    private List<String> getExistingStemsForPrompt(Long stackId, Long topicId, int limit) {
        List<MCQ> existing = mcqRepository.findNonDraftByStackAndTopic(
            stackId,
            topicId,
            null,
            List.of(
                MCQ.Status.DRAFT,
                MCQ.Status.READY_FOR_REVIEW,
                MCQ.Status.UNDER_REVIEW,
                MCQ.Status.APPROVED,
                MCQ.Status.REJECTED));

        return existing.stream()
                .map(MCQ::getQuestionStem)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().replaceAll("\\s+", " "))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .limit(Math.max(0, limit))
                .collect(Collectors.toList());
    }

    private boolean hasSimilarInBatch(MCQDTO.CreateRequest candidateReq, List<MCQDTO.CreateRequest> acceptedSoFar) {
        if (acceptedSoFar == null || acceptedSoFar.isEmpty()) {
            return false;
        }

        if (hasExactStemDuplicateInBatch(candidateReq, acceptedSoFar)) {
            return true;
        }

        int threshold = (int) (similarityThreshold * 100);
        for (MCQDTO.CreateRequest existingReq : acceptedSoFar) {
            int similarity = calculateSimilarityPercent(
                    candidateReq.getQuestionStem(),
                    candidateReq.getOptionA(), candidateReq.getOptionB(),
                    candidateReq.getOptionC(), candidateReq.getOptionD(),
                    candidateReq.getOptionE(), candidateReq.getOptionF(),
                    existingReq.getQuestionStem(),
                    existingReq.getOptionA(), existingReq.getOptionB(),
                    existingReq.getOptionC(), existingReq.getOptionD(),
                    existingReq.getOptionE(), existingReq.getOptionF());
            if (similarity >= threshold) {
                return true;
            }
        }

        return false;
    }

    private boolean hasExactStemDuplicateInBatch(MCQDTO.CreateRequest candidateReq, List<MCQDTO.CreateRequest> acceptedSoFar) {
        String candidateStem = normalizeStem(candidateReq.getQuestionStem());
        for (MCQDTO.CreateRequest existing : acceptedSoFar) {
            if (candidateStem.equals(normalizeStem(existing.getQuestionStem()))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeStem(String stem) {
        if (stem == null) {
            return "";
        }
        return stem.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private List<MCQ> toTransientMcqs(List<MCQDTO.CreateRequest> requests) {
        List<MCQ> result = new ArrayList<>();
        long tempId = -1L;

        for (MCQDTO.CreateRequest req : requests) {
            MCQ m = new MCQ();
            m.setQuestionId(tempId--);
            m.setQuestionStem(req.getQuestionStem());
            m.setOptionA(req.getOptionA());
            m.setOptionB(req.getOptionB());
            m.setOptionC(req.getOptionC());
            m.setOptionD(req.getOptionD());
            m.setOptionE(req.getOptionE());
            m.setOptionF(req.getOptionF());
            result.add(m);
        }

        return result;
    }

    private SimilarityCheckResponse checkSimilarityForGeneration(
            String questionStem, String optionA, String optionB,
            String optionC, String optionD, String optionE, String optionF,
            List<MCQ> existing) {

        if (existing.isEmpty()) {
            return new SimilarityCheckResponse(false, Collections.emptyList());
        }

        SimilarityCheckResponse deterministic = runDeterministicSimilarityCheck(
                questionStem, optionA, optionB, optionC, optionD, optionE, optionF, existing);
        if (deterministic.isHasSimilar() || !generateUseAiSimilarity) {
            return deterministic;
        }

        String candidate = buildQuestionText(questionStem, optionA, optionB, optionC, optionD, optionE, optionF);
        String prompt = buildSimilarityPrompt(candidate, existing);
        String rawResponse = callModel(prompt);
        SimilarityCheckResponse aiResult = parseSimilarityResponse(rawResponse, existing);
        return aiResult.isHasSimilar() ? aiResult : deterministic;
    }

    private SimilarityCheckResponse runDeterministicSimilarityCheck(
            String questionStem, String optionA, String optionB,
            String optionC, String optionD, String optionE, String optionF,
            List<MCQ> existing) {

        int threshold = (int) (similarityThreshold * 100);
        List<SimilarityCheckResponse.SimilarQuestion> similar = new ArrayList<>();

        for (MCQ m : existing) {
            int similarity = calculateSimilarityPercent(
                    questionStem, optionA, optionB, optionC, optionD, optionE, optionF,
                    m.getQuestionStem(), m.getOptionA(), m.getOptionB(),
                    m.getOptionC(), m.getOptionD(), m.getOptionE(), m.getOptionF());

            if (similarity >= threshold) {
                similar.add(new SimilarityCheckResponse.SimilarQuestion(
                        m.getQuestionId(),
                        similarity,
                        m.getQuestionStem(),
                        m.getOptionA(), m.getOptionB(),
                        m.getOptionC(), m.getOptionD(),
                        m.getOptionE(), m.getOptionF(),
                        m.getCorrectAnswer()));
            }
        }

        similar.sort((a, b) -> b.getSimilarityPercent() - a.getSimilarityPercent());
        return new SimilarityCheckResponse(!similar.isEmpty(), similar);
    }

    private int calculateSimilarityPercent(
            String stem1, String a1, String b1, String c1, String d1, String e1, String f1,
            String stem2, String a2, String b2, String c2, String d2, String e2, String f2) {

        String normalizedStem1 = normalizeForSimilarity(stem1);
        String normalizedStem2 = normalizeForSimilarity(stem2);
        if (!normalizedStem1.isEmpty() && normalizedStem1.equals(normalizedStem2)) {
            return 100;
        }

        int stemSimilarity = (int) Math.round(
            jaccardSimilarity(tokenize(normalizedStem1), tokenize(normalizedStem2)) * 100.0);

        Set<String> options1 = normalizedOptions(a1, b1, c1, d1, e1, f1);
        Set<String> options2 = normalizedOptions(a2, b2, c2, d2, e2, f2);
        int optionSimilarity = (int) Math.round(jaccardSimilarity(options1, options2) * 100.0);

        return (int) Math.round(stemSimilarity * 0.85 + optionSimilarity * 0.15);
    }

    private String normalizeForSimilarity(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Set<String> tokenize(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> tokens = new HashSet<>();
        for (String token : normalizedText.split("\\s+")) {
            if (token.length() >= 3 && !SIMILARITY_STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Set<String> normalizedOptions(String... options) {
        Set<String> result = new HashSet<>();
        for (String option : options) {
            String normalized = normalizeForSimilarity(option);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private double jaccardSimilarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);

        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private String buildGenerationPrompt(String stackName, String topicName,
                                     String difficulty, int count, List<String> existingStems, String questionType) {
        String normalizedType = normalizeQuestionType(questionType);
        String answerFormatRules = "MCQ".equals(normalizedType)
                ? "- Exactly one correct answer.\n- correctAnswer must be a single uppercase letter from A to D."
                : "- Multiple correct answers are required.\n- At least 2 correct answers.\n- correctAnswer must be comma-separated uppercase letters (e.g., \"A,C,E\") sorted alphabetically with no duplicates.";

        String optionRules = "MCQ".equals(normalizedType)
                ? "- Each question must have exactly 4 options (A, B, C, D)."
                : "- Each question must have 4 to 6 options. A-D are mandatory, E and F are optional.";

        String responseExample = "MCQ".equals(normalizedType)
                ? """
            [
            {
                \"questionStem\": \"...\",
                \"optionA\": \"...\",
                \"optionB\": \"...\",
                \"optionC\": \"...\",
                \"optionD\": \"...\",
                \"correctAnswer\": \"A\"
            }
            ]
            """
                : """
            [
            {
                \"questionStem\": \"...\",
                \"optionA\": \"...\",
                \"optionB\": \"...\",
                \"optionC\": \"...\",
                \"optionD\": \"...\",
                \"optionE\": \"...\",
                \"optionF\": \"...\",
                \"correctAnswer\": \"A,C\"
            }
            ]
            """;

        String avoidSection = "";
        if (existingStems != null && !existingStems.isEmpty()) {
            StringBuilder avoid = new StringBuilder();
            avoid.append("\\n\\nEXISTING QUESTION STEMS TO AVOID (MANDATORY):\\n");
            for (String stem : existingStems) {
                avoid.append("- ").append(stem).append("\\n");
            }
            avoid.append("\\nDo NOT generate any question that is semantically similar to these stems.\\n");
            avoidSection = avoid.toString();
        }

        return String.format("""
            You are an expert technical trainer creating HIGH-QUALITY, CONCEPTUALLY DISTINCT, and NON-OVERLAPPING %s questions.

            Generate EXACTLY %d unique %s questions.

            Context:
            - Technology Stack: %s
            - Topic: %s
            - Difficulty: %s

            STRICT REQUIREMENTS (MANDATORY):
            1. Each question MUST test a clearly different concept, subtopic, behavior, edge case, or practical use case.
            2. DO NOT generate paraphrases, rewordings, pattern variants, or minor twists of the same underlying concept.
            3. DO NOT repeat the same answer logic, reasoning path, or distractor pattern across multiple questions.
            4. Ensure strong conceptual separation across all questions.
            5. Distribute questions across different angles whenever possible, such as:
            - Definitions
            - Real-world scenarios
            - Code behavior
            - Edge cases
            - Performance considerations
            - Best practices
            - Debugging/troubleshooting
            - Design decisions

            SHARED SIMILARITY RULESET (USE THIS FOR SELF-CHECK):
            For every pair of generated questions, evaluate similarity using the SAME criteria that will be used for duplicate detection:
            - Concept tested / learning objective
            - Problem intent / what the user must reason about
            - Answer logic required to solve it
            - Scenario/use-case overlap
            - Option structure overlap

            SIMILARITY THRESHOLD:
            - Final similarity between ANY pair of questions MUST be LESS THAN 30%%.
            - If similarity is 30%% or higher for any pair, REGENERATE those questions before finalizing.

            IMPORTANT NORMALIZATION RULES:
            - Questions are considered too similar if they test the same core concept, even if phrased differently.
            - Questions are considered too similar if they require nearly the same reasoning to arrive at the correct answer.
            - Questions are considered too similar if they differ only by variable names, examples, wording style, or scenario decoration.
            - Questions are NOT distinct just because the wording is different.

            QUALITY RULES:
            %s
            %s
            - Options must be plausible, clearly distinct, and non-overlapping.
            - Avoid vague, generic, or trivia-style questions.
            - Keep question styles varied.

            FINAL INTERNAL CHECK BEFORE OUTPUT:
            - Compare every question against every other question.
            - Remove or regenerate any question pair with similarity >= 30%%.
            - Ensure the final set is diverse in concept, framing, and reasoning.

            RESPONSE FORMAT (STRICT):
            - Return ONLY valid JSON
            - NO explanation
            - NO markdown
            - NO extra text
            - Ensure JSON object keys exactly match the schema example.

            %s


            %s
            """,
            normalizedType,
            count,
            normalizedType,
            stackName,
            topicName,
            difficulty,
            optionRules,
            answerFormatRules,
            avoidSection,
            responseExample);
    }

    private List<MCQDTO.CreateRequest> parseGeneratedQuestions(
            String rawJson, Long stackId, Long topicId, String difficulty, String questionType) {
        String normalizedQuestionType = normalizeQuestionType(questionType);
        try {
            JsonNode arr;
            try {
                arr = parseQuestionArrayNode(rawJson);
            } catch (Exception primaryParseError) {
                List<MCQDTO.CreateRequest> fallback = parseLooseTextQuestions(rawJson, stackId, topicId, difficulty, normalizedQuestionType);
                if (!fallback.isEmpty()) {
                    log.warn("Used loose-text fallback parser for AI response.");
                    return fallback;
                }
                throw primaryParseError;
            }
            List<MCQDTO.CreateRequest> results = new ArrayList<>();

            for (JsonNode node : arr) {
                MCQDTO.CreateRequest req = toCreateRequest(node, stackId, topicId, difficulty, normalizedQuestionType);
                if (req != null) {
                    results.add(req);
                }
            }

            if (results.isEmpty()) {
                log.error("AI response parsed but no valid questions found. Raw snippet: {}",
                        safeSnippet(rawJson));
                throw new RuntimeException("Failed to parse AI-generated questions. Please try again.");
            }

            return results;
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}. Raw snippet: {}", e.getMessage(), safeSnippet(rawJson));
            throw new RuntimeException("Failed to parse AI-generated questions. Please try again.");
        }
    }

    private List<MCQDTO.CreateRequest> parseLooseTextQuestions(
            String rawText, Long stackId, Long topicId, String difficulty, String questionType) {

        String normalizedQuestionType = normalizeQuestionType(questionType);
        boolean isMsq = "MSQ".equals(normalizedQuestionType);

        List<MCQDTO.CreateRequest> results = new ArrayList<>();
        String cleaned = preprocessModelOutput(rawText);

        String[] lines = cleaned.split("\\R");
        String currentStem = null;
        String currentA = null;
        String currentB = null;
        String currentC = null;
        String currentD = null;
        String currentAnswer = null;

        Pattern questionStart = Pattern.compile("^\\s*(?:\\d+[\\).:-]\\s*|Q(?:uestion)?\\s*\\d*\\s*[:).-]?\\s*)(.+)$", Pattern.CASE_INSENSITIVE);
        Pattern answerLine = Pattern.compile("^\\s*(?:correct\\s*answer|answer)\\s*[:=-]\\s*([A-F1-6,\\s]+)\\b", Pattern.CASE_INSENSITIVE);

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            Matcher qMatcher = questionStart.matcher(line);
            Matcher optionMatcher = MCQ_LINE_OPTION_PATTERN.matcher(line);
            Matcher ansMatcher = answerLine.matcher(line);

            if (qMatcher.find()) {
                if (!isBlank(currentStem) && !isBlank(currentA) && !isBlank(currentB) && !isBlank(currentC) && !isBlank(currentD)) {
                    MCQDTO.CreateRequest req = new MCQDTO.CreateRequest();
                    req.setQuestionStem(currentStem.trim());
                    req.setStackId(stackId);
                    req.setTopicId(topicId);
                    req.setDifficulty(difficulty);
                    req.setQuestionType(normalizedQuestionType);
                    req.setOptionA(currentA.trim());
                    req.setOptionB(currentB.trim());
                    req.setOptionC(currentC.trim());
                    req.setOptionD(currentD.trim());
                    req.setCorrectAnswer(formatCorrectAnswer(firstNonBlank(currentAnswer, "A"), isMsq));
                    req.setSendForReview(false);
                    if (isValidCorrectAnswer(req, isMsq)) {
                        results.add(req);
                    }
                }

                currentStem = qMatcher.group(1).trim();
                currentA = null;
                currentB = null;
                currentC = null;
                currentD = null;
                currentAnswer = null;
                continue;
            }

            if (optionMatcher.find()) {
                String letter = optionMatcher.group(1).toUpperCase();
                String value = optionMatcher.group(2);
                if ("A".equals(letter)) currentA = value;
                if ("B".equals(letter)) currentB = value;
                if ("C".equals(letter)) currentC = value;
                if ("D".equals(letter)) currentD = value;
                continue;
            }

            if (ansMatcher.find()) {
                currentAnswer = ansMatcher.group(1).trim();
            }
        }

        if (!isBlank(currentStem) && !isBlank(currentA) && !isBlank(currentB) && !isBlank(currentC) && !isBlank(currentD)) {
            MCQDTO.CreateRequest req = new MCQDTO.CreateRequest();
            req.setQuestionStem(currentStem.trim());
            req.setStackId(stackId);
            req.setTopicId(topicId);
            req.setDifficulty(difficulty);
            req.setQuestionType(normalizedQuestionType);
            req.setOptionA(currentA.trim());
            req.setOptionB(currentB.trim());
            req.setOptionC(currentC.trim());
            req.setOptionD(currentD.trim());
            req.setCorrectAnswer(formatCorrectAnswer(firstNonBlank(currentAnswer, "A"), isMsq));
            req.setSendForReview(false);
            if (isValidCorrectAnswer(req, isMsq)) {
                results.add(req);
            }
        }

        if (!results.isEmpty()) {
            return results;
        }

        String stem = firstNonBlank(
                extractQuotedField(cleaned, "questionStem"),
                extractQuotedField(cleaned, "question"),
                extractQuotedField(cleaned, "stem"),
                extractQuestionLine(cleaned));

        String optionA = firstNonBlank(extractQuotedField(cleaned, "optionA"), extractOptionLine(cleaned, "A"));
        String optionB = firstNonBlank(extractQuotedField(cleaned, "optionB"), extractOptionLine(cleaned, "B"));
        String optionC = firstNonBlank(extractQuotedField(cleaned, "optionC"), extractOptionLine(cleaned, "C"));
        String optionD = firstNonBlank(extractQuotedField(cleaned, "optionD"), extractOptionLine(cleaned, "D"));

        if (isBlank(stem) || isBlank(optionA) || isBlank(optionB) || isBlank(optionC) || isBlank(optionD)) {
            return results;
        }

        String rawAnswer = firstNonBlank(
                extractQuotedField(cleaned, "correctAnswer"),
                extractQuotedField(cleaned, "answer"),
                extractAnswerLine(cleaned),
                "A");

        MCQDTO.CreateRequest req = new MCQDTO.CreateRequest();
        req.setQuestionStem(stem.trim());
        req.setStackId(stackId);
        req.setTopicId(topicId);
        req.setDifficulty(difficulty);
        req.setQuestionType(normalizedQuestionType);
        req.setOptionA(optionA.trim());
        req.setOptionB(optionB.trim());
        req.setOptionC(optionC.trim());
        req.setOptionD(optionD.trim());
        req.setCorrectAnswer(formatCorrectAnswer(rawAnswer, isMsq));
        req.setSendForReview(false);
        if (isValidCorrectAnswer(req, isMsq)) {
            results.add(req);
        }

        return results;
    }

    private String extractQuotedField(String text, String fieldName) {
        if (isBlank(text) || isBlank(fieldName)) {
            return "";
        }
        Pattern p = Pattern.compile(
                "(?is)[\"']?" + Pattern.quote(fieldName) + "[\"']?\\s*[:=]\\s*([\"'])(.*?)\\1");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(2).trim();
        }
        return "";
    }

    private String extractQuestionLine(String text) {
        if (isBlank(text)) {
            return "";
        }
        Matcher m = Pattern.compile("(?m)^\\s*(?:Q(?:uestion)?\\s*[:.)-])\\s*(.+)$").matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private String extractOptionLine(String text, String letter) {
        if (isBlank(text) || isBlank(letter)) {
            return "";
        }
        Matcher m = MCQ_LINE_OPTION_PATTERN.matcher(text);
        while (m.find()) {
            if (letter.equalsIgnoreCase(m.group(1))) {
                return m.group(2).trim();
            }
        }
        return "";
    }

    private String extractAnswerLine(String text) {
        if (isBlank(text)) {
            return "";
        }
        Matcher m = Pattern.compile("(?mi)^\\s*(?:correct\\s*answer|answer)\\s*[:=-]\\s*([A-F1-6])\\b").matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private JsonNode parseQuestionArrayNode(String rawJson) throws Exception {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("AI response is empty");
        }

        String cleaned = preprocessModelOutput(rawJson);

        // Try direct parse first.
        try {
            JsonNode node = readTreeLenient(cleaned);
            JsonNode arr = resolveArrayNode(node);
            if (arr != null && arr.isArray()) {
                return arr;
            }
        } catch (Exception ignored) {
            // Fallback to extracting JSON fragments below.
        }

        // Try extracting a JSON array embedded in prose.
        String arrayFragment = extractBalancedJson(cleaned, '[', ']');
        if (arrayFragment != null) {
            JsonNode node = readTreeLenient(arrayFragment);
            if (node.isArray()) {
                return node;
            }
        }

        // Try extracting an object that contains a questions array.
        String objectFragment = extractBalancedJson(cleaned, '{', '}');
        if (objectFragment != null) {
            JsonNode node = readTreeLenient(objectFragment);
            JsonNode arr = resolveArrayNode(node);
            if (arr != null && arr.isArray()) {
                return arr;
            }
        }

        throw new IllegalArgumentException("No valid questions array found in AI response");
    }

    private JsonNode resolveArrayNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (node.isTextual()) {
            String value = stripCodeFences(node.asText());
            try {
                return resolveArrayNode(readTreeLenient(value));
            } catch (Exception ignored) {
                return null;
            }
        }
        if (!node.isObject()) {
            return null;
        }

        // Accept a single-question object as an array of one element.
        if (looksLikeSingleQuestionObject(node)) {
            return objectMapper.createArrayNode().add(node);
        }

        String[] arrayKeys = {"questions", "mcqs", "items", "data", "result"};
        for (String key : arrayKeys) {
            JsonNode candidate = node.path(key);
            if (candidate.isArray()) {
                return candidate;
            }
            if (candidate.isTextual()) {
                try {
                    JsonNode parsed = readTreeLenient(stripCodeFences(candidate.asText()));
                    JsonNode nested = resolveArrayNode(parsed);
                    if (nested != null && nested.isArray()) {
                        return nested;
                    }
                } catch (Exception ignored) {
                    // continue
                }
            }
        }

        // Some models return OpenAI-style payloads: { choices: [{ message: { content: "[...]" }}] }
        JsonNode choices = node.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            String content = first.path("message").path("content").asText(null);
            if (content != null) {
                try {
                    return resolveArrayNode(readTreeLenient(stripCodeFences(content)));
                } catch (Exception ignored) {
                    // continue
                }
            }
        }

        return null;
    }

    private boolean looksLikeSingleQuestionObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }

        boolean hasStem = node.has("questionStem") || node.has("question") || node.has("stem");
        boolean hasOptions =
                (node.has("optionA") && node.has("optionB") && node.has("optionC") && node.has("optionD"))
                || node.has("options");

        return hasStem && hasOptions;
    }

    private MCQDTO.CreateRequest toCreateRequest(JsonNode node, Long stackId, Long topicId, String difficulty, String questionType) {
        String normalizedQuestionType = normalizeQuestionType(questionType);
        boolean isMsq = "MSQ".equals(normalizedQuestionType);
        String stem = firstNonBlank(
                node.path("questionStem").asText(null),
                node.path("question").asText(null),
                node.path("stem").asText(null));

        String optionA = node.path("optionA").asText(null);
        String optionB = node.path("optionB").asText(null);
        String optionC = node.path("optionC").asText(null);
        String optionD = node.path("optionD").asText(null);

        optionA = firstNonBlank(optionA, node.path("A").asText(null), node.path("option_1").asText(null));
        optionB = firstNonBlank(optionB, node.path("B").asText(null), node.path("option_2").asText(null));
        optionC = firstNonBlank(optionC, node.path("C").asText(null), node.path("option_3").asText(null));
        optionD = firstNonBlank(optionD, node.path("D").asText(null), node.path("option_4").asText(null));

        JsonNode options = node.path("options");
        if ((isBlank(optionA) || isBlank(optionB) || isBlank(optionC) || isBlank(optionD))
                && options.isArray() && options.size() >= 4) {
            optionA = firstNonBlank(optionA, options.get(0).asText(""));
            optionB = firstNonBlank(optionB, options.get(1).asText(""));
            optionC = firstNonBlank(optionC, options.get(2).asText(""));
            optionD = firstNonBlank(optionD, options.get(3).asText(""));
        }

        if ((isBlank(optionA) || isBlank(optionB) || isBlank(optionC) || isBlank(optionD)) && options.isObject()) {
            optionA = firstNonBlank(optionA, options.path("A").asText(null), options.path("a").asText(null), options.path("1").asText(null));
            optionB = firstNonBlank(optionB, options.path("B").asText(null), options.path("b").asText(null), options.path("2").asText(null));
            optionC = firstNonBlank(optionC, options.path("C").asText(null), options.path("c").asText(null), options.path("3").asText(null));
            optionD = firstNonBlank(optionD, options.path("D").asText(null), options.path("d").asText(null), options.path("4").asText(null));
        }

        if (isBlank(stem) || isBlank(optionA) || isBlank(optionB) || isBlank(optionC) || isBlank(optionD)) {
            return null;
        }

        String rawAnswer = firstNonBlank(
                node.path("correctAnswer").asText(null),
                node.path("answer").asText(null),
            node.path("correct_option").asText(null),
                "A");

        String optionE = firstNonBlank(node.path("optionE").asText(null), node.path("E").asText(null));
        String optionF = firstNonBlank(node.path("optionF").asText(null), node.path("F").asText(null));

        MCQDTO.CreateRequest req = new MCQDTO.CreateRequest();
        req.setQuestionStem(stem.trim());
        req.setStackId(stackId);
        req.setTopicId(topicId);
        req.setDifficulty(difficulty);
        req.setQuestionType(normalizedQuestionType);
        req.setOptionA(optionA.trim());
        req.setOptionB(optionB.trim());
        req.setOptionC(optionC.trim());
        req.setOptionD(optionD.trim());
        req.setOptionE(isBlank(optionE) ? null : optionE.trim());
        req.setOptionF(isBlank(optionF) ? null : optionF.trim());
        req.setCorrectAnswer(formatCorrectAnswer(rawAnswer, isMsq));
        req.setSendForReview(false);
        return isValidCorrectAnswer(req, isMsq) ? req : null;
    }

    private String stripCodeFences(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();
    }

    private String preprocessModelOutput(String raw) {
        if (raw == null) {
            return "";
        }

        String cleaned = stripCodeFences(raw);
        cleaned = cleaned.replaceAll("(?is)<think>.*?</think>", " ");
        cleaned = cleaned.replace('\u201c', '"').replace('\u201d', '"')
                .replace('\u2018', '\'').replace('\u2019', '\'');

        // Remove dangling commas before object/array close to handle lenient-but-common model output.
        cleaned = cleaned.replaceAll(",\\s*([}\\]])", "$1");
        return cleaned.trim();
    }

    private JsonNode readTreeLenient(String input) throws Exception {
        try {
            return objectMapper.readTree(input);
        } catch (Exception strictEx) {
            return lenientObjectMapper.readTree(input);
        }
    }

    private String extractBalancedJson(String text, char open, char close) {
        int start = text.indexOf(open);
        if (start < 0) {
            return null;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        return null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return "";
    }

    private String normalizeQuestionType(String questionType) {
        if (questionType == null) {
            return "MCQ";
        }
        return "MSQ".equalsIgnoreCase(questionType.trim()) ? "MSQ" : "MCQ";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String extractAnswerLetter(String rawAnswer) {
        if (isBlank(rawAnswer)) {
            return "A";
        }
        String normalized = rawAnswer.trim();

        if (normalized.matches("^[1-6]$")) {
            int idx = Integer.parseInt(normalized);
            return String.valueOf((char) ('A' + idx - 1));
        }

        Matcher m = Pattern.compile("[A-F]", Pattern.CASE_INSENSITIVE).matcher(rawAnswer.trim());
        if (m.find()) {
            return m.group().toUpperCase();
        }
        return "A";
    }

    private String formatCorrectAnswer(String rawAnswer, boolean isMsq) {
        if (!isMsq) {
            return extractAnswerLetter(rawAnswer);
        }

        List<String> letters = extractAnswerLetters(rawAnswer);
        if (letters.isEmpty()) {
            return "A,C";
        }
        return String.join(",", letters);
    }

    private List<String> extractAnswerLetters(String rawAnswer) {
        if (isBlank(rawAnswer)) {
            return Collections.emptyList();
        }

        Set<String> allowed = Set.of("A", "B", "C", "D", "E", "F");
        Set<String> ordered = new HashSet<>();
        Matcher m = Pattern.compile("[A-F1-6]", Pattern.CASE_INSENSITIVE).matcher(rawAnswer);
        while (m.find()) {
            String token = m.group().toUpperCase();
            if (token.matches("[1-6]")) {
                int idx = Integer.parseInt(token);
                token = String.valueOf((char) ('A' + idx - 1));
            }
            if (allowed.contains(token)) {
                ordered.add(token);
            }
        }

        return ALL_OPTION_KEYS.stream()
                .filter(ordered::contains)
                .collect(Collectors.toList());
    }

    private boolean isValidCorrectAnswer(MCQDTO.CreateRequest req, boolean isMsq) {
        List<String> letters = extractAnswerLetters(req.getCorrectAnswer());
        if (!isMsq) {
            return letters.size() == 1 && List.of("A", "B", "C", "D").contains(letters.get(0));
        }

        if (letters.size() < 2) {
            return false;
        }

        Set<String> available = new HashSet<>();
        if (!isBlank(req.getOptionA())) available.add("A");
        if (!isBlank(req.getOptionB())) available.add("B");
        if (!isBlank(req.getOptionC())) available.add("C");
        if (!isBlank(req.getOptionD())) available.add("D");
        if (!isBlank(req.getOptionE())) available.add("E");
        if (!isBlank(req.getOptionF())) available.add("F");

        return letters.stream().allMatch(available::contains);
    }

    private static final List<String> ALL_OPTION_KEYS = List.of("A", "B", "C", "D", "E", "F");

    private String safeSnippet(String text) {
        if (text == null) {
            return "<null>";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 400 ? normalized.substring(0, 400) + "..." : normalized;
    }

    // â”€â”€ SIMILARITY CHECK â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public SimilarityCheckResponse checkSimilarity(
            String questionStem, String optionA, String optionB,
            String optionC, String optionD, String optionE, String optionF,
            Long stackId, Long topicId, Long excludeQuestionId) {

        // Fetch all existing questions in same stack + topic across statuses.
        List<MCQ> existing = mcqRepository.findNonDraftByStackAndTopic(
            stackId,
            topicId,
            excludeQuestionId,
            List.of(
                MCQ.Status.DRAFT,
                MCQ.Status.READY_FOR_REVIEW,
                MCQ.Status.UNDER_REVIEW,
                MCQ.Status.APPROVED,
                MCQ.Status.REJECTED));

        if (existing.isEmpty()) {
            return new SimilarityCheckResponse(false, Collections.emptyList());
        }

        SimilarityCheckResponse deterministic = runDeterministicSimilarityCheck(
                questionStem, optionA, optionB, optionC, optionD, optionE, optionF, existing);
        if (deterministic.isHasSimilar()) {
            return deterministic;
        }

        // Build candidate text
        String candidate = buildQuestionText(questionStem, optionA, optionB, optionC, optionD, optionE, optionF);

        // Build prompt for similarity scoring
        String prompt = buildSimilarityPrompt(candidate, existing);
        String rawResponse = callModel(prompt);
        SimilarityCheckResponse aiResult = parseSimilarityResponse(rawResponse, existing);
        return aiResult.isHasSimilar() ? aiResult : deterministic;
    }

    private String buildQuestionText(String stem, String a, String b, String c, String d,
                                      String e, String f) {
        StringBuilder sb = new StringBuilder();
        sb.append("Q: ").append(stem).append("\n");
        sb.append("A. ").append(a).append("\n");
        sb.append("B. ").append(b).append("\n");
        sb.append("C. ").append(c).append("\n");
        sb.append("D. ").append(d).append("\n");
        if (e != null && !e.isBlank()) sb.append("E. ").append(e).append("\n");
        if (f != null && !f.isBlank()) sb.append("F. ").append(f).append("\n");
        return sb.toString();
    }

    private String buildSimilarityPrompt(String candidate, List<MCQ> existing) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            
        You are a STRICT quiz question similarity and duplicate detector.

            Your job is to identify question pairs whose similarity is GREATER THAN 30%.
            

            IMPORTANT RULES:
            - Evaluate similarity based on the CONCEPT being tested, not just wording.
            - Do NOT treat questions as different if they are only reworded, reframed, or wrapped in different examples.
            - Do NOT ignore same-concept questions just because the option text is different.
            - If two questions test substantially the same learning objective or require nearly the same reasoning, they should be flagged.

            SIMILARITY CRITERIA (MANDATORY):
            Assess similarity using ALL of the following:
            1. Core concept being tested
            2. Problem intent / what the learner must determine
            3. Answer logic / reasoning path needed
            4. Scenario or use-case overlap
            5. Option structure or answer pattern overlap

            DECISION RULE:
            - Similarity > 30%  â†’ FLAG the pair
            - Similarity <= 30% â†’ DO NOT include the pair in the output

            SCORING GUIDANCE:
            - 0-15   â†’ Clearly different concepts
            - 16-30  â†’ Related topic but materially different concept/reasoning
            - 31-50  â†’ Noticeable overlap in concept or reasoning
            - 51-70  â†’ Strong overlap; likely redundant
            - 71-100 â†’ Near-duplicate, paraphrase, or same concept with same answer logic

            CRITICAL INSTRUCTION:
            - Return ONLY question pairs whose similarity is GREATER THAN 30%.
            - Ignore all pairs with similarity 30% or below.
            - Be precise and concept-focused.

            OUTPUT FORMAT:
            For each flagged pair, include:
            - question_1
            - question_2
            - similarity_percentage
            - brief_reason

            Return ONLY valid JSON.
            No explanation.
            No markdown.
            No extra text.


            """);
        sb.append(candidate).append("\n\n");
        sb.append("EXISTING QUESTIONS:\n");

        for (MCQ m : existing) {
            sb.append("---\n");
            sb.append("ID: ").append(m.getQuestionId()).append("\n");
            sb.append("Q: ").append(m.getQuestionStem()).append("\n");
            sb.append("A. ").append(m.getOptionA()).append("\n");
            sb.append("B. ").append(m.getOptionB()).append("\n");
            sb.append("C. ").append(m.getOptionC()).append("\n");
            sb.append("D. ").append(m.getOptionD()).append("\n");
            if (m.getOptionE() != null) sb.append("E. ").append(m.getOptionE()).append("\n");
            if (m.getOptionF() != null) sb.append("F. ").append(m.getOptionF()).append("\n");
        }

        sb.append("""
            
            Respond ONLY with a valid JSON array listing only questions with similarity >= 30%.
            No preamble, no markdown, just the JSON array.
            Format:
            [{"id": 123, "similarity": 42}, {"id": 456, "similarity": 35}]
            
            If no questions have similarity >= 30%, respond with: []
            """);

        return sb.toString();
    }

    private SimilarityCheckResponse parseSimilarityResponse(String rawJson, List<MCQ> existing) {
        try {
            String json = rawJson.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("```\\s*$", "")
                    .trim();

            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root;
            if (!arr.isArray()) {
                if (root.path("matches").isArray()) {
                    arr = root.path("matches");
                } else if (root.path("similarQuestions").isArray()) {
                    arr = root.path("similarQuestions");
                } else {
                    arr = objectMapper.createArrayNode();
                }
            }
            Map<Long, MCQ> existingMap = existing.stream()
                    .collect(Collectors.toMap(MCQ::getQuestionId, m -> m));

            List<SimilarityCheckResponse.SimilarQuestion> similarList = new ArrayList<>();
            boolean hasSimilar = false;

            for (JsonNode node : arr) {
                long id = extractQuestionId(node);
                int similarity = extractSimilarityPercent(node);

                if (id <= 0 || similarity <= 0) {
                    continue;
                }

                if (similarity >= (int)(similarityThreshold * 100)) {
                    hasSimilar = true;
                    MCQ m = existingMap.get(id);
                    if (m != null) {
                        similarList.add(new SimilarityCheckResponse.SimilarQuestion(
                                m.getQuestionId(),
                                similarity,
                                m.getQuestionStem(),
                                m.getOptionA(), m.getOptionB(),
                                m.getOptionC(), m.getOptionD(),
                                m.getOptionE(), m.getOptionF(),
                                m.getCorrectAnswer()
                        ));
                    }
                }
            }

            // Sort by similarity descending
            similarList.sort((a, b) -> b.getSimilarityPercent() - a.getSimilarityPercent());

            return new SimilarityCheckResponse(hasSimilar, similarList);
        } catch (Exception e) {
            log.error("Failed to parse similarity response: {}", e.getMessage());
            return new SimilarityCheckResponse(false, Collections.emptyList());
        }
    }

    private long extractQuestionId(JsonNode node) {
        long id = node.path("id").asLong(0);
        if (id > 0) return id;

        id = node.path("questionId").asLong(0);
        if (id > 0) return id;

        id = node.path("existingQuestionId").asLong(0);
        if (id > 0) return id;

        String text = node.path("question_2").asText("");
        Matcher matcher = ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }

        return 0;
    }

    private int extractSimilarityPercent(JsonNode node) {
        if (node.path("similarity").canConvertToInt()) {
            return node.path("similarity").asInt();
        }
        if (node.path("similarity_percentage").canConvertToInt()) {
            return node.path("similarity_percentage").asInt();
        }
        if (node.path("similarityPercent").canConvertToInt()) {
            return node.path("similarityPercent").asInt();
        }

        if (node.path("score").isNumber()) {
            double score = node.path("score").asDouble();
            if (score <= 1.0) {
                return (int) Math.round(score * 100.0);
            }
            return (int) Math.round(score);
        }

        return 0;
    }
}
