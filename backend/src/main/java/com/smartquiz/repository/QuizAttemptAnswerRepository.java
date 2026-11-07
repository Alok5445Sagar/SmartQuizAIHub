package com.smartquiz.repository;

import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Analytics only need answer counts, so these return grouped rows instead of answer entities. */
public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

    /** Rows of [attemptId, answerCount] for every attempt. */
    @Query("SELECT ans.attempt.attemptId, COUNT(ans) FROM QuizAttemptAnswer ans GROUP BY ans.attempt.attemptId")
    List<Object[]> countPerAttempt();

    @Query("""
        SELECT ans.attempt.attemptId, COUNT(ans) FROM QuizAttemptAnswer ans
        WHERE ans.attempt.quiz = :quiz
        GROUP BY ans.attempt.attemptId
    """)
    List<Object[]> countPerAttemptForQuiz(@Param("quiz") Quiz quiz);

    @Query("""
        SELECT ans.attempt.attemptId, COUNT(ans) FROM QuizAttemptAnswer ans
        WHERE ans.attempt.quiz.topic.topicId = :topicId
        GROUP BY ans.attempt.attemptId
    """)
    List<Object[]> countPerAttemptForTopic(@Param("topicId") Long topicId);

    /**
     * Rows of [questionId, questionStem, stackName, topicName, attempted, incorrect] per answered question.
     * participantType is ALL, GUEST (no enterprise id) or EMPLOYEE.
     */
    @Query("""
        SELECT q.questionId, q.questionStem, s.stackName, t.topicName,
               COUNT(ans), SUM(CASE WHEN ans.correct = false THEN 1 ELSE 0 END)
        FROM QuizAttemptAnswer ans
        JOIN ans.attempt a
        JOIN ans.question q
        LEFT JOIN q.stack s
        LEFT JOIN q.topic t
        WHERE :participantType = 'ALL'
           OR (:participantType = 'GUEST' AND (a.participantEnterpriseId IS NULL OR TRIM(a.participantEnterpriseId) = ''))
           OR (:participantType = 'EMPLOYEE' AND a.participantEnterpriseId IS NOT NULL AND TRIM(a.participantEnterpriseId) <> '')
        GROUP BY q.questionId, q.questionStem, s.stackName, t.topicName
    """)
    List<Object[]> questionStats(@Param("participantType") String participantType);
}