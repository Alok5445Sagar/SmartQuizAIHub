package com.smartquiz.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttemptSession;
import com.smartquiz.entity.QuizQuestion;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.QuizAttemptRepository;
import com.smartquiz.repository.QuizAttemptSessionRepository;
import com.smartquiz.repository.QuizQuestionRepository;
import com.smartquiz.repository.QuizRepository;
import com.smartquiz.security.JwtUtils;

@AutoConfigureMockMvc
@DisplayName("Quiz attempt start/submit flow")
class QuizAttemptFlowIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptSessionRepository sessionRepository;

    private Quiz quiz;
    private MCQ q1;
    private MCQ q2;

    @BeforeEach
    void setUp() {
        User admin = createAdmin("attempt.flow.admin");
        TechnologyStack stack = createStack("Attempt Flow Stack");
        Topic topic = createTopic("Attempt Flow Topic", stack);
        q1 = createMCQ(admin, stack, topic, MCQ.Status.APPROVED);
        q2 = createMCQ(admin, stack, topic, MCQ.Status.APPROVED);

        Quiz q = new Quiz();
        q.setQuizCode("FLOW" + System.nanoTime());
        q.setTitle("Attempt Flow Quiz");
        q.setStack(stack);
        q.setTopic(topic);
        q.setCreatedBy(admin);
        q.setStatus(Quiz.Status.PUBLISHED);
        q.setPublishedAt(LocalDateTime.now());
        q.setDurationMinutes(10);
        quiz = quizRepository.save(q);
        List<QuizQuestion> qqs = new ArrayList<>();
        int order = 1;
        for (MCQ m : List.of(q1, q2)) {
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(quiz);
            qq.setQuestion(m);
            qq.setQuestionOrder(order++);
            qqs.add(qq);
        }
        quizQuestionRepository.saveAll(qqs);
    }

    // ------------------------------------------------------------------ start

    @Test
    @DisplayName("a guest start returns a token and the full duration; starting again resumes the same attempt")
    void guestStartIsIdempotent() throws Exception {
        JsonNode first = start(guest("  Asha   Rao ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.participantName").value("Asha Rao"))
                .andExpect(jsonPath("$.durationSeconds").value(600))
                .andExpect(jsonPath("$.resumed").value(false))
                .andReturn().getResponse().getContentAsString().transform(this::json);
        assertThat(first.get("remainingSeconds").asInt()).isBetween(598, 600);

        backdate(first.get("attemptToken").asText(), 120);

        JsonNode again = json(start(guest("asha rao")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(again.get("attemptToken").asText()).isEqualTo(first.get("attemptToken").asText());
        assertThat(again.get("resumed").asBoolean()).isTrue();
        assertThat(again.get("elapsedSeconds").asInt()).isBetween(120, 122);
        assertThat(again.get("remainingSeconds").asInt()).isBetween(478, 480);
    }

    @Test
    @DisplayName("a guest needs a name to start")
    void guestStartNeedsName() throws Exception {
        start(guest("  ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Participant name is required"));
    }

    @Test
    @DisplayName("an employee start takes identity from the JWT")
    void employeeStartUsesJwt() throws Exception {
        User emp = employee("attempt.flow.emp");
        mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/start")
                        .header("Authorization", bearer(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantName\":\"Someone Else\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantName").value(emp.getFullName()));
    }

    @Test
    @DisplayName("starting a closed quiz is rejected")
    void startClosedQuiz() throws Exception {
        quiz.setStatus(Quiz.Status.CLOSED);
        quizRepository.save(quiz);
        start(guest("Late Guest")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This quiz is closed and cannot accept new attempts"));
    }

    @Test
    @DisplayName("starting again after submitting returns 409")
    void startAfterSubmitConflicts() throws Exception {
        String token = token(start(guest("Done Guest")));
        submit(token, allCorrect(), null).andExpect(status().isOk());
        start(guest("done guest")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A participant with this name has already attempted this quiz"));
    }

    // ------------------------------------------------------------------ submit

    @Test
    @DisplayName("submitting without starting is rejected")
    void submitWithoutToken() throws Exception {
        submit(null, allCorrect(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Start the quiz before submitting answers"));
        submit("00000000-0000-0000-0000-000000000000", allCorrect(), null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("time taken is measured by the server, not the client")
    void serverMeasuresTime() throws Exception {
        String token = token(start(guest("Timed Guest")));
        backdate(token, 125);

        JsonNode result = json(mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "attemptToken", token,
                                "elapsedSeconds", 3,
                                "answers", allCorrect()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(result.get("timeTakenSeconds").asInt()).isBetween(125, 127);
        assertThat(result.get("autoSubmitted").asBoolean()).isFalse();
        assertThat(result.get("score").asInt()).isEqualTo(2);
        assertThat(result.get("participantName").asText()).isEqualTo("Timed Guest");
    }

    @Test
    @DisplayName("a submission after the deadline is capped at the duration and treated as auto-submitted")
    void lateSubmissionIsCapped() throws Exception {
        String token = token(start(guest("Slow Guest")));
        backdate(token, 900);

        submit(token, List.of(answer(q1, "A")), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeTakenSeconds").value(600))
                .andExpect(jsonPath("$.autoSubmitted").value(true))
                .andExpect(jsonPath("$.totalQuestions").value(2));
    }

    @Test
    @DisplayName("a second submit of the same attempt returns 409 and stores nothing")
    void doubleSubmitConflicts() throws Exception {
        String token = token(start(guest("Double Guest")));
        submit(token, allCorrect(), null).andExpect(status().isOk());
        submit(token, allCorrect(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This attempt has already been submitted"));
        assertThat(attemptRepository.findLeaderboardByQuiz(quiz)).hasSize(1);
    }

    @Test
    @DisplayName("an employee attempt can only be submitted with that employee's JWT")
    void employeeAttemptNeedsOwnerJwt() throws Exception {
        User emp = employee("attempt.flow.owner");
        User other = employee("attempt.flow.other");
        String token = token(mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/start")
                .header("Authorization", bearer(emp))));

        submit(token, allCorrect(), null).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/attempt")
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(token, allCorrect(), null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/attempt")
                        .header("Authorization", bearer(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(token, allCorrect(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantEnterpriseId").value(emp.getEnterpriseId()));
    }

    @Test
    @DisplayName("an attempt token only works for the quiz it was issued for")
    void tokenIsBoundToQuiz() throws Exception {
        String token = token(start(guest("Wrong Quiz Guest")));
        Quiz other = new Quiz();
        other.setQuizCode("OTHER" + System.nanoTime());
        other.setTitle("Other");
        other.setStack(quiz.getStack());
        other.setTopic(quiz.getTopic());
        other.setCreatedBy(quiz.getCreatedBy());
        other.setStatus(Quiz.Status.PUBLISHED);
        other.setPublishedAt(LocalDateTime.now());
        other.setDurationMinutes(10);
        other = quizRepository.save(other);

        mockMvc.perform(post("/api/public/quiz/" + other.getQuizCode() + "/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody(token, allCorrect(), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("after submitting, the public quiz reports alreadyAttempted for that guest name")
    void publicQuizSeesAttempt() throws Exception {
        submit(token(start(guest("Seen Guest"))), allCorrect(), null).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/quiz/" + quiz.getQuizCode()).param("participantName", "SEEN guest"))
                .andExpect(jsonPath("$.alreadyAttempted").value(true))
                .andExpect(jsonPath("$.canAttempt").value(false));
    }

    // ------------------------------------------------------------------ helpers

    private User employee(String id) {
        User u = new User();
        u.setEnterpriseId(id);
        u.setPassword("{noop}password123");
        u.setFullName("Employee " + id);
        u.setRole(User.Role.PARTICIPANT);
        u.setActive(true);
        return userRepository.save(u);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());
    }

    private String guest(String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of("participantName", name));
    }

    private ResultActions start(String body) throws Exception {
        return mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String token(ResultActions started) throws Exception {
        return json(started.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("attemptToken").asText();
    }

    private void backdate(String token, int seconds) {
        QuizAttemptSession session = sessionRepository.findById(token).orElseThrow();
        session.setStartedAt(session.getStartedAt().minusSeconds(seconds));
        sessionRepository.saveAndFlush(session);
    }

    private Map<String, Object> answer(MCQ q, String choice) {
        return Map.of("questionId", q.getQuestionId(), "selectedAnswers", List.of(choice));
    }

    private List<Map<String, Object>> allCorrect() {
        return List.of(answer(q1, "A"), answer(q2, "A"));
    }

    private String submitBody(String token, List<Map<String, Object>> answers, Boolean autoSubmitted) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("attemptToken", token);
        body.put("answers", answers);
        body.put("autoSubmitted", autoSubmitted);
        return objectMapper.writeValueAsString(body);
    }

    private ResultActions submit(String token, List<Map<String, Object>> answers, Boolean autoSubmitted) throws Exception {
        return mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/attempt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody(token, answers, autoSubmitted)));
    }

    private JsonNode json(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
