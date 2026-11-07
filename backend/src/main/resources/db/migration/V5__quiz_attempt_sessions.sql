-- Server-side quiz timing and one attempt per participant.
--
-- participant_key identifies who took a quiz: 'emp:<enterprise id>' for employees, or
-- 'guest:<lower-cased name with single spaces>' for guests. It must match QuizAttempt.participantKey().

-- 1. Attempt sessions: created by POST /api/public/quiz/{code}/start; the token is required to submit.
CREATE TABLE IF NOT EXISTS quiz_attempt_sessions (
    attempt_token             CHAR(36) PRIMARY KEY,
    quiz_id                   BIGINT NOT NULL,
    participant_key           VARCHAR(220) NOT NULL,
    participant_name          VARCHAR(200) NOT NULL,
    participant_enterprise_id VARCHAR(100),
    started_at                DATETIME(6) NOT NULL,
    submitted_at              DATETIME(6),
    CONSTRAINT uq_attempt_session_participant UNIQUE (quiz_id, participant_key),
    CONSTRAINT fk_attempt_session_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (quiz_id)
);

-- 2. Backfill a participant key on existing attempts.
ALTER TABLE quiz_attempts ADD COLUMN participant_key VARCHAR(220) NULL AFTER participant_enterprise_id;

UPDATE quiz_attempts
SET participant_key = CASE
        WHEN participant_enterprise_id IS NOT NULL AND TRIM(participant_enterprise_id) <> ''
            THEN CONCAT('emp:', TRIM(participant_enterprise_id))
        ELSE CONCAT('guest:', LOWER(REGEXP_REPLACE(TRIM(participant_name), '[[:space:]]+', ' ')))
    END;

-- 3. The old code could store the same participant twice (double submit). Keep the earliest attempt as
--    the real one and give later copies a distinct key so the unique constraint can be added.
UPDATE quiz_attempts a
JOIN (
    SELECT quiz_id, participant_key, MIN(attempt_id) AS keep_id
    FROM quiz_attempts
    GROUP BY quiz_id, participant_key
    HAVING COUNT(*) > 1
) d ON a.quiz_id = d.quiz_id AND a.participant_key = d.participant_key AND a.attempt_id <> d.keep_id
SET a.participant_key = CONCAT(a.participant_key, '#dup', a.attempt_id);

ALTER TABLE quiz_attempts
    MODIFY participant_key VARCHAR(220) NOT NULL,
    ADD CONSTRAINT uq_attempt_participant UNIQUE (quiz_id, participant_key);
