package com.smartquiz.repository;

import com.smartquiz.BaseTest;
import com.smartquiz.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MCQ Repository Tests")
public class MCQRepositoryTest extends BaseTest {

    private User sme, reviewer;
    private TechnologyStack stack;
    private Topic topic;

    @BeforeEach
    void setUp() {
        sme      = createSME("repo.sme",      "Repo SME");
        reviewer = createSME("repo.reviewer", "Repo Reviewer");
        stack    = createStack("Core Java");
        topic    = createTopic("Collections", stack);
    }

    @Test
    @DisplayName("findByCreatorEnterpriseId returns only that user's questions")
    void findByCreator_returnsOwned() {
        createMCQ(sme,      stack, topic, MCQ.Status.DRAFT);
        createMCQ(sme,      stack, topic, MCQ.Status.APPROVED);
        createMCQ(reviewer, stack, topic, MCQ.Status.DRAFT);

        Page<MCQ> result = mcqRepository.findByCreatorEnterpriseId(
                sme.getEnterpriseId(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        result.getContent().forEach(m ->
            assertThat(m.getCreator().getEnterpriseId()).isEqualTo(sme.getEnterpriseId()));
    }

    @Test
    @DisplayName("findByReviewerEnterpriseIdAndStatus returns only UNDER_REVIEW for that reviewer")
    void findByReviewerAndStatus_returnsCorrect() {
        MCQ m1 = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        m1.setReviewer(reviewer); mcqRepository.save(m1);

        MCQ m2 = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        m2.setReviewer(reviewer); mcqRepository.save(m2);

        Page<MCQ> result = mcqRepository.findByReviewerEnterpriseIdAndStatus(
                reviewer.getEnterpriseId(), MCQ.Status.UNDER_REVIEW, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(MCQ.Status.UNDER_REVIEW);
    }

    @Test
    @DisplayName("findByCreatorWithFilters - filter by difficulty")
    void findByCreatorWithFilters_difficulty() {
        createMCQ(sme, stack, topic, MCQ.Status.DRAFT); // Easy
        MCQ hard = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        hard.setDifficulty(MCQ.Difficulty.Hard);
        mcqRepository.save(hard);

        Page<MCQ> result = mcqRepository.findByCreatorWithFilters(
                sme.getEnterpriseId(), null, null, MCQ.Difficulty.Hard, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDifficulty()).isEqualTo(MCQ.Difficulty.Hard);
    }

    @Test
    @DisplayName("findByCreatorWithFilters - filter by status")
    void findByCreatorWithFilters_status() {
        createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        createMCQ(sme, stack, topic, MCQ.Status.APPROVED);

        Page<MCQ> result = mcqRepository.findByCreatorWithFilters(
                sme.getEnterpriseId(), null, null, null, MCQ.Status.APPROVED, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByCreatorWithFilters - filter by stackId")
    void findByCreatorWithFilters_stack() {
        TechnologyStack stack2 = createStack("Spring Boot");
        Topic topic2 = createTopic("Auto Config", stack2);

        createMCQ(sme, stack,  topic,  MCQ.Status.DRAFT);
        createMCQ(sme, stack2, topic2, MCQ.Status.DRAFT);

        Page<MCQ> result = mcqRepository.findByCreatorWithFilters(
                sme.getEnterpriseId(), stack.getStackId(), null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getStack().getStackId()).isEqualTo(stack.getStackId());
    }

    @Test
    @DisplayName("findAllWithFilters - search by question stem")
    void findAllWithFilters_searchByStem() {
        MCQ m1 = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        m1.setQuestionStem("What is polymorphism?"); mcqRepository.save(m1);
        MCQ m2 = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        m2.setQuestionStem("What is a HashMap?"); mcqRepository.save(m2);

        Page<MCQ> result = mcqRepository.findAllWithFilters(
                null, null, null, null, "polymorphism", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getQuestionStem()).contains("polymorphism");
    }

    @Test
    @DisplayName("findAllWithFilters - no filters returns all")
    void findAllWithFilters_noFilters() {
        createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        createMCQ(reviewer, stack, topic, MCQ.Status.REJECTED);

        Page<MCQ> result = mcqRepository.findAllWithFilters(
                null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Pagination splits results correctly across pages")
    void pagination_worksCorrectly() {
        for (int i = 0; i < 12; i++) createMCQ(sme, stack, topic, MCQ.Status.DRAFT);

        Page<MCQ> p1 = mcqRepository.findByCreatorEnterpriseId(sme.getEnterpriseId(), PageRequest.of(0, 5));
        Page<MCQ> p2 = mcqRepository.findByCreatorEnterpriseId(sme.getEnterpriseId(), PageRequest.of(1, 5));
        Page<MCQ> p3 = mcqRepository.findByCreatorEnterpriseId(sme.getEnterpriseId(), PageRequest.of(2, 5));

        assertThat(p1.getContent()).hasSize(5);
        assertThat(p2.getContent()).hasSize(5);
        assertThat(p3.getContent()).hasSize(2);
        assertThat(p1.getTotalElements()).isEqualTo(12);
        assertThat(p1.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("findUsersByStackIdExcluding excludes the specified user")
    void findUsersByStackIdExcluding_excludesUser() {
        UserStackMapping m1 = new UserStackMapping();
        m1.setUser(reviewer); m1.setStack(stack);
        userStackMappingRepository.save(m1);

        UserStackMapping m2 = new UserStackMapping();
        m2.setUser(sme); m2.setStack(stack);
        userStackMappingRepository.save(m2);

        List<User> result = userStackMappingRepository.findUsersByStackIdExcluding(
                stack.getStackId(), sme.getEnterpriseId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEnterpriseId()).isEqualTo(reviewer.getEnterpriseId());
    }
}