-- ============================================================
-- Smart Quiz AI Hub — Full Schema + Seed Data (Updated with MSQ)
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS smart_quiz_hub
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE smart_quiz_hub;

-- ============================================================
-- TABLES
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    enterprise_id VARCHAR(100) NOT NULL PRIMARY KEY,
    full_name     VARCHAR(200) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    role          ENUM('SME','ADMIN') NOT NULL,
    active        TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS technology_stacks (
    stack_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    stack_name VARCHAR(200) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS topics (
    topic_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_name VARCHAR(300) NOT NULL,
    stack_id   BIGINT NOT NULL,
    CONSTRAINT fk_topic_stack FOREIGN KEY (stack_id) REFERENCES technology_stacks(stack_id)
);

CREATE TABLE IF NOT EXISTS user_stack_mapping (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    enterprise_id VARCHAR(100) NOT NULL,
    stack_id      BIGINT NOT NULL,
    CONSTRAINT fk_usm_user  FOREIGN KEY (enterprise_id) REFERENCES users(enterprise_id),
    CONSTRAINT fk_usm_stack FOREIGN KEY (stack_id)      REFERENCES technology_stacks(stack_id)
);

CREATE TABLE IF NOT EXISTS mcqs (
    question_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_stem          TEXT NOT NULL,
    question_type          ENUM('MCQ','MSQ') NOT NULL DEFAULT 'MCQ',
    option_a               VARCHAR(500) NOT NULL,
    option_b               VARCHAR(500) NOT NULL,
    option_c               VARCHAR(500) NOT NULL,
    option_d               VARCHAR(500) NOT NULL,
    option_e               VARCHAR(500) NULL,
    option_f               VARCHAR(500) NULL,
    correct_answer         VARCHAR(20) NOT NULL COMMENT 'MCQ: single letter e.g. A | MSQ: comma-separated e.g. A,C,E',
    difficulty             ENUM('Easy','Medium','Hard') NOT NULL,
    stack_id               BIGINT NOT NULL,
    topic_id               BIGINT NOT NULL,
    creator_enterprise_id  VARCHAR(100) NOT NULL,
    reviewer_enterprise_id VARCHAR(100),
    status                 ENUM('DRAFT','READY_FOR_REVIEW','UNDER_REVIEW','APPROVED','REJECTED') NOT NULL DEFAULT 'DRAFT',
    reviewer_comments      TEXT,
    created_at             DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_mcq_stack    FOREIGN KEY (stack_id)               REFERENCES technology_stacks(stack_id),
    CONSTRAINT fk_mcq_topic    FOREIGN KEY (topic_id)               REFERENCES topics(topic_id),
    CONSTRAINT fk_mcq_creator  FOREIGN KEY (creator_enterprise_id)  REFERENCES users(enterprise_id),
    CONSTRAINT fk_mcq_reviewer FOREIGN KEY (reviewer_enterprise_id) REFERENCES users(enterprise_id)
);

-- ============================================================
-- SEED DATA — Technology Stacks
-- ============================================================

INSERT IGNORE INTO technology_stacks (stack_id, stack_name) VALUES
(1001, 'Spring Cloud'),
(1002, 'Spring Boot'),
(1003, 'Spring Core'),
(1004, 'Spring MVC & REST'),
(1005, 'Spring ORM & Data JPA'),
(1006, 'Core Java');

-- ============================================================
-- SEED DATA — Topics
-- ============================================================

INSERT IGNORE INTO topics (topic_id, topic_name, stack_id) VALUES
-- Spring Cloud (1001)
(1001, 'Introduction to Spring Cloud',                                         1001),
(1002, 'Service Discovery design pattern - Eureka Server & Discovery Client',  1001),
(1003, 'Eureka Heartbeats & Self Preservation',                                1001),
(1004, 'Spring Cloud Loadbalancer',                                            1001),
(1005, 'Spring Cloud OpenFeign',                                               1001),
(1006, 'Resilience4J- Circuit Breaker',                                        1001),
(1007, 'Spring Boot Actuator',                                                 1001),

-- Spring Boot (1002)
(1008, 'Spring Boot Auto-Configuration',    1002),
(1009, 'Spring Boot Starters',              1002),
(1010, 'Embedded Server Configuration',     1002),
(1011, 'Application Properties & Profiles', 1002),
(1012, 'Spring Boot Testing',               1002),

-- Spring Core (1003)
(1013, 'Introduction to Spring Core',       1003),
(1014, 'Dependency Injection & IoC',        1003),
(1015, 'Bean Lifecycle & Scopes',           1003),
(1016, 'Spring AOP',                        1003),

-- Spring MVC & REST (1004)
(1017, 'Introduction to Spring MVC',        1004),
(1018, 'REST Controllers & Mappings',       1004),
(1019, 'Exception Handling in REST',        1004),
(1020, 'Request & Response DTOs',           1004),

-- Spring ORM & Data JPA (1005)
(1021, 'Introduction to Spring Data JPA',   1005),
(1022, 'Entity Relationships',              1005),
(1023, 'JPQL & Native Queries',             1005),
(1024, 'Transactions & Locking',            1005),

-- Core Java (1006)
(1025, 'Introduction to Core Java',           1006),
(1026, 'OOP Concepts',                        1006),
(1027, 'Collections Framework',               1006),
(1028, 'Java 8+ Features (Streams, Lambdas)', 1006);

-- ============================================================
-- SEED DATA — Users
-- Plain text passwords (NoOpPasswordEncoder used in app)
-- ============================================================

INSERT IGNORE INTO users (enterprise_id, full_name, password, role, active) VALUES
('admin',               'System Admin',         'admin123',    'ADMIN', 1),
('gaurav.a.bhola',      'Gaurav Bhola',         'password123', 'SME',   1),
('birendra.kumar.singh','Birendra Kumar Singh', 'password123', 'SME',   1),
('divya.madhanasekar',  'Divya Madhanasekar',   'password123', 'SME',   1),
('swati.avinash.nikam', 'Swati Nikam',          'password123', 'SME',   1),
('indugu.hari.prasad',  'Indugu Hari Prasad',   'password123', 'SME',   1);

-- ============================================================
-- SEED DATA — SME Stack Mappings
-- ============================================================

INSERT IGNORE INTO user_stack_mapping (enterprise_id, stack_id) VALUES
('gaurav.a.bhola',       1001),   -- Spring Cloud
('gaurav.a.bhola',       1002),   -- Spring Boot
('gaurav.a.bhola',       1006),   -- Core Java
('birendra.kumar.singh', 1002),   -- Spring Boot
('birendra.kumar.singh', 1003),   -- Spring Core
('birendra.kumar.singh', 1001),   -- Spring Cloud
('divya.madhanasekar',   1003),   -- Spring Core
('divya.madhanasekar',   1004),   -- Spring MVC & REST
('swati.avinash.nikam',  1004),   -- Spring MVC & REST
('swati.avinash.nikam',  1005),   -- Spring ORM & Data JPA
('indugu.hari.prasad',   1005),   -- Spring ORM & Data JPA
('indugu.hari.prasad',   1006);   -- Core Java

-- ============================================================
-- DONE
-- ============================================================
SELECT 'smart_quiz_hub schema and seed data loaded successfully!' AS status;