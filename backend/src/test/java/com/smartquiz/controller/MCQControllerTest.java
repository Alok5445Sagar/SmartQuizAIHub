package com.smartquiz.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.dto.AuthDTO;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.entity.UserStackMapping;

@AutoConfigureMockMvc
@DisplayName("MCQ Controller Tests")
public class MCQControllerTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private User sme, admin, reviewer;
    private TechnologyStack stack;
    private Topic topic;
    private String smeToken, adminToken, reviewerToken;

    @BeforeEach
    void setUp() throws Exception {
        sme      = createSME("ctrl.sme", "Ctrl SME");
        admin    = createAdmin("ctrl.admin");
        reviewer = createSME("ctrl.reviewer", "Ctrl Reviewer");
        stack    = createStack("Test Stack Controller 66");
        topic    = createTopic("Test Topic Controller 66", stack);

        UserStackMapping m = new UserStackMapping();
        m.setUser(reviewer); m.setStack(stack);
        userStackMappingRepository.save(m);

        smeToken      = getToken("ctrl.sme",      "password123");
        adminToken    = getToken("ctrl.admin",     "admin123");
        reviewerToken = getToken("ctrl.reviewer",  "password123");
    }

    // ── CREATE ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/mcq creates DRAFT MCQ successfully")
    void createMCQ_returnsDraft() throws Exception {
        mockMvc.perform(post("/api/mcq")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildCreate(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.creatorEnterpriseId").value("ctrl.sme"));
    }

    @Test
    @DisplayName("POST /api/mcq with sendForReview=true returns READY_FOR_REVIEW")
    void createMCQ_sendForReview() throws Exception {
        mockMvc.perform(post("/api/mcq")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildCreate(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));
    }

    @Test
    @DisplayName("POST /api/mcq without auth returns 401")
    void createMCQ_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/mcq")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildCreate(false))))
                .andExpect(status().isUnauthorized());
    }

    // ── GET MY QUESTIONS ────────────────────────────────────

    @Test
    @DisplayName("GET /api/mcq/my-questions returns only SME's questions")
    void getMyQuestions_returnsOnlySMEOwned() throws Exception {
        createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        createMCQ(sme, stack, topic, MCQ.Status.APPROVED);
        createMCQ(reviewer, stack, topic, MCQ.Status.DRAFT);

        mockMvc.perform(get("/api/mcq/my-questions")
                .header("Authorization", "Bearer " + smeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/mcq/my-questions with status filter returns filtered results")
    void getMyQuestions_statusFilter() throws Exception {
        createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        createMCQ(sme, stack, topic, MCQ.Status.APPROVED);

        mockMvc.perform(get("/api/mcq/my-questions?status=DRAFT")
                .header("Authorization", "Bearer " + smeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /api/mcq/my-questions paginates correctly")
    void getMyQuestions_pagination() throws Exception {
        for (int i = 0; i < 15; i++) createMCQ(sme, stack, topic, MCQ.Status.DRAFT);

        mockMvc.perform(get("/api/mcq/my-questions?page=0&size=10")
                .header("Authorization", "Bearer " + smeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    // ── UPDATE ──────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/mcq/{id} updates own DRAFT question")
    void updateMCQ_ownDraft_succeeds() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);

        mockMvc.perform(put("/api/mcq/" + mcq.getQuestionId())
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildUpdate("Updated?", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionStem").value("Updated?"));
    }

    @Test
        @DisplayName("PUT /api/mcq/{id} fails when editing another user's question")
        void updateMCQ_otherUser_returnsError() throws Exception {
        MCQ mcq = createMCQ(reviewer, stack, topic, MCQ.Status.DRAFT);

        mockMvc.perform(put("/api/mcq/" + mcq.getQuestionId())
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildUpdate("Hacked?", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not authorized to edit this MCQ"));
        }

    // ── REVIEW ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/mcq/{id}/review approves question")
    void reviewMCQ_approve() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        mcq.setReviewer(reviewer); mcqRepository.save(mcq);

        mockMvc.perform(post("/api/mcq/" + mcq.getQuestionId() + "/review")
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new MCQDTO.ReviewRequest("APPROVE", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /api/mcq/{id}/review rejects with comments")
    void reviewMCQ_rejectWithComments() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        mcq.setReviewer(reviewer); mcqRepository.save(mcq);

        mockMvc.perform(post("/api/mcq/" + mcq.getQuestionId() + "/review")
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new MCQDTO.ReviewRequest("REJECT", "Fix stem."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewerComments").value("Fix stem."));
    }

    // ── PENDING REVIEWS ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/mcq/my-pending-reviews returns assigned UNDER_REVIEW questions")
    void getPendingReviews_returnsAssigned() throws Exception {
        MCQ mcq1 = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        mcq1.setReviewer(reviewer); mcqRepository.save(mcq1);
        MCQ mcq2 = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        mcq2.setReviewer(sme); mcqRepository.save(mcq2);

        mockMvc.perform(get("/api/mcq/my-pending-reviews")
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Admin can edit UNDER_REVIEW question")
    void adminEdit_underReview_succeeds() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);

        mockMvc.perform(put("/api/admin/edit-question/" + mcq.getQuestionId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildUpdate("Admin edited under review?", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionStem").value("Admin edited under review?"));
    }

    @Test
    @DisplayName("Admin can edit APPROVED question")
    void adminEdit_approved_succeeds() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.APPROVED);

        mockMvc.perform(put("/api/admin/edit-question/" + mcq.getQuestionId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildUpdate("Admin edited approved?", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionStem").value("Admin edited approved?"));
    }

    @Test
    @DisplayName("Admin cannot edit DRAFT question")
    void adminEdit_draft_returns400() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);

        mockMvc.perform(put("/api/admin/edit-question/" + mcq.getQuestionId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildUpdate("Admin hack draft?", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Admin cannot edit DRAFT")));
    }

    @Test
    @DisplayName("SME cannot edit READY_FOR_REVIEW question")
    void smeEdit_readyForReview_returnsError() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);

        mockMvc.perform(put("/api/mcq/" + mcq.getQuestionId())
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildUpdate("SME hack?", false))))
                .andExpect(status().isBadRequest());
    }

    // ── ADMIN ENDPOINTS ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/admin/question-bank is forbidden for SME")
    void questionBank_forbiddenForSME() throws Exception {
        mockMvc.perform(get("/api/admin/question-bank")
                .header("Authorization", "Bearer " + smeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/question-bank returns all MCQs for Admin")
    void questionBank_adminSeesAll() throws Exception {
        createMCQ(sme,      stack, topic, MCQ.Status.DRAFT);
        createMCQ(reviewer, stack, topic, MCQ.Status.APPROVED);

        mockMvc.perform(get("/api/admin/question-bank")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("POST /api/admin/assign-reviewer assigns reviewer successfully")
    void assignReviewer_succeeds() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);

        mockMvc.perform(post("/api/admin/assign-reviewer/" + mcq.getQuestionId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("reviewerEnterpriseId", reviewer.getEnterpriseId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewerEnterpriseId").value(reviewer.getEnterpriseId()));
    }

    @Test
    @DisplayName("POST /api/admin/assign-reviewer is forbidden for SME")
    void assignReviewer_forbiddenForSME() throws Exception {
        MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.READY_FOR_REVIEW);

        mockMvc.perform(post("/api/admin/assign-reviewer/" + mcq.getQuestionId())
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("reviewerEnterpriseId", reviewer.getEnterpriseId()))))
                .andExpect(status().isForbidden());
    }

    // ── REFERENCE ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/reference/stacks returns stacks")
    void getStacks_returnsAll() throws Exception {
        mockMvc.perform(get("/api/reference/stacks")
                .header("Authorization", "Bearer " + smeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/reference/topics/{stackId} returns topics for stack")
    void getTopics_returnsForStack() throws Exception {
        mockMvc.perform(get("/api/reference/topics/" + stack.getStackId())
                .header("Authorization", "Bearer " + smeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    // ── DUPLICATE DETECTION ─────────────────────────────────

@Test
@DisplayName("POST /api/mcq rejects duplicate question stem in same stack and topic")
void createMCQ_duplicate_returns400() throws Exception {
    // Create first
    mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(buildCreate(false))))
            .andExpect(status().isOk());

    // Try creating duplicate
    mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(buildCreate(false))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                containsString("Duplicate question")));
}

@Test
@DisplayName("PUT /api/mcq/{id} rejects duplicate stem matching another question")
void updateMCQ_duplicateStem_returns400() throws Exception {
    // Create two questions
    mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(buildCreate(false))))
            .andExpect(status().isOk());

    MCQ q2 = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
    q2.setQuestionStem("A different question?");
    mcqRepository.save(q2);

    // Try updating q2 to have same stem as q1
    MCQDTO.UpdateRequest updateReq = new MCQDTO.UpdateRequest(
        "Test question?", "MCQ",
        stack.getStackId(), topic.getTopicId(), "Easy",
        "Option A", "Option B", "Option C", "Option D", null, null, "A", false);

    mockMvc.perform(put("/api/mcq/" + q2.getQuestionId())
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                containsString("Duplicate question")));
}

// ── MSQ CONTROLLER TESTS ─────────────────────────────────

@Test
@DisplayName("POST /api/mcq creates MSQ with multiple correct answers")
void createMSQ_returnsCorrectType() throws Exception {
    MCQDTO.CreateRequest req = new MCQDTO.CreateRequest(
        "Which are OOP concepts?", "MSQ",
        stack.getStackId(), topic.getTopicId(), "Medium",
        "Inheritance", "Compilation", "Polymorphism", "Linking",
        null, null, "A,C", false, false);

    mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionType").value("MSQ"))
            .andExpect(jsonPath("$.correctAnswer").value("A,C"))
            .andExpect(jsonPath("$.status").value("DRAFT"));
}

@Test
@DisplayName("POST /api/mcq MSQ with optionE and optionF saves correctly")
void createMSQ_withSixOptions() throws Exception {
    MCQDTO.CreateRequest req = new MCQDTO.CreateRequest(
        "Which are valid access modifiers?", "MSQ",
        stack.getStackId(), topic.getTopicId(), "Easy",
        "public", "private", "protected", "default",
        "internal", "external", "A,B,C,D", false, false);

    mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.optionE").value("internal"))
            .andExpect(jsonPath("$.optionF").value("external"))
            .andExpect(jsonPath("$.correctAnswer").value("A,B,C,D"));
}

@Test
@DisplayName("GET /api/mcq/{id} returns questionType field in response")
void getMCQById_returnsQuestionType() throws Exception {
    MCQ mcq = createMCQ(sme, stack, topic, MCQ.Status.DRAFT);

    mockMvc.perform(get("/api/mcq/" + mcq.getQuestionId())
            .header("Authorization", "Bearer " + smeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionType").value("MCQ"));
}

    // ── HELPERS ─────────────────────────────────────────────

    private String getToken(String id, String pass) throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthDTO.LoginRequest(id, pass))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }

    private MCQDTO.CreateRequest buildCreate(boolean send) {
        return new MCQDTO.CreateRequest("Test question?", "MCQ",
                stack.getStackId(), topic.getTopicId(), "Easy",
                "Option A", "Option B", "Option C", "Option D", null, null, "A", send, false);
    }

        private MCQDTO.UpdateRequest buildUpdate(String stem, boolean send) {
        return new MCQDTO.UpdateRequest(stem, "MCQ",
                stack.getStackId(), topic.getTopicId(), "Medium",
                "Option A", "Option B", "Option C", "Option D", null, null, "B", send);
        }
}