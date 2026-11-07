package com.smartquiz.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A started quiz attempt. The server records when it started so time taken cannot be faked by the
 * client; the random token is required to submit, and each participant gets one session per quiz.
 */
@Entity
@Table(name = "quiz_attempt_sessions")
@Data
@NoArgsConstructor
public class QuizAttemptSession {

    @Id
    @Column(name = "attempt_token", length = 36, columnDefinition = "CHAR(36)")
    private String attemptToken;

    @ManyToOne
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "participant_key", nullable = false, length = 220)
    private String participantKey;

    @Column(name = "participant_name", nullable = false, length = 200)
    private String participantName;

    @Column(name = "participant_enterprise_id", length = 100)
    private String participantEnterpriseId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
}
