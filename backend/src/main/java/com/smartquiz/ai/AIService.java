package com.smartquiz.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import java.time.Duration;
import reactor.core.publisher.Mono;

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
            SimilarityCheckResponse check = checkSimilarity(
                    req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                    req.getOptionC(), req.getOptionD(), null, null,
                    stack.getStackId(), topic.getTopicId(), null);

            if (!check.isHasSimilar()) {
                screened.add(req);
            } else {
                log.info("Generated question screened out due to similarity ≥30%. Requesting replacement.");
            }
        }

        // If we don't have enough after screening, generate more
        if (screened.size() < count && attempts < maxAttempts) {
            int stillNeeded = count - screened.size();
            if (stillNeeded > 0) {
                String retryPrompt = buildGenerationPrompt(
                        stack.getStackName(), topic.getTopicName(), difficulty, stillNeeded);
                String retryResponse = callOllamaAPI(retryPrompt);
                List<MCQDTO.CreateRequest> retryGenerated = parseGeneratedQuestions(
                        retryResponse, stack.getStackId(), topic.getTopicId(), difficulty);

                for (MCQDTO.CreateRequest req : retryGenerated) {
                    if (screened.size() >= count) break;
                    SimilarityCheckResponse check = checkSimilarity(
                            req.getQuestionStem(), req.getOptionA(), req.getOptionB(),
                            req.getOptionC(), req.getOptionD(), null, null,
                            stack.getStackId(), topic.getTopicId(), null);
                    if (!check.isHasSimilar()) {
                        screened.add(req);
                    }
                }
            }
        }

        return screened;
    }

    private String buildGenerationPrompt(String stackName, String topicName,
                                     String difficulty, int count) {
        return String.format("""
            You are an expert technical trainer creating HIGH-QUALITY and NON-OVERLAPPING MCQ questions.

            Generate EXACTLY %d unique multiple-choice questions.

            Context:
            - Technology Stack: %s
            - Topic: %s
            - Difficulty: %s

            STRICT REQUIREMENTS (MANDATORY):
            1. Each question MUST test a DISTINCT concept, subtopic, or use case.
            2. DO NOT generate variations, paraphrases, or rewordings of the same idea.
            3. DO NOT repeat patterns in question framing.
            4. Ensure clear conceptual separation between all questions.
            5. Cover different angles such as:
            - Definitions
            - Real-world scenarios
            - Code behavior
            - Edge cases
            - Performance considerations
            - Best practices

            INTERNAL SELF-CHECK (VERY IMPORTANT):
            - Before finalizing, compare all generated questions with each other.
            - If ANY two questions are similar in concept, wording, or intent → REWRITE them.
            - Ensure similarity between any pair of questions is LESS THAN 20%%.

            OUTPUT REQUIREMENTS:
            - Each question must have exactly 4 options (A, B, C, D)
            - Only ONE correct answer
            - Options must be plausible and non-overlapping
            - Avoid vague or generic questions

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
            // Strip any markdown code fences if present
            String json = rawJson.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("```\\s*$", "")
                    .trim();

            JsonNode arr = objectMapper.readTree(json);
            List<MCQDTO.CreateRequest> results = new ArrayList<>();

            for (JsonNode node : arr) {
                MCQDTO.CreateRequest req = new MCQDTO.CreateRequest();
                req.setQuestionStem(node.path("questionStem").asText());
                req.setStackId(stackId);
                req.setTopicId(topicId);
                req.setDifficulty(difficulty);
                req.setQuestionType("MCQ");
                req.setOptionA(node.path("optionA").asText());
                req.setOptionB(node.path("optionB").asText());
                req.setOptionC(node.path("optionC").asText());
                req.setOptionD(node.path("optionD").asText());
                req.setCorrectAnswer(node.path("correctAnswer").asText("A"));
                req.setSendForReview(false);
                results.add(req);
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse AI-generated questions. Please try again.");
        }
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
            
        You are a STRICT duplicate detector for quiz questions.

                Your job is to identify ONLY TRUE DUPLICATES.

                IMPORTANT RULES:
                - DO NOT mark questions as similar just because they belong to the same topic.
                - DO NOT mark questions as similar if they test different concepts, even if wording overlaps.
                - Focus on CONCEPT being tested, not just wording.

                DUPLICATE CRITERIA:
                - 90-100 → Same question OR exact paraphrase OR identical concept + same answer logic
                - 70-89 → Same concept being tested with very similar structure/options
                - BELOW 70 → NOT a duplicate (ignore these)

                CRITICAL INSTRUCTION:
                - ONLY return questions with similarity >= 70
                - Ignore all moderate similarities (below 70)
                - Be VERY conservative — false positives are NOT acceptable

                NEW QUESTION:

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
            
            Respond ONLY with a valid JSON array listing only questions with similarity >= 1%.
            No preamble, no markdown, just the JSON array.
            Format:
            [{"id": 123, "similarity": 45}, {"id": 456, "similarity": 32}]
            
            If no questions have similarity >= 1%, respond with: []
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