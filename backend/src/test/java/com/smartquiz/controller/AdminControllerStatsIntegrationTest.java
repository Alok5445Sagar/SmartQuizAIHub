package com.smartquiz.controller;

import com.smartquiz.BaseTest;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Admin Question Bank Stats Integration Tests")
class AdminControllerStatsIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin.stats.api", roles = "ADMIN")
    @DisplayName("Question bank stats endpoint returns global status counts")
    void questionBankStats_shouldReturnCounts() throws Exception {
        User admin = createAdmin("admin.stats.api");
        TechnologyStack stack = createStack("Stats Stack");
        Topic topic = createTopic("Stats Topic", stack);

        createQuestion(admin, stack, topic, MCQ.Status.READY_FOR_REVIEW);
        createQuestion(admin, stack, topic, MCQ.Status.UNDER_REVIEW);
        createQuestion(admin, stack, topic, MCQ.Status.APPROVED);

        mockMvc.perform(get("/api/admin/question-bank/stats")
                        .param("stackId", String.valueOf(stack.getStackId()))
                        .param("topicId", String.valueOf(topic.getTopicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.readyForReview").isNumber())
                .andExpect(jsonPath("$.underReview").isNumber())
                .andExpect(jsonPath("$.approved").isNumber())
                .andExpect(jsonPath("$.readyForReview").value(1))
                .andExpect(jsonPath("$.underReview").value(1))
                .andExpect(jsonPath("$.approved").value(1));
    }

    private void createQuestion(User creator, TechnologyStack stack, Topic topic, MCQ.Status status) {
        MCQ mcq = createMCQ(creator, stack, topic, status);
        mcq.setStatus(status);
        mcqRepository.save(mcq);
    }
}
