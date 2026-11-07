package com.smartquiz.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One background AI question-generation request and its outcome. Only the worker thread updates a running job. */
@Entity
@Table(name = "ai_generation_jobs")
@Data
@NoArgsConstructor
public class AiGenerationJob {

    @Id
    @Column(name = "job_id", length = 36, columnDefinition = "CHAR(36)")
    private String jobId;

    @Column(name = "enterprise_id", nullable = false, length = 100)
    private String enterpriseId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "stack_id", nullable = false)
    private Long stackId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(nullable = false, length = 10)
    private String difficulty;

    @Column(name = "requested_mcq", nullable = false)
    private int requestedMcq;

    @Column(name = "requested_msq", nullable = false)
    private int requestedMsq;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "question_ids", columnDefinition = "TEXT")
    private String questionIds;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public enum Status {
        QUEUED, RUNNING, SUCCEEDED, FAILED
    }
}
