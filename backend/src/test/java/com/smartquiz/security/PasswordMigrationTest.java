package com.smartquiz.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.smartquiz.BaseTest;
import com.smartquiz.entity.User;

@AutoConfigureMockMvc
@DisplayName("Password hashing and migration")
class PasswordMigrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private String loginBody(String id, String password) {
        return "{\"enterpriseId\":\"" + id + "\",\"password\":\"" + password + "\"}";
    }

    private String storedPassword(String id) {
        userRepository.flush();
        return jdbc.queryForObject("SELECT password FROM users WHERE enterprise_id = ?", String.class, id);
    }

    @Test
    @DisplayName("new passwords are encoded with BCrypt by default")
    void encoderDefaultsToBcrypt() {
        String encoded = passwordEncoder.encode("s3cret-value");
        assertThat(encoded).startsWith("{bcrypt}$2");
        assertThat(passwordEncoder.matches("s3cret-value", encoded)).isTrue();
    }

    @Test
    @DisplayName("a legacy {noop} password logs in once and is re-hashed with BCrypt")
    void noopPassword_isUpgradedOnLogin() throws Exception {
        createSME("pw.legacy", "Legacy User");
        assertThat(storedPassword("pw.legacy")).isEqualTo("{noop}password123");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("pw.legacy", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        String upgraded = storedPassword("pw.legacy");
        assertThat(upgraded).startsWith("{bcrypt}").doesNotContain("password123");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("pw.legacy", "password123")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("V3 migration prefixes plain-text passwords with {noop} and leaves encoded ones alone")
    void v3Migration_prefixesPlainPasswords() throws Exception {
        jdbc.update("INSERT INTO users (enterprise_id, password, full_name, role, active, preferred_language) "
                + "VALUES ('pw.plain', 'plain-text', 'Plain', 'SME', TRUE, 'en')");
        String bcrypt = passwordEncoder.encode("already-hashed");
        jdbc.update("INSERT INTO users (enterprise_id, password, full_name, role, active, preferred_language) "
                + "VALUES ('pw.hashed', ?, 'Hashed', 'SME', TRUE, 'en')", bcrypt);

        String v3 = new ClassPathResource("db/migration/V3__prefix_legacy_passwords.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        for (String statement : v3.split(";")) {
            String sql = statement.lines().filter(l -> !l.trim().startsWith("--")).reduce("", (a, b) -> a + "\n" + b).trim();
            if (!sql.isEmpty()) {
                jdbc.execute(sql);
            }
        }

        assertThat(storedPassword("pw.plain")).isEqualTo("{noop}plain-text");
        assertThat(storedPassword("pw.hashed")).isEqualTo(bcrypt);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("pw.plain", "plain-text")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an inactive user cannot log in")
    void inactiveUser_gets401() throws Exception {
        User user = createSME("pw.inactive", "Inactive User");
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("pw.inactive", "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }
}
