package com.smartquiz.service;

import com.smartquiz.BaseTest;
import com.smartquiz.dto.QuizDTO;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttempt;
import com.smartquiz.entity.QuizQuestion;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.MCQRepository;
import com.smartquiz.repository.QuizAttemptRepository;
import com.smartquiz.repository.QuizQuestionRepository;
import com.smartquiz.repository.QuizRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Quiz Service Auto-Pick Tests")
// Service methods are guarded by @PreAuthorize; run as an admin unless a test says otherwise.
@org.springframework.security.test.context.support.WithMockUser(username = "svc.admin", roles = "ADMIN")
public class QuizServiceTest extends BaseTest {

    @Autowired private QuizService quizService;
    @Autowired private MCQRepository mcqRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAttemptRepository quizAttemptRepository;

    @Test
    @DisplayName("Auto-pick prefers unused questions when enough are available")
    void autoPick_prefersUnusedWhenEnoughAvailable() {
        User admin = createAdmin("quiz.auto.admin.1");
        TechnologyStack stack = createStack("Quiz AutoPick Stack 1");
        Topic topic = createTopic("Quiz AutoPick Topic 1", stack);

        // Build 15 unused approved questions with exact 10 MCQ + 5 MSQ and 5/5/5 difficulty mix.
        List<MCQ> unused = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            unused.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, pickDifficulty(i)));
        }
        for (int i = 10; i < 15; i++) {
            unused.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, pickDifficulty(i)));
        }

        // Add used approved questions as noise; algorithm should still pick only unused in this scenario.
        List<MCQ> usedNoise = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            usedNoise.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy));
        }
        markAsUsedInPreviousQuiz(admin, stack, topic, usedNoise);

        QuizDTO.AutoPickResponse response = quizService.autoPickQuestions(
            autoPickRequest(topic.getTopicId(), 15, 10, 5, 5, 5, 5));

        assertThat(response.getSelectedCount()).isEqualTo(15);
        assertThat(response.getUnusedSelectedCount()).isEqualTo(15);
        assertThat(response.getReusedSelectedCount()).isEqualTo(0);

        List<MCQ> selected = mcqRepository.findAllById(response.getQuestionIds());
        assertThat(selected).hasSize(15);
        assertThat(selected.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MCQ).count()).isEqualTo(10);
        assertThat(selected.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MSQ).count()).isEqualTo(5);
        assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Easy).count()).isEqualTo(5);
        assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Medium).count()).isEqualTo(5);
        assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Hard).count()).isEqualTo(5);

        Set<Long> unusedIds = unused.stream().map(MCQ::getQuestionId).collect(Collectors.toSet());
        assertThat(new HashSet<>(response.getQuestionIds())).isSubsetOf(unusedIds);
    }

    @Test
    @DisplayName("Auto-pick reuses previously used questions when unused pool is insufficient")
    void autoPick_reusesUsedWhenUnusedInsufficient() {
        User admin = createAdmin("quiz.auto.admin.2");
        TechnologyStack stack = createStack("Quiz AutoPick Stack 2");
        Topic topic = createTopic("Quiz AutoPick Topic 2", stack);

        List<MCQ> unused = new ArrayList<>();
        List<MCQ> used = new ArrayList<>();

        // Unused set: 12 questions (8 MCQ + 4 MSQ).
        for (int i = 0; i < 8; i++) {
            unused.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, pickDifficulty(i)));
        }
        for (int i = 8; i < 12; i++) {
            unused.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, pickDifficulty(i)));
        }

        // Used set: 3 questions to complete required mix (2 MCQ + 1 MSQ).
        used.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, pickDifficulty(12)));
        used.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, pickDifficulty(13)));
        used.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, pickDifficulty(14)));
        markAsUsedInPreviousQuiz(admin, stack, topic, used);

        QuizDTO.AutoPickResponse response = quizService.autoPickQuestions(
            autoPickRequest(topic.getTopicId(), 15, 10, 5, 5, 5, 5));

        assertThat(response.getSelectedCount()).isEqualTo(15);
        assertThat(response.getReusedSelectedCount()).isGreaterThan(0);
        assertThat(response.getUnusedSelectedCount() + response.getReusedSelectedCount()).isEqualTo(15);

        List<MCQ> selected = mcqRepository.findAllById(response.getQuestionIds());
        assertThat(selected).hasSize(15);
        assertThat(selected.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MCQ).count()).isEqualTo(10);
        assertThat(selected.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MSQ).count()).isEqualTo(5);
        assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Easy).count()).isEqualTo(5);
        assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Medium).count()).isEqualTo(5);
        assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Hard).count()).isEqualTo(5);
    }

        @Test
        @DisplayName("Auto-pick supports variable total and MCQ/MSQ composition")
        void autoPick_supportsVariableComposition() {
            User admin = createAdmin("quiz.auto.admin.3");
            TechnologyStack stack = createStack("Quiz AutoPick Stack 3");
            Topic topic = createTopic("Quiz AutoPick Topic 3", stack);

            for (int i = 0; i < 8; i++) {
                createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, pickDifficulty(i));
            }
            for (int i = 8; i < 12; i++) {
                createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, pickDifficulty(i));
            }

            QuizDTO.AutoPickResponse response = quizService.autoPickQuestions(
                    autoPickRequest(topic.getTopicId(), 12, 8, 4, 4, 4, 4));

            assertThat(response.getSelectedCount()).isEqualTo(12);
            List<MCQ> selected = mcqRepository.findAllById(response.getQuestionIds());
            assertThat(selected).hasSize(12);
            assertThat(selected.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MCQ).count()).isEqualTo(8);
            assertThat(selected.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MSQ).count()).isEqualTo(4);
            assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Easy).count()).isEqualTo(4);
            assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Medium).count()).isEqualTo(4);
            assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Hard).count()).isEqualTo(4);
        }

        @Test
        @DisplayName("Auto-pick falls back to available difficulty when requested bucket is short")
        void autoPick_fallsBackWhenDifficultyShort() {
            User admin = createAdmin("quiz.auto.admin.4");
            TechnologyStack stack = createStack("Quiz AutoPick Stack 4");
            Topic topic = createTopic("Quiz AutoPick Topic 4", stack);

            for (int i = 0; i < 3; i++) {
                createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Medium);
            }
            for (int i = 0; i < 3; i++) {
                createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Hard);
            }

            QuizDTO.AutoPickResponse response = quizService.autoPickQuestions(
                    autoPickRequest(topic.getTopicId(), 6, 3, 3, 4, 1, 1));

            assertThat(response.getSelectedCount()).isEqualTo(6);
            List<MCQ> selected = mcqRepository.findAllById(response.getQuestionIds());
            assertThat(selected).hasSize(6);
            assertThat(selected.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MCQ).count()).isEqualTo(3);
            assertThat(selected.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MSQ).count()).isEqualTo(3);
            assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Easy).count()).isEqualTo(0);
            assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Medium).count()).isEqualTo(3);
            assertThat(selected.stream().filter(q -> q.getDifficulty() == MCQ.Difficulty.Hard).count()).isEqualTo(3);
        }

        @Test
        @DisplayName("Submit attempt fails when not all quiz questions are submitted")
        void submitAttempt_failsWhenQuestionMissing() {
        User admin = createAdmin("quiz.submit.admin.1");
        TechnologyStack stack = createStack("Quiz Submit Stack 1");
        Topic topic = createTopic("Quiz Submit Topic 1", stack);

        MCQ q1 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        MCQ q2 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Medium);
        Quiz quiz = createPublishedQuiz(admin, stack, topic, List.of(q1, q2));

        QuizDTO.SubmitAttemptRequest request = new QuizDTO.SubmitAttemptRequest(
            "Guest User",
            null,
            null,
            List.of(new QuizDTO.AnswerRequest(q1.getQuestionId(), List.of("A"))),
            null,
            false
        );

        assertThatThrownBy(() -> quizService.submitAttempt(quiz.getQuizCode(), request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("All quiz questions must be attempted before submission");
        }

        @Test
        @DisplayName("Submit attempt fails when any question has empty selected answers")
        void submitAttempt_failsWhenAnyAnswerIsEmpty() {
        User admin = createAdmin("quiz.submit.admin.2");
        TechnologyStack stack = createStack("Quiz Submit Stack 2");
        Topic topic = createTopic("Quiz Submit Topic 2", stack);

        MCQ q1 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        MCQ q2 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Hard);
        Quiz quiz = createPublishedQuiz(admin, stack, topic, List.of(q1, q2));

        QuizDTO.SubmitAttemptRequest request = new QuizDTO.SubmitAttemptRequest(
            "Guest User Two",
            null,
            null,
            List.of(
                new QuizDTO.AnswerRequest(q1.getQuestionId(), List.of("A")),
                new QuizDTO.AnswerRequest(q2.getQuestionId(), List.of())
            ),
            null,
            false
        );

        assertThatThrownBy(() -> quizService.submitAttempt(quiz.getQuizCode(), request))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("All quiz questions must be attempted before submission");
        }

    @Test
    @DisplayName("Reopen keeps same quiz and leaderboard")
    void reopenQuiz_keepsSameLeaderboard() {
        User admin = createAdmin("quiz.reopen.admin");
        TechnologyStack stack = createStack("Quiz Reopen Stack");
        Topic topic = createTopic("Quiz Reopen Topic", stack);

        MCQ q1 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        MCQ q2 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Medium);
        Quiz quiz = createPublishedQuiz(admin, stack, topic, List.of(q1, q2));
        quiz.setStatus(Quiz.Status.CLOSED);
        quiz.setClosedAt(LocalDateTime.now());
        Quiz closedQuiz = quizRepository.save(quiz);

        createAttempt(closedQuiz, "User One", null, 2, 2, 2);
        createAttempt(closedQuiz, "User Two", "EMP001", 1, 2, 1);

        QuizDTO.PublishedQuizSummary reopened = quizService.reopenQuiz(closedQuiz.getQuizId());
        QuizDTO.LeaderboardResponse leaderboard = quizService.getLeaderboard(closedQuiz.getQuizCode());

        assertThat(reopened.getQuizId()).isEqualTo(closedQuiz.getQuizId());
        assertThat(reopened.getStatus()).isEqualTo("PUBLISHED");
        assertThat(leaderboard.getTotalParticipants()).isEqualTo(2);
    }

    @Test
    @DisplayName("Rehost creates new quiz with fresh leaderboard")
    void rehostClosedQuiz_createsNewQuizWithFreshLeaderboard() {
        User admin = createAdmin("quiz.rehost.admin");
        TechnologyStack stack = createStack("Quiz Rehost Stack");
        Topic topic = createTopic("Quiz Rehost Topic", stack);

        MCQ q1 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        MCQ q2 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Hard);
        Quiz oldQuiz = createPublishedQuiz(admin, stack, topic, List.of(q1, q2));
        oldQuiz.setStatus(Quiz.Status.CLOSED);
        oldQuiz.setClosedAt(LocalDateTime.now());
        Quiz savedOldQuiz = quizRepository.save(oldQuiz);

        createAttempt(savedOldQuiz, "Old Participant", null, 2, 2, 2);

        QuizDTO.PublishedQuizSummary rehosted = quizService.rehostClosedQuiz(admin.getEnterpriseId(), savedOldQuiz.getQuizId());
        QuizDTO.LeaderboardResponse oldLeaderboard = quizService.getLeaderboard(savedOldQuiz.getQuizCode());
        QuizDTO.LeaderboardResponse newLeaderboard = quizService.getLeaderboard(rehosted.getQuizCode());

        assertThat(rehosted.getQuizId()).isNotEqualTo(savedOldQuiz.getQuizId());
        assertThat(rehosted.getQuizCode()).isNotEqualTo(savedOldQuiz.getQuizCode());
        assertThat(rehosted.getStatus()).isEqualTo("PUBLISHED");
        assertThat(oldLeaderboard.getTotalParticipants()).isEqualTo(1);
        assertThat(newLeaderboard.getTotalParticipants()).isEqualTo(0);
    }

    @Test
    @DisplayName("Leaderboard export returns a valid xlsx workbook")
    void exportLeaderboardExcel_returnsWorkbook() throws Exception {
        User admin = createAdmin("quiz.export.admin");
        TechnologyStack stack = createStack("Quiz Export Stack");
        Topic topic = createTopic("Quiz Export Topic", stack);

        MCQ q1 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        MCQ q2 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Hard);
        Quiz quiz = createPublishedQuiz(admin, stack, topic, List.of(q1, q2));
        createAttempt(quiz, "Excel User", "EMPX1", 2, 2, 2);

        byte[] bytes = quizService.exportLeaderboardExcel(quiz.getQuizId());

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isGreaterThan(0);
            assertThat(workbook.getSheet("Leaderboard")).isNotNull();
            assertThat(workbook.getSheet("Leaderboard").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("Leaderboard").getRow(1).getCell(1).getStringCellValue()).isEqualTo("Excel User");
        }
    }

    @Test
    @DisplayName("Public quiz exposes MSQ required selection count for live hint")
    void getPublicQuiz_includesMsqRequiredSelections() {
        User admin = createAdmin("quiz.public.hint.admin");
        TechnologyStack stack = createStack("Quiz Public Hint Stack");
        Topic topic = createTopic("Quiz Public Hint Topic", stack);

        MCQ mcq = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        MCQ msq = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Medium);
        msq.setCorrectAnswer("A,C,D");
        MCQ savedMsq = mcqRepository.save(msq);

        Quiz quiz = createPublishedQuiz(admin, stack, topic, List.of(mcq, savedMsq));

        QuizDTO.PublicQuizResponse response = quizService.getPublicQuiz(quiz.getQuizCode());

        QuizDTO.ParticipantQuestion mcqQuestion = response.getQuestions().stream()
                .filter(q -> q.getQuestionId().equals(mcq.getQuestionId()))
                .findFirst()
                .orElseThrow();
        QuizDTO.ParticipantQuestion msqQuestion = response.getQuestions().stream()
            .filter(q -> q.getQuestionId().equals(savedMsq.getQuestionId()))
                .findFirst()
                .orElseThrow();

        assertThat(mcqQuestion.getMsqRequiredSelections()).isNull();
        assertThat(msqQuestion.getMsqRequiredSelections()).isEqualTo(3);
    }

    @Test
    @DisplayName("Auto-submitted attempt allows partial answers and captures elapsed time")
    void submitAttempt_autoSubmittedAllowsPartialAnswers() {
        User admin = createAdmin("quiz.submit.auto.admin");
        TechnologyStack stack = createStack("Quiz Submit Auto Stack");
        Topic topic = createTopic("Quiz Submit Auto Topic", stack);

        MCQ q1 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        MCQ q2 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Medium);
        Quiz quiz = createPublishedQuiz(admin, stack, topic, List.of(q1, q2));
        quiz.setDurationMinutes(10);
        quizRepository.save(quiz);

        QuizDTO.SubmitAttemptRequest request = new QuizDTO.SubmitAttemptRequest(
                "Timed Guest",
                null,
                null,
                List.of(new QuizDTO.AnswerRequest(q1.getQuestionId(), List.of("A"))),
                600,
                true
        );

        QuizDTO.AttemptResultResponse response = quizService.submitAttempt(quiz.getQuizCode(), request);

        assertThat(response.isAutoSubmitted()).isTrue();
        assertThat(response.getTimeTakenSeconds()).isEqualTo(600);
        assertThat(response.getTotalQuestions()).isEqualTo(2);
    }

    @Test
    @DisplayName("Leaderboard uses lower completion time as tie-break for equal score")
    void leaderboard_tiebreaksByLowerTimeTaken() {
        User admin = createAdmin("quiz.leaderboard.time.admin");
        TechnologyStack stack = createStack("Quiz Leaderboard Time Stack");
        Topic topic = createTopic("Quiz Leaderboard Time Topic", stack);

        MCQ q1 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        MCQ q2 = createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Hard);
        Quiz quiz = createPublishedQuiz(admin, stack, topic, List.of(q1, q2));

        createAttempt(quiz, "Slow User", null, 2, 2, 2, 180);
        createAttempt(quiz, "Fast User", null, 2, 2, 2, 95);

        QuizDTO.LeaderboardResponse leaderboard = quizService.getLeaderboard(quiz.getQuizCode());

        assertThat(leaderboard.getLeaderboard()).hasSize(2);
        assertThat(leaderboard.getLeaderboard().get(0).getParticipantName()).isEqualTo("Fast User");
        assertThat(leaderboard.getLeaderboard().get(1).getParticipantName()).isEqualTo("Slow User");
    }

    private MCQ createApprovedQuestion(User creator,
                                       TechnologyStack stack,
                                       Topic topic,
                                       MCQ.QuestionType type,
                                       MCQ.Difficulty difficulty) {
        MCQ mcq = createMCQ(creator, stack, topic, MCQ.Status.APPROVED);
        mcq.setQuestionType(type);
        mcq.setDifficulty(difficulty);
        mcq.setQuestionStem(mcq.getQuestionStem() + " " + type.name() + " " + difficulty.name());
        if (type == MCQ.QuestionType.MSQ) {
            mcq.setOptionE("Option E");
            mcq.setCorrectAnswer("A,C");
        } else {
            mcq.setCorrectAnswer("A");
        }
        return mcqRepository.save(mcq);
    }

    private void markAsUsedInPreviousQuiz(User admin, TechnologyStack stack, Topic topic, List<MCQ> questions) {
        Quiz quiz = new Quiz();
        quiz.setQuizCode("USED" + System.nanoTime());
        quiz.setTitle("Used Quiz Marker");
        quiz.setStack(stack);
        quiz.setTopic(topic);
        quiz.setCreatedBy(admin);
        quiz.setStatus(Quiz.Status.CLOSED);
        quiz.setPublishedAt(LocalDateTime.now().minusDays(2));
        quiz.setClosedAt(LocalDateTime.now().minusDays(1));
        Quiz savedQuiz = quizRepository.save(quiz);

        List<QuizQuestion> quizQuestions = new ArrayList<>();
        int order = 1;
        for (MCQ q : questions) {
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(savedQuiz);
            qq.setQuestion(q);
            qq.setQuestionOrder(order++);
            quizQuestions.add(qq);
        }
        quizQuestionRepository.saveAll(quizQuestions);
    }

    private Quiz createPublishedQuiz(User admin,
                                     TechnologyStack stack,
                                     Topic topic,
                                     List<MCQ> questions) {
        Quiz quiz = new Quiz();
        quiz.setQuizCode("LIVE" + System.nanoTime());
        quiz.setTitle("Live Submit Validation Quiz");
        quiz.setStack(stack);
        quiz.setTopic(topic);
        quiz.setCreatedBy(admin);
        quiz.setStatus(Quiz.Status.PUBLISHED);
        quiz.setPublishedAt(LocalDateTime.now());
        Quiz savedQuiz = quizRepository.save(quiz);

        List<QuizQuestion> quizQuestions = new ArrayList<>();
        int order = 1;
        for (MCQ question : questions) {
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(savedQuiz);
            qq.setQuestion(question);
            qq.setQuestionOrder(order++);
            quizQuestions.add(qq);
        }
        quizQuestionRepository.saveAll(quizQuestions);
        return savedQuiz;
    }

    private QuizAttempt createAttempt(Quiz quiz,
                                      String participantName,
                                      String participantEnterpriseId,
                                      int score,
                                      int totalQuestions,
                                      int correctCount) {
        return createAttempt(quiz, participantName, participantEnterpriseId, score, totalQuestions, correctCount, 0);
    }

    private QuizAttempt createAttempt(Quiz quiz,
                                      String participantName,
                                      String participantEnterpriseId,
                                      int score,
                                      int totalQuestions,
                                      int correctCount,
                                      int timeTakenSeconds) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setParticipantName(participantName);
        attempt.setParticipantEnterpriseId(participantEnterpriseId);
        attempt.setScore(score);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setCorrectCount(correctCount);
        attempt.setTimeTakenSeconds(timeTakenSeconds);
        return quizAttemptRepository.save(attempt);
    }

    private MCQ.Difficulty pickDifficulty(int index) {
        int mod = index % 3;
        if (mod == 0) return MCQ.Difficulty.Easy;
        if (mod == 1) return MCQ.Difficulty.Medium;
        return MCQ.Difficulty.Hard;
    }

    private QuizDTO.AutoPickRequest autoPickRequest(Long topicId,
                                                    int totalQuestions,
                                                    int mcqCount,
                                                    int msqCount,
                                                    int easyCount,
                                                    int mediumCount,
                                                    int hardCount) {
        QuizDTO.AutoPickRequest request = new QuizDTO.AutoPickRequest();
        request.setTopicId(topicId);
        request.setTotalQuestions(totalQuestions);
        request.setMcqCount(mcqCount);
        request.setMsqCount(msqCount);
        request.setEasyCount(easyCount);
        request.setMediumCount(mediumCount);
        request.setHardCount(hardCount);
        return request;
    }
}
