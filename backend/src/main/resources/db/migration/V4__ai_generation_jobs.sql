-- Background AI question-generation jobs (POST /api/ai/generate returns 202 + jobId).
-- question_ids holds the comma-separated ids of the saved draft questions once the job succeeds.
CREATE TABLE IF NOT EXISTS ai_generation_jobs (
    job_id          CHAR(36) PRIMARY KEY,
    enterprise_id   VARCHAR(100) NOT NULL,
    status          ENUM('QUEUED','RUNNING','SUCCEEDED','FAILED') NOT NULL,
    stack_id        BIGINT NOT NULL,
    topic_id        BIGINT NOT NULL,
    difficulty      VARCHAR(10) NOT NULL,
    requested_mcq   INT NOT NULL,
    requested_msq   INT NOT NULL,
    generated_count INT NOT NULL DEFAULT 0,
    question_ids    TEXT,
    error_message   VARCHAR(500),
    created_at      DATETIME(6) NOT NULL,
    started_at      DATETIME(6),
    finished_at     DATETIME(6),
    CONSTRAINT fk_ai_job_user FOREIGN KEY (enterprise_id) REFERENCES users (enterprise_id) ON DELETE CASCADE,
    INDEX idx_ai_job_owner_status (enterprise_id, status),
    INDEX idx_ai_job_status_created (status, created_at)
);
