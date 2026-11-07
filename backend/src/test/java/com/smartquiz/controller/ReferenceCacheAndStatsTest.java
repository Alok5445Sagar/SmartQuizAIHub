package com.smartquiz.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.smartquiz.BaseTest;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.security.JwtUtils;

import jakarta.persistence.EntityManagerFactory;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.cache.type=caffeine",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@DisplayName("Reference data cache and My Questions stats")
class ReferenceCacheAndStatsTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("stacks and topics are served from the cache after the first request")
    void referenceDataIsCached() throws Exception {
        User sme = createSME("cache.sme", "Cache SME");
        String token = "Bearer " + jwtUtils.generateToken(sme.getEnterpriseId(), "SME");
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        mockMvc.perform(get("/api/reference/stacks").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stackId").exists())
                .andExpect(jsonPath("$[0].stackName").exists());
        Long stackId = stackRepository.findAll().get(0).getStackId();
        mockMvc.perform(get("/api/reference/topics/" + stackId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topicId").exists())
                .andExpect(jsonPath("$[0].topicName").exists());

        stats.clear();
        mockMvc.perform(get("/api/reference/stacks").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/reference/topics/" + stackId).header("Authorization", token)).andExpect(status().isOk());
        // Only the JWT filter's user lookup may hit the database.
        long referenceQueries = stats.getPrepareStatementCount() - 2;
        assertThat(referenceQueries).isZero();
    }

    @Test
    @DisplayName("My Questions stats count every status across all pages, honouring filters")
    void myQuestionStats() throws Exception {
        User sme = createSME("stats.sme", "Stats SME");
        User other = createSME("stats.other", "Other SME");
        TechnologyStack stack = createStack("Stats Stack");
        Topic topic = createTopic("Stats Topic", stack);
        Topic otherTopic = createTopic("Stats Other Topic", stack);
        for (int i = 0; i < 12; i++) {
            createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        }
        createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        createMCQ(sme, stack, otherTopic, MCQ.Status.REJECTED);
        createMCQ(other, stack, topic, MCQ.Status.DRAFT);
        String token = "Bearer " + jwtUtils.generateToken(sme.getEnterpriseId(), "SME");

        mockMvc.perform(get("/api/mcq/my-questions/stats").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(14))
                .andExpect(jsonPath("$.DRAFT").value(12))
                .andExpect(jsonPath("$.APPROVED").value(1))
                .andExpect(jsonPath("$.REJECTED").value(1))
                .andExpect(jsonPath("$.UNDER_REVIEW").value(0));

        mockMvc.perform(get("/api/mcq/my-questions/stats").param("topicId", topic.getTopicId().toString())
                        .header("Authorization", token))
                .andExpect(jsonPath("$.total").value(13))
                .andExpect(jsonPath("$.REJECTED").value(0));
    }
}
