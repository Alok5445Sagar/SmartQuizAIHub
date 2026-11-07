package com.smartquiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import com.smartquiz.BaseTest;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.MCQTranslation;
import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttempt;
import com.smartquiz.entity.QuizAttemptAnswer;
import com.smartquiz.entity.QuizQuestion;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.MCQTranslationRepository;
import com.smartquiz.repository.QuizAttemptAnswerRepository;
import com.smartquiz.repository.QuizAttemptRepository;
import com.smartquiz.repository.QuizQuestionRepository;
import com.smartquiz.repository.QuizRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Read paths must run a fixed number of SQL statements no matter how many quizzes, attempts and answers
 * exist. Each check runs once with a small data set and again after tripling it; the counts must match.
 */
@WithMockUser(username = "qc.admin", roles = "ADMIN")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("Query counts do not grow with data")
class QueryCountRegressionTest extends BaseTest {

    @Autowired private QuizService quizService;
    @Autowired private MCQService mcqService;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptAnswerRepository answerRepository;
    @Autowired private MCQTranslationRepository translationRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User admin;
    private TechnologyStack stack;
    private Topic topicA;
    private Topic topicB;
    private Quiz probeQuiz;
    private int seeded;

    @BeforeEach
    void setUp() {
        admin = createAdmin("qc.admin");
        stack = createStack("QC Stack");
        topicA = createTopic("QC Topic A", stack);
        topicB = createTopic("QC Topic B", stack);
    }

    @Test
    @DisplayName("analytics, leaderboards, quiz lists, public quiz, dashboard and question stats")
    void readPathsHaveConstantQueryCounts() {
        seed(2);
        Map<String, Long> small = measureAll();
        seed(6);
        Map<String, Long> large = measureAll();

        assertThat(large).as("statements per read path (small data: %s)", small).isEqualTo(small);
    }

    @Test
    @DisplayName("a translated public quiz and its submit cost the same with 3 or 9 questions")
    void publicQuizDoesNotGrowWithQuestionCount() {
        Quiz small = quizWithTranslatedQuestions(3);
        Quiz large = quizWithTranslatedQuestions(9);

        long smallCount = count(() -> quizService.getPublicQuiz(small.getQuizCode(), null, "Reader", "es"));
        long largeCount = count(() -> quizService.getPublicQuiz(large.getQuizCode(), null, "Reader", "es"));

        assertThat(largeCount).isEqualTo(smallCount);
    }

    private Quiz quizWithTranslatedQuestions(int questions) {
        Quiz quiz = new Quiz();
        quiz.setQuizCode("QCQ" + questions + "X" + System.nanoTime());
        quiz.setTitle("QC Size " + questions);
        quiz.setStack(stack);
        quiz.setTopic(topicA);
        quiz.setCreatedBy(admin);
        quiz.setStatus(Quiz.Status.PUBLISHED);
        quiz.setPublishedAt(LocalDateTime.now());
        quiz.setDurationMinutes(15);
        quiz = quizRepository.save(quiz);
        for (int i = 0; i < questions; i++) {
            MCQ q = question(i % 2 == 0 ? topicA : topicB);
            MCQTranslation tr = new MCQTranslation();
            tr.setMcq(q);
            tr.setLanguageCode("es");
            tr.setQuestionStem("ES " + q.getQuestionStem());
            tr.setOptionA("a");
            tr.setOptionB("b");
            tr.setOptionC("c");
            tr.setOptionD("d");
            translationRepository.save(tr);
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(quiz);
            qq.setQuestion(q);
            qq.setQuestionOrder(i + 1);
            quizQuestionRepository.save(qq);
        }
        return quiz;
    }

    private Map<String, Long> measureAll() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("participantTopicAnalytics", count(() -> quizService.getParticipantTopicAnalytics("ALL")));
        counts.put("overallLeaderboard", count(() -> quizService.getOverallLeaderboard("ALL")));
        counts.put("topicLeaderboard", count(() -> quizService.getTopicLeaderboard(topicA.getTopicId(), "ALL")));
        counts.put("hostedTopics", count(() -> quizService.getHostedTopicsForLeaderboard()));
        counts.put("publishedQuizzes", count(() -> quizService.getPublishedQuizzes(0, 50)));
        counts.put("quizLeaderboard", count(() -> quizService.getLeaderboard(probeQuiz.getQuizCode())));
        counts.put("publicQuizTranslated", count(() -> quizService.getPublicQuiz(probeQuiz.getQuizCode(), null, "Probe", "es")));
        counts.put("employeeDashboard", count(() -> quizService.getParticipantPortalDashboard("qc.emp", null, "en")));
        counts.put("myQuestionStats", count(() -> mcqService.getMyQuestionStats(admin.getEnterpriseId(), null, null, null)));
        return counts;
    }

    private long count(Supplier<?> call) {
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        call.get();
        return stats.getPrepareStatementCount();
    }

    /** Adds {@code quizzes} quizzes; each has 3 questions over two topics, a guest and an employee attempt. */
    private void seed(int quizzes) {
        for (int i = 0; i < quizzes; i++) {
            int n = seeded++;
            MCQ q1 = question(topicA);
            MCQ q2 = question(topicA);
            MCQ q3 = question(topicB);
            for (MCQ q : List.of(q1, q2, q3)) {
                MCQTranslation tr = new MCQTranslation();
                tr.setMcq(q);
                tr.setLanguageCode("es");
                tr.setQuestionStem("ES " + q.getQuestionStem());
                tr.setOptionA("a");
                tr.setOptionB("b");
                tr.setOptionC("c");
                tr.setOptionD("d");
                tr.setTranslatedBy(admin);
                translationRepository.save(tr);
            }

            Quiz quiz = new Quiz();
            quiz.setQuizCode("QC" + n + "X" + System.nanoTime());
            quiz.setTitle("QC Quiz " + n);
            quiz.setStack(stack);
            quiz.setTopic(n % 2 == 0 ? topicA : topicB);
            quiz.setCreatedBy(admin);
            quiz.setStatus(Quiz.Status.PUBLISHED);
            quiz.setPublishedAt(LocalDateTime.now());
            quiz.setDurationMinutes(15);
            quiz = quizRepository.save(quiz);
            if (probeQuiz == null) {
                probeQuiz = quiz;
            }
            int order = 1;
            for (MCQ q : List.of(q1, q2, q3)) {
                QuizQuestion qq = new QuizQuestion();
                qq.setQuiz(quiz);
                qq.setQuestion(q);
                qq.setQuestionOrder(order++);
                quizQuestionRepository.save(qq);
            }
            attempt(quiz, "QC Guest " + n, null, List.of(q1, q2, q3));
            attempt(quiz, "QC Employee", "qc.emp", List.of(q1, q2, q3));
            if (quiz == probeQuiz) {
                continue;
            }
            // Extra attempts on the probe quiz make its leaderboard grow too.
            attempt(probeQuiz, "QC Probe Guest " + n, null, List.of());
        }
    }

    private MCQ question(Topic topic) {
        return createMCQ(admin, stack, topic, MCQ.Status.APPROVED);
    }

    private void attempt(Quiz quiz, String name, String enterpriseId, List<MCQ> answered) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setParticipantName(name);
        attempt.setParticipantEnterpriseId(enterpriseId);
        attempt.setScore(answered.size() > 0 ? 2 : 0);
        attempt.setTotalQuestions(3);
        attempt.setCorrectCount(answered.size() > 0 ? 2 : 0);
        attempt.setTimeTakenSeconds(60);
        attempt.setAutoSubmitted(answered.isEmpty());
        attempt = attemptRepository.save(attempt);
        boolean correct = true;
        for (MCQ q : answered) {
            QuizAttemptAnswer answer = new QuizAttemptAnswer();
            answer.setAttempt(attempt);
            answer.setQuestion(q);
            answer.setSelectedAnswers("A");
            answer.setCorrect(correct);
            correct = !correct;
            answerRepository.save(answer);
        }
    }
}
