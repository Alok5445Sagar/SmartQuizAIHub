package com.smartquiz.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Utility Tests")
public class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret",
                "TestSecretKeyForJWTTokenGenerationInTestEnvironment2024VeryLong");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 86400000L);
    }

    @Test
    @DisplayName("generateToken returns non-null token")
    void generateToken_returnsNonNullToken() {
        String token = jwtUtils.generateToken("test.user", "SME");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("getEnterpriseIdFromToken extracts correct enterprise ID")
    void getEnterpriseIdFromToken_extractsCorrectId() {
        String token = jwtUtils.generateToken("gaurav.a.bhola", "SME");
        String extracted = jwtUtils.getEnterpriseIdFromToken(token);
        assertThat(extracted).isEqualTo("gaurav.a.bhola");
    }

    @Test
    @DisplayName("validateToken returns true for valid token")
    void validateToken_validToken_returnsTrue() {
        String token = jwtUtils.generateToken("test.user", "SME");
        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken returns false for tampered token")
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtUtils.generateToken("test.user", "SME") + "tampered";
        assertThat(jwtUtils.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken returns false for empty string")
    void validateToken_emptyToken_returnsFalse() {
        assertThat(jwtUtils.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("Different users get different tokens")
    void generateToken_differentUsersGetDifferentTokens() {
        String t1 = jwtUtils.generateToken("user.one", "SME");
        String t2 = jwtUtils.generateToken("user.two", "SME");
        assertThat(t1).isNotEqualTo(t2);
    }
}