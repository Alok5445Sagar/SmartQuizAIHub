package com.smartquiz.repository;

import com.smartquiz.BaseTest;
import com.smartquiz.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Collection;

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
        stack    = createStack("Test Stack Unique 99");
        topic    = createTopic("Test Topic Unique 99", stack);
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
        TechnologyStack stack2 = createStack("Another Unique Stack 88");
        Topic topic2 = createTopic("Another Unique Topic 88", stack2);

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

    // ── DUPLICATE DETECTION ─────────────────────────────────

@Test
    @DisplayName("existsByQuestionStemAndStackAndTopicNonDraft returns true for APPROVED question")
    void existsByQuestionStemNonDraft_returnsTrueForApproved() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        mcq.setQuestionStem("Unique approved question?");
        mcqRepository.save(mcq);

        boolean exists = mcqRepository.existsByQuestionStemAndStackAndTopicNonDraft(
                "Unique approved question?", stack.getStackId(), topic.getTopicId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByQuestionStemAndStackAndTopicNonDraft returns false for DRAFT question")
    void existsByQuestionStemNonDraft_returnsFalseForDraft() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        mcq.setQuestionStem("Unique draft question?");
        mcqRepository.save(mcq);

        // DRAFT should NOT be flagged as duplicate
        boolean exists = mcqRepository.existsByQuestionStemAndStackAndTopicNonDraft(
                "Unique draft question?", stack.getStackId(), topic.getTopicId());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByQuestionStemAndStackAndTopicNonDraft returns false for different topic")
    void existsByQuestionStemNonDraft_differentTopic_returnsFalse() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        mcq.setQuestionStem("Unique approved question?");
        mcqRepository.save(mcq);

        Topic differentTopic = createTopic("Different Topic For Test", stack);

        boolean exists = mcqRepository.existsByQuestionStemAndStackAndTopicNonDraft(
                "Unique approved question?", stack.getStackId(), differentTopic.getTopicId());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("existsByQuestionStemAndStackAndTopicNonDraft is case insensitive")
    void existsByQuestionStemNonDraft_caseInsensitive() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);
        mcq.setQuestionStem("What is Java?");
        mcqRepository.save(mcq);

        boolean exists = mcqRepository.existsByQuestionStemAndStackAndTopicNonDraft(
                "WHAT IS JAVA?", stack.getStackId(), topic.getTopicId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("findNonDraftByStackAndTopic excludes DRAFT questions")
    void findNonDraftByStackAndTopic_excludesDrafts() {
        MCQ draft = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        MCQ approved = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        MCQ readyForReview = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);

        List<MCQ> result = mcqRepository.findNonDraftByStackAndTopic(
                stack.getStackId(), topic.getTopicId(), null, List.of(MCQ.Status.APPROVED, MCQ.Status.READY_FOR_REVIEW));

        assertThat(result).hasSize(2);
        assertThat(result.stream().noneMatch(m -> m.getStatus() == MCQ.Status.DRAFT)).isTrue();
    }

    @Test
    @DisplayName("findNonDraftByStackAndTopic excludes specified question ID")
    void findNonDraftByStackAndTopic_excludesSpecifiedId() {
        MCQ approved1 = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        MCQ approved2 = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);

        List<MCQ> result = mcqRepository.findNonDraftByStackAndTopic(
                stack.getStackId(), topic.getTopicId(), approved1.getQuestionId(),  List.of(MCQ.Status.APPROVED, MCQ.Status.READY_FOR_REVIEW));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuestionId()).isEqualTo(approved2.getQuestionId());
    }

@Test
@DisplayName("existsByQuestionStemAndStackAndTopicExcluding excludes self")
void existsByQuestionStemExcluding_excludesSelf() {
    MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    mcq.setQuestionStem("Self reference question?");
    mcqRepository.save(mcq);

    // Should return false — excluding self
    boolean exists = mcqRepository.existsByQuestionStemAndStackAndTopicExcluding(
            "Self reference question?",
            stack.getStackId(), topic.getTopicId(),
            mcq.getQuestionId());

    assertThat(exists).isFalse();
}

@Test
@DisplayName("existsByQuestionStemAndStackAndTopicExcluding detects other duplicates")
void existsByQuestionStemExcluding_detectsOtherDuplicate() {
    MCQ mcq1 = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    mcq1.setQuestionStem("Duplicate stem question?");
    mcqRepository.save(mcq1);

    MCQ mcq2 = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    mcq2.setQuestionStem("Different question?");
    mcqRepository.save(mcq2);

    // mcq2 trying to use mcq1's stem — should return true
    boolean exists = mcqRepository.existsByQuestionStemAndStackAndTopicExcluding(
            "Duplicate stem question?",
            stack.getStackId(), topic.getTopicId(),
            mcq2.getQuestionId());

    assertThat(exists).isTrue();
}

// ── MSQ REPOSITORY TESTS ─────────────────────────────────

@Test
@DisplayName("MCQ saved with MSQ type returns correct questionType")
void savedMSQ_returnsCorrectType() {
    MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    mcq.setQuestionType(MCQ.QuestionType.MSQ);
    mcq.setCorrectAnswer("A,C");
    mcq.setOptionE("Option E");
    mcqRepository.save(mcq);

    MCQ found = mcqRepository.findById(mcq.getQuestionId()).orElseThrow();
    assertThat(found.getQuestionType()).isEqualTo(MCQ.QuestionType.MSQ);
    assertThat(found.getCorrectAnswer()).isEqualTo("A,C");
    assertThat(found.getOptionE()).isEqualTo("Option E");
}

@Test
@DisplayName("MSQ with optionF saves and retrieves correctly")
void savedMSQ_withOptionF_retrievesCorrectly() {
    MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    mcq.setQuestionType(MCQ.QuestionType.MSQ);
    mcq.setOptionE("Option E value");
    mcq.setOptionF("Option F value");
    mcq.setCorrectAnswer("A,B,E,F");
    mcqRepository.save(mcq);

    MCQ found = mcqRepository.findById(mcq.getQuestionId()).orElseThrow();
    assertThat(found.getOptionF()).isEqualTo("Option F value");
    assertThat(found.getCorrectAnswer()).isEqualTo("A,B,E,F");
}

@Test
@DisplayName("findByCreatorWithFilters returns both MCQ and MSQ questions")
void findByCreatorWithFilters_returnsBothTypes() {
    MCQ mcqQ = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);

    MCQ msqQ = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    msqQ.setQuestionStem("MSQ specific question?");
    msqQ.setQuestionType(MCQ.QuestionType.MSQ);
    msqQ.setCorrectAnswer("A,B");
    mcqRepository.save(msqQ);

    Page<MCQ> result = mcqRepository.findByCreatorWithFilters(
            sme.getEnterpriseId(), null, null, null, null,
            PageRequest.of(0, 10));

    assertThat(result.getTotalElements()).isEqualTo(2);
}

@Test
@DisplayName("Duplicate check works for MSQ question type")
void duplicateCheck_worksForMSQ() {
    MCQ msq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    msq.setQuestionStem("MSQ duplicate test question?");
    msq.setQuestionType(MCQ.QuestionType.MSQ);
    mcqRepository.save(msq);

    boolean exists = mcqRepository.existsByQuestionStemAndStackAndTopic(
            "MSQ duplicate test question?",
            stack.getStackId(), topic.getTopicId());

    assertThat(exists).isTrue();
}


}