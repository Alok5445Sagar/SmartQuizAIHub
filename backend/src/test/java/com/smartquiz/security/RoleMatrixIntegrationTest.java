package com.smartquiz.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isOneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.smartquiz.BaseTest;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.entity.UserStackMapping;

/**
 * Who can reach what: anonymous, participant, SME, assigned reviewer and admin.
 */
@AutoConfigureMockMvc
@DisplayName("Role matrix")
class RoleMatrixIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;

    private User creator;
    private User reviewer;
    private User unmappedSme;
    private User admin;
    private User participant;
    private TechnologyStack stack;
    private Topic topic;
    private MCQ question;

    @BeforeEach
    void setUp() {
        creator = createSME("rm.creator", "RM Creator");
        reviewer = createSME("rm.reviewer", "RM Reviewer");
        unmappedSme = createSME("rm.unmapped", "RM Unmapped");
        admin = createAdmin("rm.admin");
        participant = new User();
        participant.setEnterpriseId("rm.participant");
        participant.setPassword("{noop}password123");
        participant.setFullName("RM Participant");
        participant.setRole(User.Role.PARTICIPANT);
        participant = userRepository.save(participant);

        stack = createStack("RM Stack");
        topic = createTopic("RM Topic", stack);
        UserStackMapping mapping = new UserStackMapping();
        mapping.setUser(reviewer);
        mapping.setStack(stack);
        userStackMappingRepository.save(mapping);

        question = createMCQ(creator, stack, topic, MCQ.Status.READY_FOR_REVIEW);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return user == null ? request : request.header("Authorization", bearer(user));
    }

    @Nested
    @DisplayName("URL-level access")
    class UrlAccess {

        @Test
        @DisplayName("question endpoints: anonymous 401, participant 403, SME 200")
        void questionEndpoints() throws Exception {
            String url = "/api/mcq/" + question.getQuestionId();
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            mockMvc.perform(as(get(url), participant)).andExpect(status().isForbidden());
            mockMvc.perform(as(get(url), unmappedSme)).andExpect(status().isOk());
            mockMvc.perform(as(get("/api/mcq/my-questions"), participant)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("participant cannot read correct answers or create questions")
        void participantBlockedFromAuthoring() throws Exception {
            mockMvc.perform(as(post("/api/mcq"), participant).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(as(get("/api/mcq/" + question.getQuestionId() + "/translations"), participant))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AI and reference endpoints are SME/ADMIN only")
        void aiAndReference() throws Exception {
            mockMvc.perform(as(get("/api/ai/chat/suggestions"), participant)).andExpect(status().isForbidden());
            mockMvc.perform(as(get("/api/ai/chat/suggestions"), creator)).andExpect(status().isOk());
            mockMvc.perform(get("/api/reference/stacks")).andExpect(status().isUnauthorized());
            mockMvc.perform(as(get("/api/reference/stacks"), participant)).andExpect(status().isForbidden());
            mockMvc.perform(as(get("/api/reference/stacks"), creator)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("admin endpoints: SME 403, admin 200")
        void adminEndpoints() throws Exception {
            mockMvc.perform(as(get("/api/admin/question-bank"), creator)).andExpect(status().isForbidden());
            mockMvc.perform(as(get("/api/admin/quizzes"), creator)).andExpect(status().isForbidden());
            mockMvc.perform(as(get("/api/admin/quizzes"), participant)).andExpect(status().isForbidden());
            mockMvc.perform(as(get("/api/admin/question-bank"), admin)).andExpect(status().isOk());
            mockMvc.perform(as(get("/api/admin/quizzes"), admin)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("/api/auth/me needs a token but any role may call it")
        void authMe() throws Exception {
            mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
            mockMvc.perform(as(get("/api/auth/me"), participant))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("PARTICIPANT"));
        }

        @Test
        @DisplayName("public quiz endpoints stay open")
        void publicEndpoints() throws Exception {
            mockMvc.perform(get("/api/public/quiz/NO-SUCH-CODE/leaderboard")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("user list leaves out participants")
        void referenceUsersExcludeParticipants() throws Exception {
            mockMvc.perform(as(get("/api/reference/users"), creator))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].role", everyItem(isOneOf("SME", "ADMIN"))));
        }
    }

    @Nested
    @DisplayName("Tokens for missing or inactive users")
    class StaleTokens {

        @Test
        @DisplayName("token for a deleted user is rejected with 401, not 500")
        void deletedUser() throws Exception {
            String token = "Bearer " + jwtUtils.generateToken("rm.ghost", "SME");
            mockMvc.perform(get("/api/reference/stacks").header("Authorization", token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("token for an inactive user is rejected with 401")
        void inactiveUser() throws Exception {
            String token = bearer(unmappedSme);
            unmappedSme.setActive(false);
            userRepository.save(unmappedSme);
            mockMvc.perform(get("/api/reference/stacks").header("Authorization", token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Reviewer assignment rules")
    class Assignment {

        private String assignBody(String reviewerId) {
            return "{\"reviewerEnterpriseId\":\"" + reviewerId + "\"}";
        }

        @Test
        @DisplayName("assigning an eligible reviewer moves the question to UNDER_REVIEW")
        void eligibleReviewer() throws Exception {
            mockMvc.perform(as(post("/api/admin/assign-reviewer/" + question.getQuestionId()), admin)
                            .contentType(MediaType.APPLICATION_JSON).content(assignBody("rm.reviewer")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        }

        @Test
        @DisplayName("an SME without the stack is not an eligible reviewer")
        void ineligibleReviewer() throws Exception {
            mockMvc.perform(as(post("/api/admin/assign-reviewer/" + question.getQuestionId()), admin)
                            .contentType(MediaType.APPLICATION_JSON).content(assignBody("rm.unmapped")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Reviewer is not eligible for this question's stack"));
        }

        @Test
        @DisplayName("the creator cannot review their own question")
        void creatorCannotReview() throws Exception {
            UserStackMapping mapping = new UserStackMapping();
            mapping.setUser(creator);
            mapping.setStack(stack);
            userStackMappingRepository.save(mapping);

            mockMvc.perform(as(post("/api/admin/assign-reviewer/" + question.getQuestionId()), admin)
                            .contentType(MediaType.APPLICATION_JSON).content(assignBody("rm.creator")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("an APPROVED question cannot be reassigned")
        void approvedCannotBeAssigned() throws Exception {
            MCQ approved = createMCQ(creator, stack, topic, MCQ.Status.APPROVED);
            mockMvc.perform(as(post("/api/admin/assign-reviewer/" + approved.getQuestionId()), admin)
                            .contentType(MediaType.APPLICATION_JSON).content(assignBody("rm.reviewer")))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("an admin may assign their own DRAFT but not another user's DRAFT")
        void draftAssignment() throws Exception {
            MCQ adminDraft = createMCQ(admin, stack, topic, MCQ.Status.DRAFT);
            mockMvc.perform(as(post("/api/admin/assign-reviewer/" + adminDraft.getQuestionId()), admin)
                            .contentType(MediaType.APPLICATION_JSON).content(assignBody("rm.reviewer")))
                    .andExpect(status().isOk());

            MCQ smeDraft = createMCQ(creator, stack, topic, MCQ.Status.DRAFT);
            mockMvc.perform(as(post("/api/admin/assign-reviewer/" + smeDraft.getQuestionId()), admin)
                            .contentType(MediaType.APPLICATION_JSON).content(assignBody("rm.reviewer")))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Review rules")
    class Review {

        private MockHttpServletRequestBuilder review(MCQ mcq, User user, String action, String comments) {
            String body = "{\"action\":\"" + action + "\",\"comments\":" + (comments == null ? "null" : "\"" + comments + "\"") + "}";
            return as(post("/api/mcq/" + mcq.getQuestionId() + "/review"), user)
                    .contentType(MediaType.APPLICATION_JSON).content(body);
        }

        @Test
        @DisplayName("reviewing a question that is not UNDER_REVIEW returns 409")
        void reviewDraft_returns409() throws Exception {
            MCQ draft = createMCQ(creator, stack, topic, MCQ.Status.DRAFT);
            mockMvc.perform(review(draft, reviewer, "APPROVE", null)).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("a reviewer who is not assigned gets 403")
        void notAssigned_returns403() throws Exception {
            question.setStatus(MCQ.Status.UNDER_REVIEW);
            question.setReviewer(reviewer);
            mcqRepository.save(question);
            mockMvc.perform(review(question, unmappedSme, "APPROVE", null)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("approve keeps the reviewer's comments")
        void approveKeepsComments() throws Exception {
            question.setStatus(MCQ.Status.UNDER_REVIEW);
            question.setReviewer(reviewer);
            mcqRepository.save(question);
            mockMvc.perform(review(question, reviewer, "APPROVE", "Nice distractors"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.reviewerComments").value("Nice distractors"));
        }
    }

    @Nested
    @DisplayName("Translation rules")
    class Translation {

        private final String body = """
                {"languageCode":"es","questionStem":"Hola","optionA":"a","optionB":"b","optionC":"c","optionD":"d"}
                """;

        @Test
        @DisplayName("another SME cannot save a translation; the creator and an admin can")
        void onlyCreatorOrAdmin() throws Exception {
            String url = "/api/mcq/" + question.getQuestionId() + "/translations";
            mockMvc.perform(as(put(url), unmappedSme).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(as(put(url), creator).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
            mockMvc.perform(as(put(url), admin).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
            assertThat(question.getQuestionId()).isNotNull();
        }
    }
}
