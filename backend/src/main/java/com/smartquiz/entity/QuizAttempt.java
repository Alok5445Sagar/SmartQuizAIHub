package com.smartquiz.entity;

import java.time.LocalDateTime;
import java.util.Locale;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long attemptId;

    @ManyToOne
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "participant_name", nullable = false, length = 200)
    private String participantName;

    @Column(name = "participant_enterprise_id", length = 100)
    private String participantEnterpriseId;

    /** One attempt per quiz and participant key (unique in the database). See {@link #participantKey}. */
    @Column(name = "participant_key", nullable = false, length = 220)
    private String participantKey;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "total_questions", nullable = false)
    private Integer totalQuestions;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount;

    @Column(name = "time_taken_seconds", nullable = false)
    private Integer timeTakenSeconds = 0;

    @Column(name = "auto_submitted", nullable = false)
    private boolean autoSubmitted = false;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    /**
     * 'emp:<enterprise id>' for employees, otherwise 'guest:<lower-cased name with single spaces>'.
     * The V5 migration backfills existing rows with the same rule.
     */
    public static String participantKey(String enterpriseId, String participantName) {
        if (enterpriseId != null && !enterpriseId.isBlank()) {
            return "emp:" + enterpriseId.trim();
        }
        String name = participantName == null ? "" : participantName.trim().replaceAll("\\s+", " ");
        return "guest:" + name.toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void defaultParticipantKey() {
        if (participantKey == null) {
            participantKey = participantKey(participantEnterpriseId, participantName);
        }
    }
}