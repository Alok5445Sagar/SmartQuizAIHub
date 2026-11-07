package com.smartquiz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.dto.AuthDTO;
import com.smartquiz.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@DisplayName("Auth Controller Tests")
public class AuthControllerTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        createSME("auth.sme", "Auth SME");
        createAdmin("auth.admin");
                User participant = new User();
                participant.setEnterpriseId("auth.participant");
                participant.setPassword("password123");
                participant.setFullName("Auth Participant");
                participant.setRole(User.Role.PARTICIPANT);
                participant.setActive(true);
                userRepository.save(participant);
    }

    @Test
    @DisplayName("Login with valid SME credentials returns JWT token and role")
    void login_validSme_returnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AuthDTO.LoginRequest("auth.sme", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.enterpriseId").value("auth.sme"))
                .andExpect(jsonPath("$.role").value("SME"))
                .andExpect(jsonPath("$.fullName").value("Auth SME"));
    }

    @Test
    @DisplayName("Login with valid Admin credentials returns ADMIN role")
    void login_validAdmin_returnsAdminRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AuthDTO.LoginRequest("auth.admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("Login with PARTICIPANT account is blocked for platform auth")
    void login_participantAccount_returns403() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AuthDTO.LoginRequest("auth.participant", "password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Participant accounts can only be used on the live quiz page"));
    }

    @Test
        @DisplayName("Login with wrong password returns 401")
        void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AuthDTO.LoginRequest("auth.sme", "wrongpass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }

        @Test
        @DisplayName("Login with unknown user returns 401")
        void login_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AuthDTO.LoginRequest("nobody", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }

    @Test
    @DisplayName("Accessing protected endpoint without token returns 401")
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/mcq/my-questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me returns current logged-in user info")
    void getMe_validToken_returnsUserInfo() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AuthDTO.LoginRequest("auth.sme", "password123"))))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enterpriseId").value("auth.sme"))
                .andExpect(jsonPath("$.role").value("SME"));
    }
}
