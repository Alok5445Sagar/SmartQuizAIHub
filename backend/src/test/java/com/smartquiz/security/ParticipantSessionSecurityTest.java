package com.smartquiz.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.exception.UnauthorizedException;
import com.smartquiz.entity.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@AutoConfigureMockMvc
@DisplayName("Participant sessions, stream tickets, attachments and rate limits")
class ParticipantSessionSecurityTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private ChatStreamTicketService ticketService;

    private User participant(String id) {
        User user = new User();
        user.setEnterpriseId(id);
        user.setPassword("{noop}password123");
        user.setFullName("Participant " + id);
        user.setRole(User.Role.PARTICIPANT);
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());
    }

    @Test
    @DisplayName("an employee dashboard requested without a token returns 401")
    void employeeDashboardWithoutToken_returns401() throws Exception {
        User p = participant("sess.dash");
        mockMvc.perform(get("/api/public/quiz/participant/dashboard").param("participantEnterpriseId", p.getEnterpriseId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an employee cannot read someone else's dashboard")
    void otherEmployeesDashboard_returns403() throws Exception {
        User p = participant("sess.dash.self");
        User other = participant("sess.dash.other");
        mockMvc.perform(get("/api/public/quiz/participant/dashboard")
                        .param("participantEnterpriseId", other.getEnterpriseId())
                        .header("Authorization", bearer(p)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an employee dashboard is resolved from the token")
    void employeeDashboardFromToken() throws Exception {
        User p = participant("sess.dash.token");
        mockMvc.perform(get("/api/public/quiz/participant/dashboard").header("Authorization", bearer(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantType").value("EMPLOYEE"))
                .andExpect(jsonPath("$.participantEnterpriseId").value(p.getEnterpriseId()));
    }

    @Test
    @DisplayName("stream tickets are one-time and require a JWT to obtain")
    void streamTicketIsSingleUse() throws Exception {
        User p = participant("sess.ticket");
        MvcResult result = mockMvc.perform(post("/api/public/quiz/participant/chat/stream-ticket")
                        .header("Authorization", bearer(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(30))
                .andReturn();
        String ticket = objectMapper.readTree(result.getResponse().getContentAsString()).get("ticket").asText();

        assertThat(ticketService.redeem(ticket)).isEqualTo(p.getEnterpriseId());
        assertThatThrownBy(() -> ticketService.redeem(ticket)).isInstanceOf(UnauthorizedException.class);

        mockMvc.perform(get("/api/public/quiz/participant/chat/stream").param("ticket", ticket))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an executable renamed to .txt is rejected")
    void renamedExecutable_isRejected() throws Exception {
        User sender = participant("sess.exe");
        User recipient = createSME("sess.exe.sme", "Exe SME");
        byte[] fakeExe = new byte[256];
        fakeExe[0] = 'M';
        fakeExe[1] = 'Z';
        fakeExe[0x3c] = (byte) 0x80;
        fakeExe[0x80] = 'P';
        fakeExe[0x81] = 'E';
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, fakeExe);

        mockMvc.perform(multipart("/api/public/quiz/participant/chat/send-attachment").file(file)
                        .param("recipientEnterpriseId", recipient.getEnterpriseId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Attachment content does not match its .txt extension"));
    }

    @Test
    @DisplayName("a disallowed extension is rejected")
    void disallowedExtension_isRejected() throws Exception {
        User sender = participant("sess.ext");
        User recipient = createSME("sess.ext.sme", "Ext SME");
        MockMultipartFile file = new MockMultipartFile("file", "run.sh", "text/x-sh", "echo hi".getBytes());

        mockMvc.perform(multipart("/api/public/quiz/participant/chat/send-attachment").file(file)
                        .param("recipientEnterpriseId", recipient.getEnterpriseId())
                        .header("Authorization", bearer(sender)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the 6th failed login within a minute returns 429 with Retry-After")
    void sixthFailedLogin_returns429() throws Exception {
        createSME("sess.brute", "Brute");
        String body = "{\"enterpriseId\":\"sess.brute\",\"password\":\"wrong\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // Even the right password is refused until the window refills.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":\"sess.brute\",\"password\":\"password123\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("participant portal login is rate limited too")
    void participantLogin_isRateLimited() throws Exception {
        participant("sess.portal.brute");
        String body = "{\"enterpriseId\":\"sess.portal.brute\",\"password\":\"wrong\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/public/quiz/participant/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/public/quiz/participant/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("tokens from another issuer or audience are rejected")
    void foreignTokenRejected() throws Exception {
        createSME("sess.foreign", "Foreign");
        String foreign = Jwts.builder()
                .setSubject("sess.foreign")
                .setIssuer("someone-else")
                .setAudience("smart-quiz-hub-api")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("TestSecretKeyForJWTTokenGenerationInTestEnvironment2024VeryLong".getBytes()),
                        SignatureAlgorithm.HS256)
                .compact();
        assertThat(jwtUtils.validateToken(foreign)).isFalse();
        mockMvc.perform(get("/api/reference/stacks").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }
}
