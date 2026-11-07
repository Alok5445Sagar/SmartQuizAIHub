package com.smartquiz.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizQuestion;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.QuizQuestionRepository;
import com.smartquiz.repository.QuizRepository;
import com.smartquiz.security.JwtUtils;

import io.micrometer.core.instrument.MeterRegistry;

@AutoConfigureMockMvc
@AutoConfigureObservability
@TestPropertySource(properties = {
        "app.metrics.scrape-token=test-scrape-token-1234567890",
        "spring.ai.ollama.base-url=http://127.0.0.1:1"})
@DisplayName("Observability: actuator endpoints, request ids and metrics")
class ObservabilityEndpointsTest extends BaseTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());
    }

    // ------------------------------------------------------------------ prometheus

    @Test
    @DisplayName("prometheus needs an admin JWT or the scrape token")
    void prometheusIsProtected() throws Exception {
        User admin = createAdmin("obs.admin");
        User sme = createSME("obs.sme", "Obs SME");

        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(sme))).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http_server_requests_seconds")))
                .andExpect(content().string(containsString("smartquiz_ai_circuit_state")))
                .andExpect(content().string(containsString("application=\"smart-quiz-hub\"")));
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer test-scrape-token-1234567890"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the scrape token only opens the prometheus endpoint")
    void scrapeTokenIsScoped() throws Exception {
        mockMvc.perform(get("/api/reference/stacks").header("Authorization", "Bearer test-scrape-token-1234567890"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ health / info

    @Test
    @DisplayName("health shows only the status to anonymous callers and full details to admins")
    void healthDetailsForAdminsOnly() throws Exception {
        User admin = createAdmin("obs.health.admin");
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.ollama.status").exists());
    }

    @Test
    @DisplayName("info is public")
    void infoIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ request ids

    @Test
    @DisplayName("every response carries a request id; a safe incoming id is reused")
    void requestIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "edge-abc_123.4"))
                .andExpect(header().string("X-Request-Id", "edge-abc_123.4"));
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "bad id\r\ninjected"))
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "x".repeat(65)))
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    @DisplayName("error bodies include the request id, including security errors")
    void errorBodiesIncludeRequestId() throws Exception {
        User sme = createSME("obs.err.sme", "Err SME");
        MvcResult notFound = mockMvc.perform(get("/api/public/quiz/NO-SUCH-QUIZ").header("X-Request-Id", "req-404"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(objectMapper.readTree(notFound.getResponse().getContentAsString()).get("requestId").asText())
                .isEqualTo("req-404");

        mockMvc.perform(get("/api/mcq/my-questions").header("X-Request-Id", "req-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.requestId").value("req-401"));
        mockMvc.perform(get("/api/admin/question-bank").header("X-Request-Id", "req-403").header("Authorization", bearer(sme)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.requestId").value("req-403"));
    }

    // ------------------------------------------------------------------ custom metrics

    @Test
    @DisplayName("quiz start, submit and rejected double submit are counted")
    void quizMetrics() throws Exception {
        User admin = createAdmin("obs.quiz.admin");
        TechnologyStack stack = createStack("Obs Stack");
        Topic topic = createTopic("Obs Topic", stack);
        MCQ q = createMCQ(admin, stack, topic, MCQ.Status.APPROVED);
        Quiz quiz = new Quiz();
        quiz.setQuizCode("OBS" + System.nanoTime());
        quiz.setTitle("Obs");
        quiz.setStack(stack);
        quiz.setTopic(topic);
        quiz.setCreatedBy(admin);
        quiz.setStatus(Quiz.Status.PUBLISHED);
        quiz.setPublishedAt(LocalDateTime.now());
        quiz.setDurationMinutes(10);
        quiz = quizRepository.save(quiz);
        QuizQuestion qq = new QuizQuestion();
        qq.setQuiz(quiz);
        qq.setQuestion(q);
        qq.setQuestionOrder(1);
        quizQuestionRepository.save(qq);

        double startedBefore = count("smartquiz.quiz.attempts.started", "resumed", "false");
        double resumedBefore = count("smartquiz.quiz.attempts.started", "resumed", "true");
        double submittedBefore = count("smartquiz.quiz.attempts.submitted", "auto", "false");
        double rejectedBefore = count("smartquiz.quiz.attempts.rejected", "reason", "already_submitted");

        String start = mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/start")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"participantName\":\"Obs Guest\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/start")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"participantName\":\"Obs Guest\"}"))
                .andExpect(status().isOk());
        String token = objectMapper.readTree(start).get("attemptToken").asText();
        String body = objectMapper.writeValueAsString(java.util.Map.of("attemptToken", token,
                "answers", List.of(java.util.Map.of("questionId", q.getQuestionId(), "selectedAnswers", List.of("A")))));
        mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/attempt")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mockMvc.perform(post("/api/public/quiz/" + quiz.getQuizCode() + "/attempt")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());

        assertThat(count("smartquiz.quiz.attempts.started", "resumed", "false") - startedBefore).isEqualTo(1.0);
        assertThat(count("smartquiz.quiz.attempts.started", "resumed", "true") - resumedBefore).isEqualTo(1.0);
        assertThat(count("smartquiz.quiz.attempts.submitted", "auto", "false") - submittedBefore).isEqualTo(1.0);
        assertThat(count("smartquiz.quiz.attempts.rejected", "reason", "already_submitted") - rejectedBefore).isEqualTo(1.0);
    }

    @Test
    @DisplayName("failed logins are counted")
    void loginFailureMetric() throws Exception {
        createSME("obs.login", "Obs Login");
        double before = count("smartquiz.auth.login.failures");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":\"obs.login\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(count("smartquiz.auth.login.failures") - before).isEqualTo(1.0);
    }

    private double count(String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
