package com.smartquiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.smartquiz.BaseTest;
import com.smartquiz.dto.QuizDTO;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttempt;
import com.smartquiz.entity.QuizAttemptAnswer;
import com.smartquiz.entity.QuizQuestion;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.QuizAttemptAnswerRepository;
import com.smartquiz.repository.QuizAttemptRepository;
import com.smartquiz.repository.QuizQuestionRepository;
import com.smartquiz.repository.QuizRepository;

/**
 * Pins the numbers analytics report for a small known data set, so moving the counting into SQL
 * cannot change them.
 *
 * Quiz 1 (topic A, questions m1, m2): guest answers m1 right and m2 wrong; the employee answers only
 * m1, wrong (auto-submitted). Quiz 2 (topic B, question m3): the employee answers m3 right.
 */
@WithMockUser(username = "av.admin", roles = "ADMIN")
@DisplayName("Analytics values")
class AnalyticsValuesTest extends BaseTest {

    @Autowired private QuizService quizService;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptAnswerRepository answerRepository;

    private Topic topicA;
    private Topic topicB;
    private Quiz quiz1;
    private MCQ m1;
    private MCQ m2;
    private MCQ m3;

    @BeforeEach
    void setUp() {
        User admin = createAdmin("av.admin");
        TechnologyStack stack = createStack("AV Stack");
        topicA = createTopic("AV Topic A", stack);
        topicB = createTopic("AV Topic B", stack);
        m1 = createMCQ(admin, stack, topicA, MCQ.Status.APPROVED);
        m2 = createMCQ(admin, stack, topicA, MCQ.Status.APPROVED);
        m3 = createMCQ(admin, stack, topicB, MCQ.Status.APPROVED);
        quiz1 = quiz(admin, stack, topicA, "AVQ1", List.of(m1, m2));
        Quiz quiz2 = quiz(admin, stack, topicB, "AVQ2", List.of(m3));

        QuizAttempt guest = attempt(quiz1, "AV Guest", null, 1, 2, false);
        answer(guest, m1, true);
        answer(guest, m2, false);
        QuizAttempt empQ1 = attempt(quiz1, "AV Employee", "av.emp", 0, 2, true);
        answer(empQ1, m1, false);
        QuizAttempt empQ2 = attempt(quiz2, "AV Employee", "av.emp", 1, 1, false);
        answer(empQ2, m3, true);
    }

    @Test
    @DisplayName("question analytics count attempts and wrong answers per question, filtered by participant type")
    void questionAnalytics() {
        assertThat(questionStats("ALL")).containsExactlyInAnyOrderEntriesOf(Map.of(
                m1.getQuestionId(), List.of(2, 1), m2.getQuestionId(), List.of(1, 1), m3.getQuestionId(), List.of(1, 0)));
        assertThat(questionStats("GUEST")).containsExactlyInAnyOrderEntriesOf(Map.of(
                m1.getQuestionId(), List.of(1, 0), m2.getQuestionId(), List.of(1, 1)));
        assertThat(questionStats("EMPLOYEE")).containsExactlyInAnyOrderEntriesOf(Map.of(
                m1.getQuestionId(), List.of(1, 1), m3.getQuestionId(), List.of(1, 0)));
    }

    @Test
    @DisplayName("summary: quizzes conducted, participants and completion rate")
    void summary() {
        QuizDTO.QuizAnalyticsSummary all = quizService.getParticipantTopicAnalytics("ALL").getQuizAnalytics();
        assertThat(all.getTotalQuizzesConducted()).isEqualTo(2);
        assertThat(all.getTotalParticipants()).isEqualTo(2);
        assertThat(all.getCompletionRatePercent()).isEqualTo(66.67);

        QuizDTO.QuizAnalyticsSummary employees = quizService.getParticipantTopicAnalytics("EMPLOYEE").getQuizAnalytics();
        assertThat(employees.getTotalParticipants()).isEqualTo(1);
        assertThat(employees.getCompletionRatePercent()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("hosted topics count quizzes per topic")
    void hostedTopics() {
        Map<Long, Long> hosted = quizService.getHostedTopicsForLeaderboard().stream()
                .collect(Collectors.toMap(QuizDTO.HostedTopicOption::getTopicId, QuizDTO.HostedTopicOption::getQuizzesHosted));
        assertThat(hosted).containsEntry(topicA.getTopicId(), 1L).containsEntry(topicB.getTopicId(), 1L);
    }

    @Test
    @DisplayName("quiz leaderboard: attempted and wrong counts come from stored answers")
    void quizLeaderboard() {
        Map<String, List<Integer>> rows = quizService.getLeaderboard(quiz1.getQuizCode()).getLeaderboard().stream()
                .collect(Collectors.toMap(QuizDTO.LeaderboardEntry::getParticipantName,
                        e -> List.of(e.getAttemptedCount(), e.getCorrectCount(), e.getWrongCount())));
        assertThat(rows).containsExactlyInAnyOrderEntriesOf(Map.of(
                "AV Guest", List.of(2, 1, 1), "AV Employee", List.of(1, 0, 1)));
    }

    @Test
    @DisplayName("overall and topic leaderboards aggregate per participant")
    void participantLeaderboards() {
        Map<String, List<Integer>> overall = quizService.getOverallLeaderboard("ALL").getLeaderboard().stream()
                .collect(Collectors.toMap(QuizDTO.OverallLeaderboardEntry::getParticipantName,
                        e -> List.of(e.getQuizzesPlayed(), e.getTopicsCovered(), e.getAttemptedCount(), e.getCorrectCount(), e.getWrongCount())));
        assertThat(overall).containsExactlyInAnyOrderEntriesOf(Map.of(
                "AV Guest", List.of(1, 1, 2, 1, 1), "AV Employee", List.of(2, 2, 2, 1, 1)));

        Map<String, List<Integer>> topic = quizService.getTopicLeaderboard(topicA.getTopicId(), "EMPLOYEE").getLeaderboard().stream()
                .collect(Collectors.toMap(QuizDTO.TopicLeaderboardEntry::getParticipantName,
                        e -> List.of(e.getQuizzesPlayed(), e.getAttemptedCount(), e.getCorrectCount(), e.getWrongCount())));
        assertThat(topic).containsExactlyInAnyOrderEntriesOf(Map.of("AV Employee", List.of(1, 1, 0, 1)));
    }

    private Map<Long, List<Integer>> questionStats(String type) {
        return quizService.getParticipantTopicAnalytics(type).getQuestionAnalytics().stream()
                .filter(r -> List.of(m1.getQuestionId(), m2.getQuestionId(), m3.getQuestionId()).contains(r.getQuestionId()))
                .collect(Collectors.toMap(QuizDTO.QuestionAnalyticsRow::getQuestionId,
                        r -> List.of(r.getAttemptedCount(), r.getIncorrectCount())));
    }

    private Quiz quiz(User admin, TechnologyStack stack, Topic topic, String code, List<MCQ> questions) {
        Quiz q = new Quiz();
        q.setQuizCode(code + System.nanoTime());
        q.setTitle(code);
        q.setStack(stack);
        q.setTopic(topic);
        q.setCreatedBy(admin);
        q.setStatus(Quiz.Status.PUBLISHED);
        q.setPublishedAt(LocalDateTime.now());
        q.setDurationMinutes(15);
        Quiz saved = quizRepository.save(q);
        int order = 1;
        for (MCQ m : questions) {
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(saved);
            qq.setQuestion(m);
            qq.setQuestionOrder(order++);
            quizQuestionRepository.save(qq);
        }
        return saved;
    }

    private QuizAttempt attempt(Quiz quiz, String name, String enterpriseId, int score, int total, boolean auto) {
        QuizAttempt a = new QuizAttempt();
        a.setQuiz(quiz);
        a.setParticipantName(name);
        a.setParticipantEnterpriseId(enterpriseId);
        a.setScore(score);
        a.setCorrectCount(score);
        a.setTotalQuestions(total);
        a.setTimeTakenSeconds(60);
        a.setAutoSubmitted(auto);
        return attemptRepository.save(a);
    }

    private void answer(QuizAttempt attempt, MCQ question, boolean correct) {
        QuizAttemptAnswer ans = new QuizAttemptAnswer();
        ans.setAttempt(attempt);
        ans.setQuestion(question);
        ans.setSelectedAnswers(correct ? "A" : "B");
        ans.setCorrect(correct);
        answerRepository.save(ans);
    }
}
