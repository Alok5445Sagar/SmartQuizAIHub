package com.smartquiz.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttemptSession;

import jakarta.persistence.LockModeType;

public interface QuizAttemptSessionRepository extends JpaRepository<QuizAttemptSession, String> {

    Optional<QuizAttemptSession> findByQuizAndParticipantKey(Quiz quiz, String participantKey);

    /** Row-locks the session so two concurrent submits of the same attempt are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM QuizAttemptSession s WHERE s.attemptToken = :token")
    Optional<QuizAttemptSession> lockByToken(@Param("token") String token);
}
