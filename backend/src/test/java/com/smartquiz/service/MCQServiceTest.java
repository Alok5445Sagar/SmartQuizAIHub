package com.smartquiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import com.smartquiz.BaseTest;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.entity.UserStackMapping;

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
        stack    = createStack("Test Stack Service 77");
        topic    = createTopic("Test Topic Service 77", stack);

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
        assertThat(r.getStackName()).isEqualTo("Test Stack Service 77");
        assertThat(r.getTopicName()).isEqualTo("Test Topic Service 77");
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

    @Test
    @DisplayName("Admin can update UNDER_REVIEW question")
    void updateMCQ_adminCanUpdateUnderReview() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        MCQDTO.Response r = mcqService.updateMCQ(
                admin.getEnterpriseId(), mcq.getQuestionId(), buildUpdate("Admin fix under review?", false), true);
        assertThat(r.getQuestionStem()).isEqualTo("Admin fix under review?");
    }

    @Test
    @DisplayName("Admin can update APPROVED question")
    void updateMCQ_adminCanUpdateApproved() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        MCQDTO.Response r = mcqService.updateMCQ(
                admin.getEnterpriseId(), mcq.getQuestionId(), buildUpdate("Admin fix approved?", false), true);
        assertThat(r.getQuestionStem()).isEqualTo("Admin fix approved?");
    }

    @Test
    @DisplayName("Admin can update REJECTED question")
    void updateMCQ_adminCanUpdateRejected() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.REJECTED);
        MCQDTO.Response r = mcqService.updateMCQ(
                admin.getEnterpriseId(), mcq.getQuestionId(), buildUpdate("Admin fix rejected?", false), true);
        assertThat(r.getQuestionStem()).isEqualTo("Admin fix rejected?");
    }

    @Test
    @DisplayName("Admin cannot update DRAFT question")
    void updateMCQ_adminCannotUpdateDraft() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        assertThatThrownBy(() ->
            mcqService.updateMCQ(admin.getEnterpriseId(), mcq.getQuestionId(),
                    buildUpdate("Admin hack draft?", false), true))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Admin cannot edit DRAFT");
    }

    @Test
    @DisplayName("SME can delete own DRAFT question")
    void deleteMCQ_smeCanDeleteOwnDraft() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);

        mcqService.deleteMCQ(sme.getEnterpriseId(), mcq.getQuestionId(), false);

        assertThat(mcqRepository.findById(mcq.getQuestionId())).isEmpty();
    }

    @Test
    @DisplayName("SME cannot delete READY_FOR_REVIEW question")
    void deleteMCQ_smeCannotDeleteReadyForReview() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);

        assertThatThrownBy(() ->
            mcqService.deleteMCQ(sme.getEnterpriseId(), mcq.getQuestionId(), false))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Can only delete DRAFT or REJECTED questions");
    }

    @Test
    @DisplayName("Admin can delete APPROVED question")
    void deleteMCQ_adminCanDeleteApproved() {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);

        mcqService.deleteMCQ(admin.getEnterpriseId(), mcq.getQuestionId(), true);

        assertThat(mcqRepository.findById(mcq.getQuestionId())).isEmpty();
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

    // ── DUPLICATE DETECTION ─────────────────────────────────

@Test
@DisplayName("Create MCQ does NOT fail when duplicate stem is only in DRAFT status")
void createMCQ_duplicateStemInDraft_allowed() {
    // Create first question as DRAFT
    mcqService.createMCQ(sme.getEnterpriseId(), buildCreate(false));

    // Creating same stem again as DRAFT should be allowed
    // because DRAFT questions don't block each other
    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(),
        new MCQDTO.CreateRequest("Test question?", "MCQ",
            stack.getStackId(), topic.getTopicId(), "Easy",
            "Option A", "Option B", "Option C", "Option D",
            null, null, "B", false, false));
    assertThat(r).isNotNull();
}

@Test
@DisplayName("Create MCQ allows manual duplicate stem even when APPROVED exists")
void createMCQ_duplicateStemApproved_manualAllowed() {
    // Create first question and mark as APPROVED
    MCQ approved = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
    approved.setQuestionStem("Test question?");
    mcqRepository.save(approved);

    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), buildCreate(false));
    assertThat(r).isNotNull();
}

@Test
@DisplayName("Create MCQ succeeds with same stem in different topic")
void createMCQ_sameStemDifferentTopic_succeeds() {
    Topic topic2 = createTopic("Spring Boot Testing", stack);

    MCQDTO.CreateRequest req1 = buildCreate(false);
    MCQDTO.CreateRequest req2 = new MCQDTO.CreateRequest(
        "Test question?", "MCQ",
        stack.getStackId(), topic2.getTopicId(), "Easy",
        "Option A", "Option B", "Option C", "Option D", null, null, "B", false, false);

    mcqService.createMCQ(sme.getEnterpriseId(), req1);
    // Should not throw — different topic
    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), req2);
    assertThat(r.getQuestionId()).isNotNull();
}

@Test
@DisplayName("Update MCQ allows manual duplicate stem for DRAFT flow")
void updateMCQ_duplicateStem_manualDraftAllowed() {
    // Create two questions
    MCQDTO.Response q1 = mcqService.createMCQ(sme.getEnterpriseId(), buildCreate(false));

    MCQDTO.CreateRequest req2 = new MCQDTO.CreateRequest(
        "Another question?", "MCQ",
        stack.getStackId(), topic.getTopicId(), "Easy",
        "Option A", "Option B", "Option C", "Option D", null, null, "A", false, false);
    MCQ q2 = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    q2.setQuestionStem("Another question?");
    mcqRepository.save(q2);

    // Try updating q2 to have same stem as q1
    MCQDTO.UpdateRequest updateReq = new MCQDTO.UpdateRequest(
        "Test question?", "MCQ",
        stack.getStackId(), topic.getTopicId(), "Medium",
        "Option A", "Option B", "Option C", "Option D", null, null, "A", false);

    MCQDTO.Response r = mcqService.updateMCQ(sme.getEnterpriseId(), q2.getQuestionId(), updateReq, false);
    assertThat(r).isNotNull();
}

@Test
@DisplayName("Update MCQ succeeds with same stem as itself (no false duplicate)")
void updateMCQ_sameStemAsSelf_succeeds() {
    MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    mcq.setQuestionStem("Self update question?");
    mcqRepository.save(mcq);

    // Update with same stem — should NOT throw
    MCQDTO.UpdateRequest req = new MCQDTO.UpdateRequest(
        "Self update question?", "MCQ",
        stack.getStackId(), topic.getTopicId(), "Hard",
        "Option A", "Option B", "Option C", "Option D", null, null, "B", false);

    MCQDTO.Response r = mcqService.updateMCQ(
            sme.getEnterpriseId(), mcq.getQuestionId(), req, false);
    assertThat(r.getQuestionStem()).isEqualTo("Self update question?");
}

@Test
@DisplayName("Whitespace variation in manual create is allowed")
void createMCQ_extraWhitespaceStem_manualAllowed() {
    // Create as APPROVED so it blocks duplicates
    MCQ approved = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
    approved.setQuestionStem("Test question?");
    mcqRepository.save(approved);

    // Same question with extra internal spaces — should be blocked
    MCQDTO.CreateRequest reqWithSpaces = new MCQDTO.CreateRequest(
        "Test  question?", "MCQ",  // double space
        stack.getStackId(), topic.getTopicId(), "Easy",
        "Option A", "Option B", "Option C", "Option D", null, null, "B", false, false);

    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), reqWithSpaces);
    assertThat(r).isNotNull();
}

// ── MSQ TESTS ────────────────────────────────────────────

@Test
@DisplayName("Create MSQ saves correctly with multiple correct answers")
void createMSQ_savesCorrectly() {
    MCQDTO.CreateRequest req = new MCQDTO.CreateRequest(
        "Which are OOP principles?", "MSQ",
        stack.getStackId(), topic.getTopicId(), "Medium",
        "Inheritance", "Compilation", "Encapsulation", "Linking",
        "Polymorphism", null, "A,C,E", false, false);

    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), req);

    assertThat(r.getQuestionType()).isEqualTo("MSQ");
    assertThat(r.getCorrectAnswer()).isEqualTo("A,C,E");
    assertThat(r.getOptionE()).isEqualTo("Polymorphism");
}

@Test
@DisplayName("Create MSQ saves as READY_FOR_REVIEW when sendForReview=true")
void createMSQ_sendForReview() {
    MCQDTO.CreateRequest req = new MCQDTO.CreateRequest(
        "Which are Java collection types?", "MSQ",
        stack.getStackId(), topic.getTopicId(), "Easy",
        "ArrayList", "HashMap", "Compilation", "LinkedList",
        null, null, "A,B,D", true, false);

    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), req);

    assertThat(r.getStatus()).isEqualTo("READY_FOR_REVIEW");
    assertThat(r.getQuestionType()).isEqualTo("MSQ");
}

@Test
@DisplayName("Create MSQ with optionE and optionF saves both options")
void createMSQ_withSixOptions_savesBothEAndF() {
    MCQDTO.CreateRequest req = new MCQDTO.CreateRequest(
        "Which are valid Java keywords?", "MSQ",
        stack.getStackId(), topic.getTopicId(), "Hard",
        "class", "struct", "interface", "pointer",
        "extends", "implements", "A,C,E,F", false, false);

    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), req);

    assertThat(r.getOptionE()).isEqualTo("extends");
    assertThat(r.getOptionF()).isEqualTo("implements");
    assertThat(r.getCorrectAnswer()).isEqualTo("A,C,E,F");
}

@Test
@DisplayName("Manual MSQ duplicate create is allowed")
void createMSQ_duplicate_manualAllowed() {
    // Create as APPROVED first
    MCQ approved = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
    approved.setQuestionStem("Which are OOP principles?");
    approved.setQuestionType(MCQ.QuestionType.MSQ);
    mcqRepository.save(approved);

    final MCQDTO.CreateRequest req = new MCQDTO.CreateRequest(
        "Which are OOP principles?", "MSQ",
        stack.getStackId(), topic.getTopicId(), "Medium",
        "Inheritance", "Compilation", "Encapsulation", "Linking",
        "Polymorphism", null, "A,C,E", false, false);

    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), req);
    assertThat(r).isNotNull();
}

@Test
@DisplayName("Manual MCQ and MSQ with same stem are both allowed at create stage")
void mcqAndMsq_sameStem_manualCreateAllowed() {
    // Create as MCQ
    mcqService.createMCQ(sme.getEnterpriseId(), buildCreate(false));

    // Try creating as MSQ with same stem
    MCQDTO.CreateRequest msqReq = new MCQDTO.CreateRequest(
        "Test question?", "MSQ",
        stack.getStackId(), topic.getTopicId(), "Easy",
        "Option A", "Option B", "Option C", "Option D",
        "Option E", null, "A,C", false, false);

    MCQDTO.Response r = mcqService.createMCQ(sme.getEnterpriseId(), msqReq);
    assertThat(r).isNotNull();
}

@Test
@DisplayName("AI-generated MCQ create fails when duplicate stem exists in APPROVED status")
void createMCQ_aiGeneratedDuplicateStemApproved_throwsException() {
    MCQ approved = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
    approved.setQuestionStem("Test question?");
    mcqRepository.save(approved);

    MCQDTO.CreateRequest aiReq = new MCQDTO.CreateRequest(
            "Test question?", "MCQ",
            stack.getStackId(), topic.getTopicId(), "Easy",
            "Option A", "Option B", "Option C", "Option D", null, null, "B", false, true);

    assertThatThrownBy(() -> mcqService.createMCQ(sme.getEnterpriseId(), aiReq))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Duplicate question");
}

@Test
@DisplayName("Update MCQ to MSQ type succeeds")
void updateMCQ_changeTypeToMSQ_succeeds() {
    MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);

    MCQDTO.UpdateRequest req = new MCQDTO.UpdateRequest(
        "What is Spring Boot?", "MSQ",
        stack.getStackId(), topic.getTopicId(), "Medium",
        "A framework", "A database", "A build tool", "An ORM",
        "A Spring module", null, "A,C,E", false);

    MCQDTO.Response r = mcqService.updateMCQ(
            sme.getEnterpriseId(), mcq.getQuestionId(), req, false);

    assertThat(r.getQuestionType()).isEqualTo("MSQ");
    assertThat(r.getCorrectAnswer()).isEqualTo("A,C,E");
}

    // ── HELPERS ───────────────────────────────────────────

    private MCQDTO.CreateRequest buildCreate(boolean send) {
        return new MCQDTO.CreateRequest("Test question?", "MCQ", stack.getStackId(),
                topic.getTopicId(), "Easy",
                "Option A", "Option B", "Option C", "Option D", null, null, "B", send, false);
    }

private MCQDTO.UpdateRequest buildUpdate(String stem, boolean send) {
    return new MCQDTO.UpdateRequest(stem, "MCQ", stack.getStackId(),
            topic.getTopicId(), "Medium",
            "Option A", "Option B", "Option C", "Option D", null, null, "A", send);
}
}