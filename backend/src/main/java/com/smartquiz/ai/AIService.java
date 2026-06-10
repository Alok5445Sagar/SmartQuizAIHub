package com.smartquiz.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // @Value("${app.ai.api-key}")
    // private String apiKey;

    @Value("${app.ai.api-url}")
    private String apiUrl;

    @Value("${app.ai.model}")
    private String model;

    @Value("${app.ai.similarity-threshold:0.70}")
    private double similarityThreshold;

    @Autowired
    private MCQRepository mcqRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private WebClient webClient;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    // ── AI QUESTION GENERATION ────────────────────────────────────

    public List<MCQDTO.CreateRequest> generateQuestions(
            TechnologyStack stack, Topic topic, String difficulty, int count,
            Long creatorStackId, Long creatorTopicId) {

        String prompt = buildGenerationPrompt(stack.getStackName(), topic.getTopicName(), difficulty, count);
        String rawResponse = callOllamaAPI(prompt);

        List<MCQDTO.CreateRequest> generated = parseGeneratedQuestions(
                rawResponse, stack.getStackId(), topic.getTopicId(), difficulty);

        // Screen each generated question for similarity
        List<MCQDTO.CreateRequest> screened = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = count * 3; // allow up to 3x retries

        for (MCQDTO.CreateRequest req : generated) {
            SimilarityCheckResponse check = checkSimilarityForGeneration(
                    req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                    req.getOptionC(), req.getOptionD(), null, null,
                stack.getStackId(), topic.getTopicId());

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
                String retryPrompt = buildGenerationPrompt(
                        stack.getStackName(), topic.getTopicName(), difficulty, stillNeeded);
                String retryResponse = callOllamaAPI(retryPrompt);
                List<MCQDTO.CreateRequest> retryGenerated = parseGeneratedQuestions(
                        retryResponse, stack.getStackId(), topic.getTopicId(), difficulty);

                for (MCQDTO.CreateRequest req : retryGenerated) {
                    if (screened.size() >= count) break;
                    SimilarityCheckResponse check = checkSimilarityForGeneration(
                            req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                            req.getOptionC(), req.getOptionD(), null, null,
                            stack.getStackId(), topic.getTopicId());
                    if (!check.isHasSimilar() && !hasSimilarInBatch(req, screened)) {
                        screened.add(req);
                    } else {
                        log.info("Retry-generated question screened out due to similarity with existing or in-batch questions.");
                    }
                }
            }
        }

        return screened;
    }

    private boolean hasSimilarInBatch(MCQDTO.CreateRequest candidateReq, List<MCQDTO.CreateRequest> acceptedSoFar) {
        if (acceptedSoFar == null || acceptedSoFar.isEmpty()) {
            return false;
        }

        if (hasExactStemDuplicateInBatch(candidateReq, acceptedSoFar)) {
            return true;
        }

        try {
            String candidate = buildQuestionText(
                    candidateReq.getQuestionStem(),
                    candidateReq.getOptionA(), candidateReq.getOptionB(),
                    candidateReq.getOptionC(), candidateReq.getOptionD(),
                    candidateReq.getOptionE(), candidateReq.getOptionF());

            List<MCQ> transientExisting = toTransientMcqs(acceptedSoFar);
            String prompt = buildSimilarityPrompt(candidate, transientExisting);
            String rawResponse = callOllamaAPI(prompt);

            SimilarityCheckResponse check = parseSimilarityResponse(rawResponse, transientExisting);
            return check.isHasSimilar();
        } catch (Exception ex) {
            log.warn("In-batch similarity check failed; falling back to exact stem check only. Reason: {}", ex.getMessage());
            return false;
        }
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
            Long stackId, Long topicId) {

        Collection<MCQ.Status> generationStatuses = List.of(
                MCQ.Status.DRAFT,
                MCQ.Status.READY_FOR_REVIEW,
                MCQ.Status.UNDER_REVIEW,
                MCQ.Status.APPROVED);

        List<MCQ> existing = mcqRepository.findNonDraftByStackAndTopic(
                stackId, topicId, null, generationStatuses);

        if (existing.isEmpty()) {
            return new SimilarityCheckResponse(false, Collections.emptyList());
        }

        String candidate = buildQuestionText(questionStem, optionA, optionB, optionC, optionD, optionE, optionF);
        String prompt = buildSimilarityPrompt(candidate, existing);
        String rawResponse = callOllamaAPI(prompt);
        return parseSimilarityResponse(rawResponse, existing);
    }

    private String buildGenerationPrompt(String stackName, String topicName,
                                     String difficulty, int count) {
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
            """, count, stackName, topicName, difficulty);
    }

    private String callOllamaAPI(String prompt) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            requestBody.put("format", "json");

            String response = webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(120))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("response").asText();
        } catch (Exception e) {
            log.error("Ollama API call failed: {}", e.getMessage());
            throw new RuntimeException("AI service unavailable. Make sure Ollama is running locally.");
        }
    }

    private List<MCQDTO.CreateRequest> parseGeneratedQuestions(
            String rawJson, Long stackId, Long topicId, String difficulty) {
        try {
            JsonNode arr = parseQuestionArrayNode(rawJson);
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

    private JsonNode parseQuestionArrayNode(String rawJson) throws Exception {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("AI response is empty");
        }

        String cleaned = stripCodeFences(rawJson);

        // Try direct parse first.
        try {
            JsonNode node = objectMapper.readTree(cleaned);
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
            JsonNode node = objectMapper.readTree(arrayFragment);
            if (node.isArray()) {
                return node;
            }
        }

        // Try extracting an object that contains a questions array.
        String objectFragment = extractBalancedJson(cleaned, '{', '}');
        if (objectFragment != null) {
            JsonNode node = objectMapper.readTree(objectFragment);
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
                return resolveArrayNode(objectMapper.readTree(value));
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
                    JsonNode parsed = objectMapper.readTree(stripCodeFences(candidate.asText()));
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
                    return resolveArrayNode(objectMapper.readTree(stripCodeFences(content)));
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

        JsonNode options = node.path("options");
        if ((isBlank(optionA) || isBlank(optionB) || isBlank(optionC) || isBlank(optionD))
                && options.isArray() && options.size() >= 4) {
            optionA = firstNonBlank(optionA, options.get(0).asText(""));
            optionB = firstNonBlank(optionB, options.get(1).asText(""));
            optionC = firstNonBlank(optionC, options.get(2).asText(""));
            optionD = firstNonBlank(optionD, options.get(3).asText(""));
        }

        if (isBlank(stem) || isBlank(optionA) || isBlank(optionB) || isBlank(optionC) || isBlank(optionD)) {
            return null;
        }

        String rawAnswer = firstNonBlank(
                node.path("correctAnswer").asText(null),
                node.path("answer").asText(null),
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
    List.of(MCQ.Status.APPROVED, MCQ.Status.READY_FOR_REVIEW)
);

        if (existing.isEmpty()) {
            return new SimilarityCheckResponse(false, Collections.emptyList());
        }

        // Build candidate text
        String candidate = buildQuestionText(questionStem, optionA, optionB, optionC, optionD, optionE, optionF);

        // Build prompt for similarity scoring
        String prompt = buildSimilarityPrompt(candidate, existing);
        String rawResponse = callOllamaAPI(prompt);

        return parseSimilarityResponse(rawResponse, existing);
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
            
            Respond ONLY with a valid JSON array listing only questions with similarity >= 70%.
            No preamble, no markdown, just the JSON array.
            Format:
            [{"id": 123, "similarity": 82}, {"id": 456, "similarity": 74}]
            
            If no questions have similarity >= 70%, respond with: []
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

            JsonNode arr = objectMapper.readTree(json);
            Map<Long, MCQ> existingMap = existing.stream()
                    .collect(Collectors.toMap(MCQ::getQuestionId, m -> m));

            List<SimilarityCheckResponse.SimilarQuestion> similarList = new ArrayList<>();
            boolean hasSimilar = false;

            for (JsonNode node : arr) {
                long id = node.path("id").asLong();
                int similarity = node.path("similarity").asInt();

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
}