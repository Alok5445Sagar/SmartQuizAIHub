package com.smartquiz.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.smartquiz.BaseTest;

@DisplayName("Flyway migrations")
class FlywayMigrationTest extends BaseTest {

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("V1 and V2 are applied and Hibernate validation passed (context started)")
    void migrationsApplied() {
        List<String> versions = flyway.info().applied().length == 0 ? List.of()
                : java.util.Arrays.stream(flyway.info().applied())
                        .map(m -> m.getVersion().getVersion())
                        .toList();
        assertThat(versions).contains("1", "2");
    }

    @Test
    @DisplayName("Reference data includes every stack and the topics used by the bulk-upload template")
    void referenceDataSeeded() {
        Integer stacks = jdbc.queryForObject("SELECT COUNT(*) FROM technology_stacks", Integer.class);
        assertThat(stacks).isEqualTo(6);
        Integer templateTopics = jdbc.queryForObject(
                "SELECT COUNT(*) FROM topics WHERE topic_name IN ('Spring Boot Auto-Configuration', 'OOP Concepts')",
                Integer.class);
        assertThat(templateTopics).isEqualTo(2);
    }

    @Test
    @DisplayName("Demo users are not created outside the dev profile")
    void noDemoUsersInTestProfile() {
        Integer admins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE enterprise_id = 'admin'", Integer.class);
        assertThat(admins).isZero();
    }
}
