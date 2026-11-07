package com.smartquiz.repository;

import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    boolean existsByQuizAndParticipantKey(Quiz quiz, String participantKey);

    @EntityGraph(attributePaths = {"quiz", "quiz.stack", "quiz.topic", "quiz.topic.stack", "quiz.createdBy"})
    @Query("""
        SELECT a FROM QuizAttempt a
        WHERE a.quiz = :quiz
        ORDER BY a.score DESC, COALESCE(a.timeTakenSeconds, 2147483647) ASC, a.submittedAt ASC
    """)
    List<QuizAttempt> findLeaderboardByQuiz(@Param("quiz") Quiz quiz);

    @EntityGraph(attributePaths = {"quiz", "quiz.stack", "quiz.topic", "quiz.topic.stack", "quiz.createdBy"})
    @Query("SELECT a FROM QuizAttempt a")
    List<QuizAttempt> findAllWithQuiz();

    @EntityGraph(attributePaths = {"quiz", "quiz.stack", "quiz.topic", "quiz.topic.stack", "quiz.createdBy"})
    @Query("SELECT a FROM QuizAttempt a WHERE a.quiz.topic.topicId = :topicId")
    List<QuizAttempt> findByQuizTopicIdWithQuiz(@Param("topicId") Long topicId);

    @EntityGraph(attributePaths = {"quiz", "quiz.stack", "quiz.topic", "quiz.topic.stack", "quiz.createdBy"})
    List<QuizAttempt> findByParticipantEnterpriseIdOrderBySubmittedAtDesc(String participantEnterpriseId);

    @EntityGraph(attributePaths = {"quiz", "quiz.stack", "quiz.topic", "quiz.topic.stack", "quiz.createdBy"})
    @Query("""
        SELECT a FROM QuizAttempt a
        WHERE a.participantEnterpriseId IS NULL
        AND LOWER(a.participantName) = LOWER(:participantName)
        ORDER BY a.submittedAt DESC
    """)
    List<QuizAttempt> findGuestAttemptsByParticipantName(@Param("participantName") String participantName);
}