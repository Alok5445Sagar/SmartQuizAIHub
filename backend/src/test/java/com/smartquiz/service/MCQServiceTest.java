package com.smartquiz.service;

import com.smartquiz.BaseTest;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MCQ Service Tests")
public class MCQServiceTest extends BaseTest {

    @Autowired private MCQService mcqService;

    private User sme, admin, reviewer;
    private TechnologyStack stack;
    private Topic topic;

    @BeforeEach
    void setUp() {
        sme      = createSME("svc.sme", "Service SME");
        admin    = createAdmin("svc.admin");
        reviewer = createSME("svc.reviewer", "Service Reviewer");
        stack    = createStack("Spring Boot");
        topic    = createTopic("Spring Boot Starters", stack);

        UserStackMapping m = new UserStackMapping();
        m.setUser(reviewer);
        m.setStack(stack);
        userStackMappingRepository.save(m);
    }

    // ── CREATE ──────────────────────────────────────────────

    @Test
    @DisplayName("Create MCQ saves as DRAFT when sendForReview=false")
    void createMCQ_savesAsDraft() {
        MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), buildCreate(false));
        assertThat(r.getStatus()).isEqualTo("DRAFT");
        assertThat(r.getCreatorEnterpriseId()).isEqualTo(sme.getEnterpriseId());
    }

    @Test
    @DisplayName("Create MCQ saves as READY_FOR_REVIEW when sendForReview=true")
    void createMCQ_savesAsReadyForReview() {
        MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), buildCreate(true));
        assertThat(r.getStatus()).isEqualTo("READY_FOR_REVIEW");
    }

    @Test
    @DisplayName("Create MCQ stores all four options correctly")
    void createMCQ_storesAllOptions() {
        MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), buildCreate(false));
        assertThat(r.getOptionA()).isEqualTo("Option A");
        assertThat(r.getOptionB()).isEqualTo("Option B");
        assertThat(r.getOptionC()).isEqualTo("Option C");
        assertThat(r.getOptionD()).isEqualTo("Option D");
        assertThat(r.getCorrectAnswer()).isEqualTo("B");
    }

    @Test
    @DisplayName("Create MCQ links correct stack and topic")
    void createMCQ_linksStackAndTopic() {
        MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), buildCreate(false));
        assertThat(r.getStackName()).isEqualTo("Spring Boot");
        assertThat(r.getTopicName()).isEqualTo("Spring Boot Starters");
    }

    // ── UPDATE ──────────────────────────────────────────────

    @Test
    @DisplayName("SME can update their own DRAFT question")
    void updateMCQ_smeCanUpdateOwnDraft() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        MCQDTO.Response r = mcqService.updateMCQ(
                sme.getEnterpriseId(), mcq.getQuestionId(), buildUpdate("Updated?", false), false);
        assertThat(r.getQuestionStem()).isEqualTo("Updated?");
    }

    @Test
    @DisplayName("SME can update REJECTED question and resubmit for review")
    void updateMCQ_smeCanUpdateRejectedAndResubmit() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.REJECTED);
        MCQDTO.Response r = mcqService.updateMCQ(
                sme.getEnterpriseId(), mcq.getQuestionId(), buildUpdate("Fixed?", true), false);
        assertThat(r.getStatus()).isEqualTo("READY_FOR_REVIEW");
    }

    @Test
    @DisplayName("SME cannot update another user's question")
    void updateMCQ_smeCannotUpdateOtherUserQuestion() {
        MCQ mcq = createMCQ(reviewer, stack, topic, MCQ.Status.DRAFT);
        assertThatThrownBy(() ->
            mcqService.updateMCQ(sme.getEnterpriseId(), mcq.getQuestionId(), buildUpdate("Hack?", false), false))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Not authorized");
    }

    @Test
    @DisplayName("SME cannot update READY_FOR_REVIEW question")
    void updateMCQ_smeCannotUpdateReadyForReview() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);
        assertThatThrownBy(() ->
            mcqService.updateMCQ(sme.getEnterpriseId(), mcq.getQuestionId(), buildUpdate("X?", false), false))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Admin can update READY_FOR_REVIEW question")
    void updateMCQ_adminCanUpdateReadyForReview() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);
        MCQDTO.Response r = mcqService.updateMCQ(
                admin.getEnterpriseId(), mcq.getQuestionId(), buildUpdate("Admin fix?", false), true);
        assertThat(r.getQuestionStem()).isEqualTo("Admin fix?");
    }

    // ── REVIEW ──────────────────────────────────────────────

    @Test
    @DisplayName("Reviewer can approve a question")
    void reviewMCQ_approvesSuccessfully() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        mcq.setReviewer(reviewer);
        mcqRepository.save(mcq);

        MCQDTO.Response r = mcqService.reviewMCQ(
                reviewer.getEnterpriseId(), mcq.getQuestionId(),
                new MCQDTO.ReviewRequest("APPROVE", null));
        assertThat(r.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Reviewer can reject with mandatory comments")
    void reviewMCQ_rejectsWithComments() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        mcq.setReviewer(reviewer);
        mcqRepository.save(mcq);

        MCQDTO.Response r = mcqService.reviewMCQ(
                reviewer.getEnterpriseId(), mcq.getQuestionId(),
                new MCQDTO.ReviewRequest("REJECT", "Fix the stem."));
        assertThat(r.getStatus()).isEqualTo("REJECTED");
        assertThat(r.getReviewerComments()).isEqualTo("Fix the stem.");
    }

    @Test
    @DisplayName("Reject without comments throws exception")
    void reviewMCQ_rejectWithoutComments_throwsException() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        mcq.setReviewer(reviewer);
        mcqRepository.save(mcq);

        assertThatThrownBy(() ->
            mcqService.reviewMCQ(reviewer.getEnterpriseId(), mcq.getQuestionId(),
                new MCQDTO.ReviewRequest("REJECT", "")))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("mandatory");
    }

    @Test
    @DisplayName("Non-assigned reviewer cannot review question")
    void reviewMCQ_nonReviewerThrowsException() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        mcq.setReviewer(reviewer);
        mcqRepository.save(mcq);

        User otherUser = createSME("other.user", "Other");
        assertThatThrownBy(() ->
            mcqService.reviewMCQ(otherUser.getEnterpriseId(), mcq.getQuestionId(),
                new MCQDTO.ReviewRequest("APPROVE", null)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Not authorized");
    }

    // ── ASSIGN REVIEWER ────────────────────────────────────

    @Test
    @DisplayName("Assign reviewer moves question to UNDER_REVIEW")
    void assignReviewer_movesToUnderReview() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);
        MCQDTO.Response r = mcqService.assignReviewer(mcq.getQuestionId(), reviewer.getEnterpriseId());
        assertThat(r.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(r.getReviewerEnterpriseId()).isEqualTo(reviewer.getEnterpriseId());
    }

    // ── FILTERS ───────────────────────────────────────────

    @Test
    @DisplayName("getMyQuestions filters by difficulty")
    void getMyQuestions_filtersByDifficulty() {
        createMCQ(sme, stack, topic, MCQ.Status.DRAFT); // Easy
        MCQ hard = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        hard.setDifficulty(MCQ.Difficulty.Hard);
        mcqRepository.save(hard);

        Page<MCQDTO.Response> result = mcqService.getMyQuestions(
                sme.getEnterpriseId(), 0, 10, null, null, "Hard", null);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDifficulty()).isEqualTo("Hard");
    }

    @Test
    @DisplayName("getMyQuestions filters by status")
    void getMyQuestions_filtersByStatus() {
        createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        createMCQ(sme, stack, topic, MCQ.Status.APPROVED);

        Page<MCQDTO.Response> result = mcqService.getMyQuestions(
                sme.getEnterpriseId(), 0, 10, null, null, null, "APPROVED");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("getAllMCQs filters by search term")
    void getAllMCQs_filtersBySearch() {
        MCQ m1 = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        m1.setQuestionStem("UNIQUESEARCHTERM_XYZ_question one");
        mcqRepository.save(m1);
        MCQ m2 = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        m2.setQuestionStem("UNIQUESEARCHTERM_ABC_question two");
        mcqRepository.save(m2);

        Page<MCQDTO.Response> result = mcqService.getAllMCQs(
                0, 10, null, null, null, null, "UNIQUESEARCHTERM_XYZ");
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getQuestionStem())
                .contains("UNIQUESEARCHTERM_XYZ");
    }

    // ── HELPERS ───────────────────────────────────────────

    private MCQDTO.CreateRequest buildCreate(boolean send) {
        return new MCQDTO.CreateRequest("Test question?", stack.getStackId(),
                topic.getTopicId(), "Easy",
                "Option A", "Option B", "Option C", "Option D", "B", send);
    }

    private MCQDTO.UpdateRequest buildUpdate(String stem, boolean send) {
        return new MCQDTO.UpdateRequest(stem, stack.getStackId(),
                topic.getTopicId(), "Medium",
                "Option A", "Option B", "Option C", "Option D", "A", send);
    }
}