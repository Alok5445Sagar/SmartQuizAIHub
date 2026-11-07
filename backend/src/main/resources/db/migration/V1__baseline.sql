-- Smart Quiz AI Hub baseline schema (MySQL 8). Mirrors the JPA entities.
-- CREATE TABLE IF NOT EXISTS lets an existing pre-Flyway database be baselined safely.

CREATE TABLE IF NOT EXISTS users (
    enterprise_id      VARCHAR(100) NOT NULL PRIMARY KEY,
    password           VARCHAR(255) NOT NULL,
    full_name          VARCHAR(255) NOT NULL,
    role               ENUM('SME','ADMIN','PARTICIPANT') NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'en'
);

CREATE TABLE IF NOT EXISTS technology_stacks (
    stack_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    stack_name VARCHAR(255) NOT NULL,
    CONSTRAINT uq_stack_name UNIQUE (stack_name)
);

CREATE TABLE IF NOT EXISTS topics (
    topic_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_name VARCHAR(255) NOT NULL,
    stack_id   BIGINT NOT NULL,
    CONSTRAINT fk_topic_stack FOREIGN KEY (stack_id) REFERENCES technology_stacks (stack_id)
);

CREATE TABLE IF NOT EXISTS user_stack_mapping (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id VARCHAR(100) NOT NULL,
    stack_id      BIGINT NOT NULL,
    CONSTRAINT fk_usm_user  FOREIGN KEY (enterprise_id) REFERENCES users (enterprise_id),
    CONSTRAINT fk_usm_stack FOREIGN KEY (stack_id) REFERENCES technology_stacks (stack_id)
);

CREATE TABLE IF NOT EXISTS mcqs (
    question_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_stem          TEXT NOT NULL,
    question_type          ENUM('MCQ','MSQ') NOT NULL,
    option_a               VARCHAR(255) NOT NULL,
    option_b               VARCHAR(255) NOT NULL,
    option_c               VARCHAR(255) NOT NULL,
    option_d               VARCHAR(255) NOT NULL,
    option_e               VARCHAR(255),
    option_f               VARCHAR(255),
    is_ai_generated        BOOLEAN NOT NULL DEFAULT FALSE,
    is_bulk_uploaded       BOOLEAN NOT NULL DEFAULT FALSE,
    correct_answer         VARCHAR(255) NOT NULL,
    difficulty             ENUM('Easy','Medium','Hard') NOT NULL,
    stack_id               BIGINT NOT NULL,
    topic_id               BIGINT NOT NULL,
    creator_enterprise_id  VARCHAR(100) NOT NULL,
    reviewer_enterprise_id VARCHAR(100),
    status                 ENUM('DRAFT','READY_FOR_REVIEW','UNDER_REVIEW','APPROVED','REJECTED') NOT NULL,
    reviewer_comments      TEXT,
    created_at             DATETIME(6),
    updated_at             DATETIME(6),
    CONSTRAINT fk_mcq_stack    FOREIGN KEY (stack_id) REFERENCES technology_stacks (stack_id),
    CONSTRAINT fk_mcq_topic    FOREIGN KEY (topic_id) REFERENCES topics (topic_id),
    CONSTRAINT fk_mcq_creator  FOREIGN KEY (creator_enterprise_id) REFERENCES users (enterprise_id),
    CONSTRAINT fk_mcq_reviewer FOREIGN KEY (reviewer_enterprise_id) REFERENCES users (enterprise_id),
    INDEX idx_mcq_status_stack_topic (status, stack_id, topic_id),
    INDEX idx_mcq_reviewer_status (reviewer_enterprise_id, status)
);

CREATE TABLE IF NOT EXISTS mcq_translations (
    translation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id    BIGINT NOT NULL,
    language_code  VARCHAR(10) NOT NULL,
    question_stem  TEXT NOT NULL,
    option_a       VARCHAR(255) NOT NULL,
    option_b       VARCHAR(255) NOT NULL,
    option_c       VARCHAR(255) NOT NULL,
    option_d       VARCHAR(255) NOT NULL,
    option_e       VARCHAR(255),
    option_f       VARCHAR(255),
    translated_by  VARCHAR(100),
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    CONSTRAINT uq_mcq_translation_lang UNIQUE (question_id, language_code),
    CONSTRAINT fk_translation_mcq  FOREIGN KEY (question_id) REFERENCES mcqs (question_id) ON DELETE CASCADE,
    CONSTRAINT fk_translation_user FOREIGN KEY (translated_by) REFERENCES users (enterprise_id)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    chat_message_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id   VARCHAR(100) NOT NULL,
    role            ENUM('USER','ASSISTANT') NOT NULL,
    content         TEXT NOT NULL,
    session_id      VARCHAR(100),
    created_at      DATETIME(6),
    CONSTRAINT fk_chat_message_user FOREIGN KEY (enterprise_id) REFERENCES users (enterprise_id),
    INDEX idx_chat_user_session (enterprise_id, session_id, created_at)
);

CREATE TABLE IF NOT EXISTS participant_direct_messages (
    message_id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_enterprise_id    VARCHAR(100) NOT NULL,
    recipient_enterprise_id VARCHAR(100) NOT NULL,
    message_text            TEXT,
    attachment_name         VARCHAR(255),
    attachment_content_type VARCHAR(120),
    attachment_size_bytes   BIGINT,
    attachment_storage_path VARCHAR(512),
    created_at              DATETIME(6) NOT NULL,
    delivered_at            DATETIME(6),
    read_at                 DATETIME(6),
    INDEX idx_pdm_conversation (sender_enterprise_id, recipient_enterprise_id, created_at, message_id),
    INDEX idx_pdm_read_state (sender_enterprise_id, recipient_enterprise_id, read_at, message_id)
);

CREATE TABLE IF NOT EXISTS quizzes (
    quiz_id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_code                VARCHAR(32) NOT NULL,
    title                    VARCHAR(300) NOT NULL,
    stack_id                 BIGINT NOT NULL,
    topic_id                 BIGINT NOT NULL,
    created_by_enterprise_id VARCHAR(100) NOT NULL,
    status                   ENUM('PUBLISHED','CLOSED') NOT NULL,
    published_at             DATETIME(6) NOT NULL,
    closed_at                DATETIME(6),
    duration_minutes         INT NOT NULL,
    created_at               DATETIME(6),
    updated_at               DATETIME(6),
    CONSTRAINT uq_quiz_code UNIQUE (quiz_code),
    CONSTRAINT fk_quiz_stack   FOREIGN KEY (stack_id) REFERENCES technology_stacks (stack_id),
    CONSTRAINT fk_quiz_topic   FOREIGN KEY (topic_id) REFERENCES topics (topic_id),
    CONSTRAINT fk_quiz_creator FOREIGN KEY (created_by_enterprise_id) REFERENCES users (enterprise_id),
    INDEX idx_quiz_status_published (status, published_at)
);

CREATE TABLE IF NOT EXISTS quiz_questions (
    quiz_question_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_id          BIGINT NOT NULL,
    question_id      BIGINT NOT NULL,
    question_order   INT NOT NULL,
    CONSTRAINT uq_quiz_question UNIQUE (quiz_id, question_id),
    CONSTRAINT fk_qq_quiz     FOREIGN KEY (quiz_id) REFERENCES quizzes (quiz_id),
    CONSTRAINT fk_qq_question FOREIGN KEY (question_id) REFERENCES mcqs (question_id)
);

CREATE TABLE IF NOT EXISTS quiz_attempts (
    attempt_id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_id                   BIGINT NOT NULL,
    participant_name          VARCHAR(200) NOT NULL,
    participant_enterprise_id VARCHAR(100),
    score                     INT NOT NULL,
    total_questions           INT NOT NULL,
    correct_count             INT NOT NULL,
    time_taken_seconds        INT NOT NULL,
    auto_submitted            BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at              DATETIME(6) NOT NULL,
    CONSTRAINT fk_attempt_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (quiz_id),
    INDEX idx_attempt_quiz (quiz_id),
    INDEX idx_attempt_participant (participant_enterprise_id)
);

CREATE TABLE IF NOT EXISTS quiz_attempt_answers (
    attempt_answer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_id        BIGINT NOT NULL,
    question_id       BIGINT NOT NULL,
    selected_answers  VARCHAR(30) NOT NULL,
    correct           BOOLEAN NOT NULL,
    CONSTRAINT fk_answer_attempt  FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (attempt_id),
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES mcqs (question_id)
);
