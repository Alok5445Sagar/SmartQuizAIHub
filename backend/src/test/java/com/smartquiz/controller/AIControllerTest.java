package com.smartquiz.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.ai.AIService;
import com.smartquiz.dto.AuthDTO;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.dto.SimilarityCheckResponse;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;

@AutoConfigureMockMvc
@DisplayName("AI Controller Tests")
public class AIControllerTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AIService aiService;

    private User sme;
    private TechnologyStack stack;
    private Topic topic;
    private String smeToken;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(aiService);

        sme = createSME("ai.ctrl.sme", "AI Ctrl SME");
        stack = createStack("AI Controller Stack");
        topic = createTopic("AI Controller Topic", stack);
        smeToken = getToken("ai.ctrl.sme", "password123");

        Mockito.when(aiService.generateQuestions(
                Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.anyString(), Mockito.anyList()))
            .thenAnswer(invocation -> {
                int count = invocation.getArgument(3, Integer.class);
                String questionType = invocation.getArgument(6, String.class);
                return buildGeneratedRequests(count, questionType, stack.getStackId(), topic.getTopicId());
            });
    }

    @Test
    @DisplayName("POST /api/ai/generate supports mixed MCQ and MSQ generation")
    void generateQuestions_mixedTypes_succeeds() throws Exception {
        Map<String, Object> req = Map.of(
                "stackId", stack.getStackId(),
                "topicId", topic.getTopicId(),
                "difficulty", "Medium",
                "numberOfQuestions", 5,
                "generateMCQ", true,
                "generateMSQ", true);

        mockMvc.perform(post("/api/ai/generate")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(5))
                .andExpect(jsonPath("$.generated").value(5))
                .andExpect(jsonPath("$.requestedByType.MCQ").value(3))
                .andExpect(jsonPath("$.requestedByType.MSQ").value(2))
                .andExpect(jsonPath("$.selectedTypes", hasSize(2)))
                .andExpect(jsonPath("$.questions", hasSize(5)))
                .andExpect(jsonPath("$.questions[0].questionType").value("MCQ"))
                .andExpect(jsonPath("$.questions[4].questionType").value("MSQ"));
    }

    @Test
    @DisplayName("POST /api/ai/generate rejects request when both MCQ and MSQ are disabled")
    void generateQuestions_withoutAnyType_returnsBadRequest() throws Exception {
        Map<String, Object> req = Map.of(
                "stackId", stack.getStackId(),
                "topicId", topic.getTopicId(),
                "difficulty", "Easy",
                "numberOfQuestions", 3,
                "generateMCQ", false,
                "generateMSQ", false);

        mockMvc.perform(post("/api/ai/generate")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select at least one question type: MCQ and/or MSQ"));
    }

            @Test
            @DisplayName("POST /api/ai/similarity-check returns similarity payload")
            void similarityCheck_returnsSimilarityPayload() throws Exception {
            SimilarityCheckResponse.SimilarQuestion similar = new SimilarityCheckResponse.SimilarQuestion(
                999L,
                47,
                "Existing similar stem",
                "A", "B", "C", "D", null, null,
                "A");

            Mockito.when(aiService.checkSimilarity(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new SimilarityCheckResponse(true, List.of(similar)));

            Map<String, Object> req = Map.of(
                "questionStem", "What is Java?",
                "optionA", "A",
                "optionB", "B",
                "optionC", "C",
                "optionD", "D",
                "optionE", "",
                "optionF", "",
                "stackId", stack.getStackId(),
                "topicId", topic.getTopicId(),
                "excludeQuestionId", 101L
            );

            mockMvc.perform(post("/api/ai/similarity-check")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSimilar").value(true))
                .andExpect(jsonPath("$.similarQuestions", hasSize(1)))
                .andExpect(jsonPath("$.similarQuestions[0].questionId").value(999))
                .andExpect(jsonPath("$.similarQuestions[0].similarityPercent").value(47));
            }

            @Test
            @DisplayName("POST /api/ai/similarity-check falls back when AI service fails")
            void similarityCheck_whenServiceFails_returnsFallback() throws Exception {
            Mockito.when(aiService.checkSimilarity(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new RuntimeException("AI unavailable"));

            Map<String, Object> req = Map.of(
                "questionStem", "What is Java?",
                "optionA", "A",
                "optionB", "B",
                "optionC", "C",
                "optionD", "D",
                "optionE", "",
                "optionF", "",
                "stackId", stack.getStackId(),
                "topicId", topic.getTopicId(),
                "excludeQuestionId", 101L
            );

            mockMvc.perform(post("/api/ai/similarity-check")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSimilar").value(false))
                .andExpect(jsonPath("$.similarQuestions", empty()));
            }

    private List<MCQDTO.CreateRequest> buildGeneratedRequests(int count, String questionType, Long stackId, Long topicId) {
        List<MCQDTO.CreateRequest> out = new ArrayList<>();
        boolean isMsq = "MSQ".equalsIgnoreCase(questionType);

        for (int i = 0; i < count; i++) {
            String stem = "AI " + questionType + " stem " + i + " " + UUID.randomUUID();
            MCQDTO.CreateRequest req = new MCQDTO.CreateRequest();
            req.setQuestionStem(stem);
            req.setQuestionType(isMsq ? "MSQ" : "MCQ");
            req.setStackId(stackId);
            req.setTopicId(topicId);
            req.setDifficulty("Medium");
            req.setOptionA("Option A " + i);
            req.setOptionB("Option B " + i);
            req.setOptionC("Option C " + i);
            req.setOptionD("Option D " + i);
            req.setOptionE(isMsq ? "Option E " + i : null);
            req.setOptionF(null);
            req.setCorrectAnswer(isMsq ? "A,C" : "A");
            req.setAiGenerated(true);
            req.setSendForReview(false);
            out.add(req);
        }

        return out;
    }

    private String getToken(String id, String pass) throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthDTO.LoginRequest(id, pass))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }
}
