package com.smartquiz.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

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

    private String getToken(String id, String pass) throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthDTO.LoginRequest(id, pass))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }
}
