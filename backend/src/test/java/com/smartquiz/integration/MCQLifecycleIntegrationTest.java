package com.smartquiz.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.dto.AuthDTO;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@AutoConfigureMockMvc
@DisplayName("MCQ Full Lifecycle Integration Tests")
public class MCQLifecycleIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;

    private User sme, admin, reviewer;
    private TechnologyStack stack;
    private Topic topic;
    private String smeToken, adminToken, reviewerToken;

    @BeforeEach
    void setUp() throws Exception {
        sme      = createSME("life.sme",      "Lifecycle SME");
        admin    = createAdmin("life.admin");
        reviewer = createSME("life.reviewer", "Lifecycle Reviewer");
        stack    = createStack("Test Stack Integration 55");
        topic    = createTopic("Test Topic Integration 55", stack);

        UserStackMapping m = new UserStackMapping();
        m.setUser(reviewer); m.setStack(stack);
        userStackMappingRepository.save(m);

        smeToken      = getToken("life.sme",      "password123");
        adminToken    = getToken("life.admin",     "admin123");
        reviewerToken = getToken("life.reviewer",  "password123");
    }

    @Test
    @DisplayName("Full lifecycle: DRAFT → READY_FOR_REVIEW → UNDER_REVIEW → APPROVED")
    void fullLifecycle_draftToApproved() throws Exception {
        // Step 1: SME creates MCQ as DRAFT
        MvcResult createResult = mockMvc.perform(post("/api/mcq")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new MCQDTO.CreateRequest(
                    "What is REST?", "MCQ",
                    stack.getStackId(), topic.getTopicId(), "Easy",
                    "A protocol", "An architectural style", "A framework", "A database",
                    null, null, "B", false, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        Long questionId = mapper.readTree(createResult.getResponse().getContentAsString())
                .get("questionId").asLong();

        // Step 2: SME sends for review
        mockMvc.perform(put("/api/mcq/" + questionId)
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new MCQDTO.UpdateRequest(
                    "What is REST?", "MCQ",
                    stack.getStackId(), topic.getTopicId(), "Easy",
                    "A protocol", "An architectural style", "A framework", "A database",
                    null, null, "B", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));

        // Step 3: Admin assigns reviewer
        mockMvc.perform(post("/api/admin/assign-reviewer/" + questionId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("reviewerEnterpriseId", "life.reviewer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewerEnterpriseId").value("life.reviewer"));

        // Step 4: Reviewer approves
        mockMvc.perform(post("/api/mcq/" + questionId + "/review")
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new MCQDTO.ReviewRequest("APPROVE", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Verify in DB
        MCQ saved = mcqRepository.findById(questionId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MCQ.Status.APPROVED);
    }

    @Test
    @DisplayName("Full lifecycle: DRAFT → READY_FOR_REVIEW → UNDER_REVIEW → REJECTED → DRAFT → APPROVED")
    void fullLifecycle_rejectedThenResubmittedAndApproved() throws Exception {
        // Step 1: Create as DRAFT
        MvcResult createResult = mockMvc.perform(post("/api/mcq")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new MCQDTO.CreateRequest(
                    "What is Spring?", "MCQ",
                    stack.getStackId(), topic.getTopicId(), "Medium",
                    "A season", "A framework", "A database", "A cloud",
                    null, null, "B", true, false))))
                .andExpect(status().isOk())
                .andReturn();

        Long questionId = mapper.readTree(createResult.getResponse().getContentAsString())
                .get("questionId").asLong();

        // Step 2: Admin assigns reviewer
        mockMvc.perform(post("/api/admin/assign-reviewer/" + questionId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("reviewerEnterpriseId", "life.reviewer"))))
                .andExpect(status().isOk());

        // Step 3: Reviewer rejects with comments
        mockMvc.perform(post("/api/mcq/" + questionId + "/review")
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                        new MCQDTO.ReviewRequest("REJECT", "Please improve the options."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewerComments").value("Please improve the options."));

        // Step 4: SME fixes and resubmits
        mockMvc.perform(put("/api/mcq/" + questionId)
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new MCQDTO.UpdateRequest(
                    "What is Spring Framework?", "MCQ",
                    stack.getStackId(), topic.getTopicId(), "Medium",
                    "A cloud", "A Java framework", "A database", "A protocol",
                    null, null, "B", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));

        // Step 5: Admin assigns reviewer again
        mockMvc.perform(post("/api/admin/assign-reviewer/" + questionId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("reviewerEnterpriseId", "life.reviewer"))))
                .andExpect(status().isOk());

        // Step 6: Reviewer approves
        mockMvc.perform(post("/api/mcq/" + questionId + "/review")
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new MCQDTO.ReviewRequest("APPROVE", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("SME cannot see another SME's questions in my-questions")
    void dataIsolation_smeCannotSeeOtherSMEQuestions() throws Exception {
        createMCQ(sme, stack, topic, MCQ.Status.DRAFT);
        createMCQ(reviewer, stack, topic, MCQ.Status.DRAFT);

        mockMvc.perform(get("/api/mcq/my-questions")
                .header("Authorization", "Bearer " + smeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/mcq/my-questions")
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Admin can see all MCQs from all users")
    void adminCanSeeAllMCQs() throws Exception {
        createMCQ(sme,      stack, topic, MCQ.Status.DRAFT);
        createMCQ(reviewer, stack, topic, MCQ.Status.APPROVED);

        mockMvc.perform(get("/api/admin/question-bank")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Reviewer sees only questions assigned to them in pending-reviews")
    void reviewerSeesOnlyAssignedReviews() throws Exception {
        MCQ m1 = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        m1.setReviewer(reviewer); mcqRepository.save(m1);

        MCQ m2 = createMCQ(sme, stack, topic, MCQ.Status.UNDER_REVIEW);
        m2.setReviewer(sme); mcqRepository.save(m2);

        mockMvc.perform(get("/api/mcq/my-pending-reviews")
                .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
        @DisplayName("Duplicate question is rejected end-to-end")
        void duplicateQuestion_isRejected() throws Exception {
        // Create first question
        mockMvc.perform(post("/api/mcq")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new MCQDTO.CreateRequest(
                        "What is REST?", "MCQ",
                        stack.getStackId(), topic.getTopicId(), "Easy",
                        "Protocol", "Style", "Framework", "Database", null, null, "B", false, false))))
                .andExpect(status().isOk());

        // Try creating same question again
        mockMvc.perform(post("/api/mcq")
                .header("Authorization", "Bearer " + smeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new MCQDTO.CreateRequest(
                        "What is REST?", "MCQ",
                        stack.getStackId(), topic.getTopicId(), "Hard",
                        "A", "B", "C", "D", null, null, "A", false, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Duplicate")));
        }


        // ── MSQ LIFECYCLE TESTS ───────────────────────────────────

@Test
@DisplayName("Full MSQ lifecycle: DRAFT → APPROVED")
void msqLifecycle_draftToApproved() throws Exception {
    // Step 1: SME creates MSQ
    MvcResult result = mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new MCQDTO.CreateRequest(
                    "Which are Spring Boot starters?", "MSQ",
                    stack.getStackId(), topic.getTopicId(), "Medium",
                    "spring-boot-starter-web", "spring-boot-starter-xyz",
                    "spring-boot-starter-test", "spring-boot-starter-data-jpa",
                    null, null, "A,C,D", true, false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionType").value("MSQ"))
            .andExpect(jsonPath("$.correctAnswer").value("A,C,D"))
            .andReturn();

    Long questionId = mapper.readTree(
            result.getResponse().getContentAsString()).get("questionId").asLong();

    // Step 2: Admin assigns reviewer
    mockMvc.perform(post("/api/admin/assign-reviewer/" + questionId)
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(
                    Map.of("reviewerEnterpriseId", "life.reviewer"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

    // Step 3: Reviewer approves
    mockMvc.perform(post("/api/mcq/" + questionId + "/review")
            .header("Authorization", "Bearer " + reviewerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(
                    new MCQDTO.ReviewRequest("APPROVE", null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.questionType").value("MSQ"));

    // Verify in DB
    MCQ saved = mcqRepository.findById(questionId).orElseThrow();
    assertThat(saved.getQuestionType()).isEqualTo(MCQ.QuestionType.MSQ);
    assertThat(saved.getCorrectAnswer()).isEqualTo("A,C,D");
    assertThat(saved.getStatus()).isEqualTo(MCQ.Status.APPROVED);
}

@Test
@DisplayName("Duplicate MCQ rejected across both SME and Admin flows")
void duplicate_rejectedAcrossAllFlows() throws Exception {
    // SME creates question
    mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new MCQDTO.CreateRequest(
                    "What is dependency injection?", "MCQ",
                    stack.getStackId(), topic.getTopicId(), "Easy",
                    "A design pattern", "A database", "A server", "A framework",
                    null, null, "A", false, false))))
            .andExpect(status().isOk());

    // Same SME tries to add duplicate
    mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + smeToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new MCQDTO.CreateRequest(
                    "What is dependency injection?", "MCQ",
                    stack.getStackId(), topic.getTopicId(), "Hard",
                    "X", "Y", "Z", "W",
                    null, null, "B", false, false))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("Duplicate")));

    // Different SME tries same question
    mockMvc.perform(post("/api/mcq")
            .header("Authorization", "Bearer " + reviewerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(new MCQDTO.CreateRequest(
                    "What is dependency injection?", "MCQ",
                    stack.getStackId(), topic.getTopicId(), "Medium",
                    "P", "Q", "R", "S",
                    null, null, "C", false, false))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("Duplicate")));
}




    private String getToken(String id, String pass) throws Exception {
        String res = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new AuthDTO.LoginRequest(id, pass))))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("token").asText();
    }


}