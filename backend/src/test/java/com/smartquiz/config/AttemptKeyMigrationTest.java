package com.smartquiz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smartquiz.BaseTest;

/**
 * Rebuilds the schema up to V4, inserts attempts the way the old code could store them (including
 * duplicates), then applies the remaining migrations. The next test class gets a fresh schema.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("V5 participant-key migration")
class AttemptKeyMigrationTest extends BaseTest {

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("backfills participant keys, keeps the earliest of duplicate attempts, then enforces uniqueness")
    void backfillsAndDeduplicates() {
        flyway.clean();
        Flyway.configure().configuration(flyway.getConfiguration()).target("4").load().migrate();

        jdbc.update("INSERT INTO users (enterprise_id, full_name, password, role, active, preferred_language) "
                + "VALUES ('mig.admin', 'Mig Admin', '{noop}x', 'ADMIN', TRUE, 'en')");
        Long stackId = jdbc.queryForObject("SELECT MIN(stack_id) FROM technology_stacks", Long.class);
        Long topicId = jdbc.queryForObject("SELECT MIN(topic_id) FROM topics WHERE stack_id = ?", Long.class, stackId);
        jdbc.update("INSERT INTO quizzes (quiz_code, title, stack_id, topic_id, created_by_enterprise_id, status, "
                + "published_at, duration_minutes) VALUES ('MIGQ', 'Mig', ?, ?, 'mig.admin', 'PUBLISHED', NOW(6), 15)",
                stackId, topicId);
        Long quizId = jdbc.queryForObject("SELECT quiz_id FROM quizzes WHERE quiz_code = 'MIGQ'", Long.class);

        long guestFirst = insertAttempt(quizId, "Asha  Rao", null);
        long guestDup = insertAttempt(quizId, " asha rao ", null);
        long empFirst = insertAttempt(quizId, "Emp One", "emp.1");
        long empDup = insertAttempt(quizId, "Emp One", "emp.1");
        long otherGuest = insertAttempt(quizId, "Ben", null);

        flyway.migrate();

        Map<Long, String> keys = new java.util.HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT attempt_id, participant_key FROM quiz_attempts")) {
            keys.put(((Number) row.get("attempt_id")).longValue(), (String) row.get("participant_key"));
        }
        assertThat(keys.get(guestFirst)).isEqualTo("guest:asha rao");
        assertThat(keys.get(empFirst)).isEqualTo("emp:emp.1");
        assertThat(keys.get(otherGuest)).isEqualTo("guest:ben");
        assertThat(keys.get(guestDup)).isEqualTo("guest:asha rao#dup" + guestDup);
        assertThat(keys.get(empDup)).isEqualTo("emp:emp.1#dup" + empDup);

        assertThatThrownBy(() -> insertAttemptWithKey(quizId, "Ben Again", "guest:ben"))
                .isInstanceOf(DataIntegrityViolationException.class);

        List<String> versions = java.util.Arrays.stream(flyway.info().applied())
                .map(m -> m.getVersion().getVersion()).toList();
        assertThat(versions).contains("5");
    }

    private long insertAttempt(Long quizId, String name, String enterpriseId) {
        jdbc.update("INSERT INTO quiz_attempts (quiz_id, participant_name, participant_enterprise_id, score, "
                + "total_questions, correct_count, time_taken_seconds, auto_submitted, submitted_at) "
                + "VALUES (?, ?, ?, 1, 2, 1, 30, FALSE, NOW(6))", quizId, name, enterpriseId);
        return jdbc.queryForObject("SELECT MAX(attempt_id) FROM quiz_attempts", Long.class);
    }

    private void insertAttemptWithKey(Long quizId, String name, String key) {
        jdbc.update("INSERT INTO quiz_attempts (quiz_id, participant_name, participant_key, score, total_questions, "
                + "correct_count, time_taken_seconds, auto_submitted, submitted_at) "
                + "VALUES (?, ?, ?, 1, 2, 1, 30, FALSE, NOW(6))", quizId, name, key);
    }
}
