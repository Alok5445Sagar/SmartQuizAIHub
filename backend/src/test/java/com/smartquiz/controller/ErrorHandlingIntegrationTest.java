package com.smartquiz.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.security.JwtUtils;

@AutoConfigureMockMvc
@DisplayName("Error handling (RFC 7807 problem responses)")
class ErrorHandlingIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtils jwtUtils;

    private User sme;
    private User otherSme;
    private TechnologyStack stack;
    private Topic topic;
    private String smeToken;

    @BeforeEach
    void setUp() {
        sme = createSME("err.sme", "Err SME");
        otherSme = createSME("err.other", "Err Other");
        stack = createStack("Error Stack");
        topic = createTopic("Error Topic", stack);
        smeToken = "Bearer " + jwtUtils.generateToken(sme.getEnterpriseId(), "SME");
    }

    private MCQDTO.CreateRequest validCreate(String stem, boolean aiGenerated) {
        return new MCQDTO.CreateRequest(stem, "MCQ", stack.getStackId(), topic.getTopicId(), "Easy",
                "A", "B", "C", "D", null, null, "A", false, aiGenerated);
    }

    @Test
    @DisplayName("unknown question returns 404 problem")
    void unknownQuestion_returns404() throws Exception {
        mockMvc.perform(get("/api/mcq/999999").header("Authorization", smeToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Question not found"))
                .andExpect(jsonPath("$.message").value("Question not found"))
                .andExpect(jsonPath("$.instance").value("/api/mcq/999999"));
    }

    @Test
    @DisplayName("editing another SME's question returns 403 problem")
    void editOthersQuestion_returns403() throws Exception {
        MCQ others = createMCQ(otherSme, stack, topic, MCQ.Status.DRAFT);
        MCQDTO.UpdateRequest update = new MCQDTO.UpdateRequest("Changed?", "MCQ", stack.getStackId(), topic.getTopicId(),
                "Easy", "A", "B", "C", "D", null, null, "A", false);

        mockMvc.perform(put("/api/mcq/" + others.getQuestionId())
                        .header("Authorization", smeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Not authorized to edit this MCQ"));
    }

    @Test
    @DisplayName("duplicate AI-generated stem returns 409 problem")
    void duplicateAiQuestion_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(validCreate("Duplicate stem for conflict?", true));
        mockMvc.perform(post("/api/mcq").header("Authorization", smeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mcq").header("Authorization", smeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Duplicate question")));
    }

    @Test
    @DisplayName("invalid body returns 400 with per-field errors")
    void invalidBody_returns400WithFieldErrors() throws Exception {
        MCQDTO.CreateRequest invalid = validCreate(" ", false);
        invalid.setDifficulty("Impossible");
        invalid.setOptionA("x".repeat(300));

        mockMvc.perform(post("/api/mcq").header("Authorization", smeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.questionStem").value("Question stem is required"))
                .andExpect(jsonPath("$.errors.difficulty").value("Difficulty must be Easy, Medium or Hard"))
                .andExpect(jsonPath("$.errors.optionA").value("Option A cannot exceed 255 characters"));
    }

    @Test
    @DisplayName("business rule violation returns 400 problem")
    void businessRule_returns400() throws Exception {
        MCQDTO.CreateRequest msqWithOneAnswer = validCreate("MSQ with one answer?", false);
        msqWithOneAnswer.setQuestionType("MSQ");

        mockMvc.perform(post("/api/mcq").header("Authorization", smeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msqWithOneAnswer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("MSQ requires at least 2 correct answers."));
    }

    @Test
    @DisplayName("page size above 100 is rejected")
    void pageSizeAboveLimit_returns400() throws Exception {
        mockMvc.perform(get("/api/mcq/my-questions?size=500").header("Authorization", smeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("malformed JSON returns 400 problem with a message")
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/mcq").header("Authorization", smeToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("wrong password returns 401 problem")
    void wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":\"err.sme\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }
}
