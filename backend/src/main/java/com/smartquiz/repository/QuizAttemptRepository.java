package com.smartquiz.repository;

import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    boolean existsByQuizAndParticipantEnterpriseId(Quiz quiz, String participantEnterpriseId);

    @Query("""
        SELECT COUNT(a) > 0 FROM QuizAttempt a
        WHERE a.quiz = :quiz
        AND a.participantEnterpriseId IS NULL
        AND LOWER(a.participantName) = LOWER(:participantName)
    """)
    boolean existsGuestByQuizAndParticipantName(@Param("quiz") Quiz quiz, @Param("participantName") String participantName);

    @Query("""
        SELECT a FROM QuizAttempt a
        WHERE a.quiz = :quiz
        ORDER BY a.score DESC, COALESCE(a.timeTakenSeconds, 2147483647) ASC, a.submittedAt ASC
    """)
    List<QuizAttempt> findLeaderboardByQuiz(@Param("quiz") Quiz quiz);

    List<QuizAttempt> findByParticipantEnterpriseIdOrderBySubmittedAtDesc(String participantEnterpriseId);

    @Query("""
        SELECT a FROM QuizAttempt a
        WHERE a.participantEnterpriseId IS NULL
        AND LOWER(a.participantName) = LOWER(:participantName)
        ORDER BY a.submittedAt DESC
    """)
    List<QuizAttempt> findGuestAttemptsByParticipantName(@Param("participantName") String participantName);
}
