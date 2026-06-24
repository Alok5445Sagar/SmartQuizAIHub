package com.smartquiz.integration;

import com.smartquiz.BaseTest;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.MCQTranslation;
import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttempt;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.MCQTranslationRepository;
import com.smartquiz.repository.QuizAttemptRepository;
import com.smartquiz.repository.QuizRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Language Workflow Smoke Tests")
class LanguageWorkflowSmokeTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MCQTranslationRepository mcqTranslationRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAttemptRepository quizAttemptRepository;

    @Test
    @WithMockUser(username = "lang.sme.user", roles = "SME")
    @DisplayName("PUT /api/auth/language persists and returns preferred language")
    void updateLanguage_persistsPreferredLanguage() throws Exception {
        User sme = createSME("lang.sme.user", "Language SME");
        sme.setPreferredLanguage("en");
        userRepository.save(sme);

        mockMvc.perform(put("/api/auth/language")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLanguage\":\"fr\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enterpriseId").value("lang.sme.user"))
            .andExpect(jsonPath("$.preferredLanguage").value("fr"));

        User updated = userRepository.findByEnterpriseId("lang.sme.user").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getPreferredLanguage()).isEqualTo("fr");
    }

    @Test
    @WithMockUser(username = "lang.viewer", roles = "SME")
    @DisplayName("GET /api/mcq/{id}?languageCode=es returns translated fields")
    void mcqById_returnsTranslatedFieldsForLanguageCode() throws Exception {
        User creator = createSME("lang.creator", "Language Creator");
        createSME("lang.viewer", "Language Viewer");
        TechnologyStack stack = createStack("Lang Smoke Stack");
        Topic topic = createTopic("Lang Smoke Topic", stack);

        MCQ mcq = createMCQ(creator, stack, topic, MCQ.Status.APPROVED);
        mcq.setQuestionStem("Original English stem");
        mcq.setOptionA("English A");
        mcq.setOptionB("English B");
        mcq.setOptionC("English C");
        mcq.setOptionD("English D");
        mcq = mcqRepository.save(mcq);

        MCQTranslation es = new MCQTranslation();
        es.setMcq(mcq);
        es.setLanguageCode("es");
        es.setQuestionStem("Enunciado traducido al espanol");
        es.setOptionA("Espanol A");
        es.setOptionB("Espanol B");
        es.setOptionC("Espanol C");
        es.setOptionD("Espanol D");
        es.setTranslatedBy(creator);
        mcqTranslationRepository.save(es);

        mockMvc.perform(get("/api/mcq/{id}", mcq.getQuestionId())
                .param("languageCode", "es"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionStem").value("Enunciado traducido al espanol"))
            .andExpect(jsonPath("$.optionA").value("Espanol A"));
    }

    @Test
    @DisplayName("GET /api/public/quiz/participant/dashboard localizes suggestion by language")
    void participantDashboard_returnsLocalizedSuggestionMessage() throws Exception {
        User admin = createAdmin("lang.admin");
        TechnologyStack stack = createStack("Lang Participant Stack");
        Topic topic = createTopic("Lang Participant Topic", stack);

        Quiz quiz = new Quiz();
        quiz.setQuizCode("LANGSMOKE1");
        quiz.setTitle("Language Participant Quiz");
        quiz.setStack(stack);
        quiz.setTopic(topic);
        quiz.setCreatedBy(admin);
        quiz.setStatus(Quiz.Status.PUBLISHED);
        quiz.setPublishedAt(LocalDateTime.now());
        quiz.setDurationMinutes(15);
        quiz = quizRepository.save(quiz);

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setParticipantName("Guest Uno");
        attempt.setParticipantEnterpriseId(null);
        attempt.setScore(1);
        attempt.setTotalQuestions(5);
        attempt.setCorrectCount(1);
        attempt.setTimeTakenSeconds(120);
        quizAttemptRepository.save(attempt);

        mockMvc.perform(get("/api/public/quiz/participant/dashboard")
                .param("participantName", "Guest Uno")
                .param("languageCode", "es"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestions[0].message", containsString("Se recomienda encarecidamente")));
    }

    @Test
    @WithMockUser(username = "lang.admin.viewer", roles = "ADMIN")
    @DisplayName("GET /api/admin/question-bank with languageCode returns translated row values")
    void adminQuestionBank_returnsTranslatedValuesForLanguageCode() throws Exception {
        createAdmin("lang.admin.viewer");
        User creator = createSME("lang.admin.creator", "Admin Language Creator");
        TechnologyStack stack = createStack("Lang Admin Stack");
        Topic topic = createTopic("Lang Admin Topic", stack);

        MCQ mcq = createMCQ(creator, stack, topic, MCQ.Status.APPROVED);
        mcq.setQuestionStem("Admin English stem");
        mcq.setOptionA("Admin English A");
        mcq = mcqRepository.save(mcq);

        MCQTranslation es = new MCQTranslation();
        es.setMcq(mcq);
        es.setLanguageCode("es");
        es.setQuestionStem("Admin enunciado traducido");
        es.setOptionA("Admin Espanol A");
        es.setOptionB("Admin Espanol B");
        es.setOptionC("Admin Espanol C");
        es.setOptionD("Admin Espanol D");
        es.setTranslatedBy(creator);
        mcqTranslationRepository.save(es);

        mockMvc.perform(get("/api/admin/question-bank")
                .param("page", "0")
                .param("size", "20")
                .param("stackId", String.valueOf(stack.getStackId()))
                .param("topicId", String.valueOf(topic.getTopicId()))
                .param("status", "APPROVED")
                .param("languageCode", "es"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.questionId==" + mcq.getQuestionId() + ")].questionStem", hasItem("Admin enunciado traducido")))
            .andExpect(jsonPath("$.content[?(@.questionId==" + mcq.getQuestionId() + ")].optionA", hasItem("Admin Espanol A")));
    }
}
