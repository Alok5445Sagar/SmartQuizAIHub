package com.smartquiz.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartquiz.BaseTest;
import com.smartquiz.dto.SimilarityCheckResponse;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;

@DisplayName("AI Service Similarity Tests")
class AIServiceTest extends BaseTest {

    @Autowired
    private AIService aiService;

    private User sme;
    private TechnologyStack stack;
    private Topic topic;

    @BeforeEach
    void setUp() {
        sme = createSME("ai.sme", "AI SME");
        stack = createStack("AI Test Stack 101");
        topic = createTopic("AI Test Topic 101", stack);
    }

    @Test
    @DisplayName("deterministic similarity engine detects exact duplicate")
    void deterministicSimilarity_detectsExactDuplicate() {
        MCQ existing = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        existing.setQuestionStem("What is dependency injection in Spring Boot?");
        existing.setOptionA("A design pattern");
        existing.setOptionB("A database");
        existing.setOptionC("A web server");
        existing.setOptionD("A logging framework");
        mcqRepository.save(existing);

        SimilarityCheckResponse response = ReflectionTestUtils.invokeMethod(
            aiService,
            "runDeterministicSimilarityCheck",
                "What is dependency injection in Spring Boot?",
                "A design pattern",
                "A database",
                "A web server",
                "A logging framework",
                null,
                null,
            List.of(existing));

        assertThat(response.isHasSimilar()).isTrue();
        assertThat(response.getSimilarQuestions())
                .extracting(SimilarityCheckResponse.SimilarQuestion::getQuestionId)
                .contains(existing.getQuestionId());
    }

    @Test
    @DisplayName("deterministic similarity engine detects near-duplicate")
    void deterministicSimilarity_detectsNearDuplicate() {
        MCQ existing = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        existing.setQuestionStem("Explain dependency injection in Spring framework.");
        existing.setOptionA("It injects dependencies automatically");
        existing.setOptionB("It compiles Java code");
        existing.setOptionC("It creates SQL tables");
        existing.setOptionD("It manages DNS");
        mcqRepository.save(existing);

        SimilarityCheckResponse response = ReflectionTestUtils.invokeMethod(
            aiService,
            "runDeterministicSimilarityCheck",
                "What is dependency injection in Spring?",
                "Dependencies are injected by container",
                "It is a database feature",
                "It configures network switches",
                "It schedules cron jobs",
                null,
                null,
            List.of(existing));

        assertThat(response.isHasSimilar()).isTrue();
        assertThat(response.getSimilarQuestions()).isNotEmpty();
        assertThat(response.getSimilarQuestions().get(0).getSimilarityPercent()).isGreaterThanOrEqualTo(30);
    }
}
