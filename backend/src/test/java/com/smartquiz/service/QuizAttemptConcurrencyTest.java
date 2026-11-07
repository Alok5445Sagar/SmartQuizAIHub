package com.smartquiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smartquiz.BaseTest;
import com.smartquiz.dto.QuizDTO;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizQuestion;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.exception.ConflictException;
import com.smartquiz.repository.QuizAttemptRepository;
import com.smartquiz.repository.QuizQuestionRepository;
import com.smartquiz.repository.QuizRepository;

/** Committed data (no test transaction) so parallel threads see the same rows and real locks apply. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Concurrent quiz attempts")
class QuizAttemptConcurrencyTest extends BaseTest {

    @Autowired private QuizService quizService;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;

    @Test
    @DisplayName("parallel submits of one attempt store exactly one result; parallel starts share one token")
    void parallelSubmitsStoreOneAttempt() throws Exception {
        User admin = createAdmin("race.admin");
        TechnologyStack stack = createStack("Race Stack");
        Topic topic = createTopic("Race Topic", stack);
        MCQ question = createMCQ(admin, stack, topic, MCQ.Status.APPROVED);
        Quiz quiz = new Quiz();
        quiz.setQuizCode("RACE" + System.nanoTime());
        quiz.setTitle("Race Quiz");
        quiz.setStack(stack);
        quiz.setTopic(topic);
        quiz.setCreatedBy(admin);
        quiz.setStatus(Quiz.Status.PUBLISHED);
        quiz.setPublishedAt(LocalDateTime.now());
        quiz.setDurationMinutes(10);
        Quiz saved = quizRepository.save(quiz);
        QuizQuestion qq = new QuizQuestion();
        qq.setQuiz(saved);
        qq.setQuestion(question);
        qq.setQuestionOrder(1);
        quizQuestionRepository.save(qq);

        List<String> tokens = runTogether(4, () -> quizService.startAttempt(saved.getQuizCode(), "Racer", null).getAttemptToken());
        assertThat(tokens).doesNotContainNull();
        assertThat(tokens.stream().distinct()).hasSize(1);

        QuizDTO.SubmitAttemptRequest request = new QuizDTO.SubmitAttemptRequest(
                tokens.get(0), List.of(new QuizDTO.AnswerRequest(question.getQuestionId(), List.of("A"))), false);
        List<String> outcomes = runTogether(4, () -> {
            try {
                quizService.submitAttempt(saved.getQuizCode(), request, null);
                return "ok";
            } catch (ConflictException e) {
                return "conflict";
            }
        });

        assertThat(outcomes).containsOnly("ok", "conflict");
        assertThat(outcomes.stream().filter("ok"::equals)).hasSize(1);
        assertThat(attemptRepository.findLeaderboardByQuiz(saved)).hasSize(1);
    }

    private static List<String> runTogether(int threads, Callable<String> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
