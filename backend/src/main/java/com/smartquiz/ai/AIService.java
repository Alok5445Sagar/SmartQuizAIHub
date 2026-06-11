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
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
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

    // @Value("${app.ai.api-key}")
    // private String apiKey;

    @Value("${app.ai.api-url}")
    private String apiUrl;

    @Value("${app.ai.model}")
    private String model;

    @Value("${app.ai.similarity-threshold:0.70}")
    private double similarityThreshold;

    @Value("${app.ai.timeout-seconds:300}")
    private int aiTimeoutSeconds;

    @Value("${app.ai.generate-use-ai-similarity:false}")
    private boolean generateUseAiSimilarity;

    @Value("${app.ai.generate-retry-multiplier:2}")
    private int generateRetryMultiplier;

    @Value("${app.ai.generate-min-attempts:2}")
    private int generateMinAttempts;

    @Value("${app.ai.max-output-tokens:1200}")
    private int maxOutputTokens;

    @Autowired
    private MCQRepository mcqRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private ObjectMapper lenientObjectMapper;

    private WebClient webClient;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.lenientObjectMapper = objectMapper.copy()
            .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature(), true);
    }
    // ── AI QUESTION GENERATION ────────────────────────────────────

    public List<MCQDTO.CreateRequest> generateQuestions(
            TechnologyStack stack, Topic topic, String difficulty, int count,
            Long creatorStackId, Long creatorTopicId) {

        List<String> existingStems = getExistingStemsForPrompt(stack.getStackId(), topic.getTopicId(), 30);

        String prompt = buildGenerationPrompt(
            stack.getStackName(), topic.getTopicName(), difficulty, count, existingStems);
        String rawResponse = callOllamaAPI(prompt);

        List<MCQDTO.CreateRequest> generated = parseGeneratedQuestions(
                rawResponse, stack.getStackId(), topic.getTopicId(), difficulty);

        Collection<MCQ.Status> generationStatuses = List.of(
            MCQ.Status.READY_FOR_REVIEW,
            MCQ.Status.UNDER_REVIEW,
            MCQ.Status.APPROVED);
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

            if (!check.isHasSimilar() && !hasSimilarInBatch(req, screened)) {
                screened.add(req);
            } else {
                log.info("Generated question screened out due to similarity ≥30%. Requesting replacement.");
            }
        }

        // If we don't have enough after screening, generate more.
        while (screened.size() < count && attempts < maxAttempts) {
            attempts++;
            int stillNeeded = count - screened.size();
            if (stillNeeded > 0) {
                List<String> retryAvoidStems = buildRetryAvoidStems(existingStems, screened, 80);
                int retryRequestCount = Math.min(stillNeeded + Math.max(1, stillNeeded), 20);
                String retryPrompt = buildGenerationPrompt(
                    stack.getStackName(), topic.getTopicName(), difficulty, retryRequestCount, retryAvoidStems);
                String retryResponse = callOllamaAPI(retryPrompt);
                List<MCQDTO.CreateRequest> retryGenerated = parseGeneratedQuestions(
                        retryResponse, stack.getStackId(), topic.getTopicId(), difficulty);

                for (MCQDTO.CreateRequest req : retryGenerated) {
                    if (screened.size() >= count) break;
                    SimilarityCheckResponse check = checkSimilarityForGeneration(
                            req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                            req.getOptionC(), req.getOptionD(), null, null,
                            existingForGeneration);
                    if (!check.isHasSimilar() && !hasSimilarInBatch(req, screened)) {
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
            List<String> singleAvoidStems = buildRetryAvoidStems(existingStems, screened, 120);
            String singlePrompt = buildGenerationPrompt(
                    stack.getStackName(), topic.getTopicName(), difficulty, 1, singleAvoidStems);
            String singleResponse = callOllamaAPI(singlePrompt);
            List<MCQDTO.CreateRequest> singleGenerated = parseGeneratedQuestions(
                    singleResponse, stack.getStackId(), topic.getTopicId(), difficulty);

            for (MCQDTO.CreateRequest req : singleGenerated) {
                SimilarityCheckResponse check = checkSimilarityForGeneration(
                        req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                        req.getOptionC(), req.getOptionD(), null, null,
                        existingForGeneration);
                if (!check.isHasSimilar() && !hasSimilarInBatch(req, screened)) {
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

    private List<String> getExistingStemsForPrompt(Long stackId, Long topicId, int limit) {
        List<MCQ> existing = mcqRepository.findNonDraftByStackAndTopic(
                stackId,
                topicId,
                null,
                List.of(MCQ.Status.READY_FOR_REVIEW, MCQ.Status.UNDER_REVIEW, MCQ.Status.APPROVED));

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
        String rawResponse = callOllamaAPI(prompt);
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
                                     String difficulty, int count) {
        return buildGenerationPrompt(stackName, topicName, difficulty, count, Collections.emptyList());
    }

    private String buildGenerationPrompt(String stackName, String topicName,
                                     String difficulty, int count, List<String> existingStems) {
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
            You are an expert technical trainer creating HIGH-QUALITY, CONCEPTUALLY DISTINCT, and NON-OVERLAPPING MCQ questions.

            Generate EXACTLY %d unique multiple-choice questions.

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
            - Each question must have exactly 4 options (A, B, C, D).
            - Only ONE correct answer.
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

            %s


            [
            {
                "questionStem": "...",
                "optionA": "...",
                "optionB": "...",
                "optionC": "...",
                "optionD": "...",
                "correctAnswer": "A"
            }
            ]
            """, count, stackName, topicName, difficulty, avoidSection);
    }

    private String callOllamaAPI(String prompt) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            requestBody.put("format", "json");
            requestBody.put("keep_alive", "30m");

            Map<String, Object> options = new HashMap<>();
            options.put("temperature", 0.2);
            options.put("top_p", 0.9);
            options.put("num_predict", maxOutputTokens);
            requestBody.put("options", options);

            String response = webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(aiTimeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.hasNonNull("error") && !root.path("error").asText().isBlank()) {
                throw new RuntimeException("Ollama error: " + root.path("error").asText());
            }

            String modelResponse = extractModelResponse(root);
            if (modelResponse.isBlank()) {
                throw new RuntimeException("Ollama returned an empty response payload. Raw: " + safeSnippet(response));
            }

            return modelResponse;
        } catch (Exception e) {
            String msg = (e.getMessage() == null) ? "" : e.getMessage().toLowerCase();
            log.error("Ollama API call failed: {}", e.getMessage());

            if (msg.contains("timed out") || msg.contains("timeout")) {
                throw new RuntimeException(
                        "AI model response timed out. Ollama is running, but model '" + model
                                + "' is responding too slowly. Try a smaller question count, or increase app.ai.timeout-seconds.");
            }

            if (msg.contains("connection refused") || msg.contains("failed to connect") || msg.contains("no route")) {
                throw new RuntimeException("AI service unavailable. Make sure Ollama is running locally.");
            }

            if (msg.contains("model") && msg.contains("not found")) {
                throw new RuntimeException("Configured AI model '" + model + "' was not found in Ollama.");
            }

            throw new RuntimeException("AI service error: " + e.getMessage());
        }
    }

    private String extractModelResponse(JsonNode root) {
        if (root == null || root.isNull()) {
            return "";
        }

        JsonNode responseNode = root.path("response");
        if (!responseNode.isMissingNode() && !responseNode.isNull()) {
            if (responseNode.isTextual()) {
                String value = responseNode.asText("").trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
            if (responseNode.isObject() || responseNode.isArray()) {
                return responseNode.toString();
            }
        }

        // Some runtimes/models may return chat-style content shape.
        JsonNode messageContent = root.path("message").path("content");
        if (!messageContent.isMissingNode() && !messageContent.isNull()) {
            String value = messageContent.asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }

        // Fallback for alternate naming.
        JsonNode contentNode = root.path("content");
        if (!contentNode.isMissingNode() && !contentNode.isNull()) {
            if (contentNode.isTextual()) {
                String value = contentNode.asText("").trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
            if (contentNode.isObject() || contentNode.isArray()) {
                return contentNode.toString();
            }
        }

        // qwen3.x on Ollama may place the useful output in `thinking`
        // while `response` is empty.
        JsonNode thinkingNode = root.path("thinking");
        if (!thinkingNode.isMissingNode() && !thinkingNode.isNull()) {
            if (thinkingNode.isTextual()) {
                String value = thinkingNode.asText("").trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
            if (thinkingNode.isObject() || thinkingNode.isArray()) {
                return thinkingNode.toString();
            }
        }

        return "";
    }

    private List<MCQDTO.CreateRequest> parseGeneratedQuestions(
            String rawJson, Long stackId, Long topicId, String difficulty) {
        try {
            JsonNode arr;
            try {
                arr = parseQuestionArrayNode(rawJson);
            } catch (Exception primaryParseError) {
                List<MCQDTO.CreateRequest> fallback = parseLooseTextQuestions(rawJson, stackId, topicId, difficulty);
                if (!fallback.isEmpty()) {
                    log.warn("Used loose-text fallback parser for AI response.");
                    return fallback;
                }
                throw primaryParseError;
            }
            List<MCQDTO.CreateRequest> results = new ArrayList<>();

            for (JsonNode node : arr) {
                MCQDTO.CreateRequest req = toCreateRequest(node, stackId, topicId, difficulty);
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
            String rawText, Long stackId, Long topicId, String difficulty) {

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
        Pattern answerLine = Pattern.compile("^\\s*(?:correct\\s*answer|answer)\\s*[:=-]\\s*([A-F1-6])\\b", Pattern.CASE_INSENSITIVE);

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
                    req.setQuestionType("MCQ");
                    req.setOptionA(currentA.trim());
                    req.setOptionB(currentB.trim());
                    req.setOptionC(currentC.trim());
                    req.setOptionD(currentD.trim());
                    req.setCorrectAnswer(extractAnswerLetter(firstNonBlank(currentAnswer, "A")));
                    req.setSendForReview(false);
                    results.add(req);
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
            req.setQuestionType("MCQ");
            req.setOptionA(currentA.trim());
            req.setOptionB(currentB.trim());
            req.setOptionC(currentC.trim());
            req.setOptionD(currentD.trim());
            req.setCorrectAnswer(extractAnswerLetter(firstNonBlank(currentAnswer, "A")));
            req.setSendForReview(false);
            results.add(req);
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
        req.setQuestionType("MCQ");
        req.setOptionA(optionA.trim());
        req.setOptionB(optionB.trim());
        req.setOptionC(optionC.trim());
        req.setOptionD(optionD.trim());
        req.setCorrectAnswer(extractAnswerLetter(rawAnswer));
        req.setSendForReview(false);
        results.add(req);

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

    private MCQDTO.CreateRequest toCreateRequest(JsonNode node, Long stackId, Long topicId, String difficulty) {
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

        MCQDTO.CreateRequest req = new MCQDTO.CreateRequest();
        req.setQuestionStem(stem.trim());
        req.setStackId(stackId);
        req.setTopicId(topicId);
        req.setDifficulty(difficulty);
        req.setQuestionType("MCQ");
        req.setOptionA(optionA.trim());
        req.setOptionB(optionB.trim());
        req.setOptionC(optionC.trim());
        req.setOptionD(optionD.trim());
        req.setCorrectAnswer(extractAnswerLetter(rawAnswer));
        req.setSendForReview(false);
        return req;
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

    private String safeSnippet(String text) {
        if (text == null) {
            return "<null>";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 400 ? normalized.substring(0, 400) + "..." : normalized;
    }

    // ── SIMILARITY CHECK ──────────────────────────────────────────

    public SimilarityCheckResponse checkSimilarity(
            String questionStem, String optionA, String optionB,
            String optionC, String optionD, String optionE, String optionF,
            Long stackId, Long topicId, Long excludeQuestionId) {

        // Fetch all existing questions in same stack + topic
        // Fetch only non-DRAFT questions in same stack + topic
        // DRAFT questions are excluded so AI-generated drafts don't flag each other
        List<MCQ> existing = mcqRepository.findNonDraftByStackAndTopic(
    stackId, topicId, excludeQuestionId,
            List.of(MCQ.Status.READY_FOR_REVIEW, MCQ.Status.UNDER_REVIEW, MCQ.Status.APPROVED, MCQ.Status.REJECTED)
);

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
        String rawResponse = callOllamaAPI(prompt);
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
            - Similarity > 30%  → FLAG the pair
            - Similarity <= 30% → DO NOT include the pair in the output

            SCORING GUIDANCE:
            - 0-15   → Clearly different concepts
            - 16-30  → Related topic but materially different concept/reasoning
            - 31-50  → Noticeable overlap in concept or reasoning
            - 51-70  → Strong overlap; likely redundant
            - 71-100 → Near-duplicate, paraphrase, or same concept with same answer logic

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