package com.smartquiz.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.entity.User;
import com.smartquiz.security.JwtUtils;

@AutoConfigureMockMvc
@DisplayName("Participant Chat Integration Tests")
class ParticipantChatIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtils jwtUtils;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header("Authorization", "Bearer " + jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name()));
    }

    private MockHttpServletRequestBuilder send(User from, User to, String message) {
        return as(post("/api/public/quiz/participant/chat/send"), from)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientEnterpriseId\":\"%s\",\"message\":\"%s\"}".formatted(to.getEnterpriseId(), message));
    }

    private Set<String> visibleUsers(User viewer) throws Exception {
        MvcResult result = mockMvc.perform(as(get("/api/public/quiz/participant/chat/users"), viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfEnterpriseId").value(viewer.getEnterpriseId()))
                .andReturn();
        Set<String> ids = new HashSet<>();
        objectMapper.readTree(result.getResponse().getContentAsString()).get("users")
                .forEach(u -> ids.add(u.path("enterpriseId").asText()));
        return ids;
    }

    @Test
    @DisplayName("Send, typing, history and read endpoints work with an Authorization header")
    void participantChatFlow_shouldSupportSendTypingHistoryAndRead() throws Exception {
        User participant = createParticipant("participant.chat.01", "Participant One");
        User sme = createSME("sme.chat.01", "SME One");

        MvcResult sendResult = mockMvc.perform(send(participant, sme, "Hello from participant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderEnterpriseId").value(participant.getEnterpriseId()))
                .andExpect(jsonPath("$.recipientEnterpriseId").value(sme.getEnterpriseId()))
                .andExpect(jsonPath("$.message").value("Hello from participant"))
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
                .andReturn();
        long messageId = objectMapper.readTree(sendResult.getResponse().getContentAsString()).get("messageId").asLong();

        mockMvc.perform(as(post("/api/public/quiz/participant/chat/typing"), participant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withEnterpriseId\":\"%s\",\"typing\":true}".formatted(sme.getEnterpriseId())))
                .andExpect(status().isOk());

        mockMvc.perform(as(get("/api/public/quiz/participant/chat/history"), sme)
                        .param("withEnterpriseId", participant.getEnterpriseId())
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfEnterpriseId").value(sme.getEnterpriseId()))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].messageId").value(messageId));

        mockMvc.perform(as(post("/api/public/quiz/participant/chat/read"), sme)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withEnterpriseId\":\"%s\",\"lastSeenMessageId\":%d}".formatted(participant.getEnterpriseId(), messageId)))
                .andExpect(status().isOk());

        mockMvc.perform(as(get("/api/public/quiz/participant/chat/history"), participant)
                        .param("withEnterpriseId", sme.getEnterpriseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].deliveryStatus").value("READ"));
    }

    @Test
    @DisplayName("Chat endpoints require a JWT; ids or tokens in the URL are ignored")
    void chatRequiresHeaderAuth() throws Exception {
        User participant = createParticipant("participant.chat.noauth", "No Auth");
        String token = jwtUtils.generateToken(participant.getEnterpriseId(), "PARTICIPANT");

        mockMvc.perform(get("/api/public/quiz/participant/chat/users")
                        .param("participantEnterpriseId", participant.getEnterpriseId())
                        .param("participantAuthToken", token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/public/quiz/participant/chat/stream-ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Attachment upload and authorized download work, unauthorized download is blocked")
    void participantChatAttachment_shouldEnforceAuthorization() throws Exception {
        User sender = createParticipant("participant.chat.02", "Participant Two");
        User recipient = createSME("sme.chat.02", "SME Two");
        User admin = createAdmin("admin.chat.unauthorized");

        byte[] fileBytes = "Attachment integration payload".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile mockFile = new MockMultipartFile("file", "chat proof é.txt", MediaType.TEXT_PLAIN_VALUE, fileBytes);

        MvcResult uploadResult = mockMvc.perform(as(multipart("/api/public/quiz/participant/chat/send-attachment").file(mockFile), sender)
                        .param("recipientEnterpriseId", recipient.getEnterpriseId())
                        .param("message", "Please review attachment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment").value(true))
                .andExpect(jsonPath("$.attachmentName").value("chat proof é.txt"))
                .andExpect(jsonPath("$.attachmentContentType").value("text/plain"))
                .andReturn();
        long attachmentMessageId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("messageId").asLong();

        mockMvc.perform(as(get("/api/public/quiz/participant/chat/attachment/{messageId}", attachmentMessageId), recipient))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''chat%20proof%20%C3%A9.txt")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(fileBytes));

        mockMvc.perform(as(get("/api/public/quiz/participant/chat/attachment/{messageId}", attachmentMessageId), admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/public/quiz/participant/chat/attachment/{messageId}", attachmentMessageId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Role matrix is enforced for chat users list and message routing")
    void participantChatRoleMatrix_shouldBeEnforced() throws Exception {
        User participant = createParticipant("participant.chat.matrix", "Participant Matrix");
        User participantTwo = createParticipant("participant.chat.matrix.2", "Participant Matrix Two");
        User sme = createSME("sme.chat.matrix", "SME Matrix");
        User admin = createAdmin("admin.chat.matrix");

        Set<String> adminSees = visibleUsers(admin);
        assertThat(adminSees).contains(sme.getEnterpriseId()).doesNotContain(participant.getEnterpriseId());

        Set<String> smeSees = visibleUsers(sme);
        assertThat(smeSees).contains(admin.getEnterpriseId(), participant.getEnterpriseId());

        Set<String> participantSees = visibleUsers(participant);
        assertThat(participantSees).contains(sme.getEnterpriseId(), participantTwo.getEnterpriseId())
                .doesNotContain(admin.getEnterpriseId());

        mockMvc.perform(send(admin, sme, "Hello SME from admin")).andExpect(status().isOk());
        mockMvc.perform(send(sme, admin, "Hello admin from SME")).andExpect(status().isOk());
        mockMvc.perform(send(sme, participant, "Hello participant from SME")).andExpect(status().isOk());
        mockMvc.perform(send(participant, participantTwo, "Participant to participant")).andExpect(status().isOk());

        mockMvc.perform(send(admin, participant, "Admin to participant should fail"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Recipient is not allowed for your role"));
        mockMvc.perform(send(participant, admin, "Participant to admin should fail"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Recipient is not allowed for your role"));
    }

    private User createParticipant(String enterpriseId, String fullName) {
        User user = new User();
        user.setEnterpriseId(enterpriseId);
        user.setPassword("{noop}password123");
        user.setFullName(fullName);
        user.setRole(User.Role.PARTICIPANT);
        user.setActive(true);
        return userRepository.save(user);
    }
}
