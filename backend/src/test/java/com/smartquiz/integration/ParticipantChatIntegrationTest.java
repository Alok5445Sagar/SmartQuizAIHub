package com.smartquiz.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.entity.User;
import com.smartquiz.security.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Participant Chat Integration Tests")
class ParticipantChatIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtils jwtUtils;

    @Test
    @DisplayName("Send, typing, history and read endpoints work for employee chat users")
    void participantChatFlow_shouldSupportSendTypingHistoryAndRead() throws Exception {
        User participant = createParticipant("participant.chat.01", "Participant One");
        User sme = createSME("sme.chat.01", "SME One");

        String participantToken = tokenFor(participant);
        String smeToken = tokenFor(sme);

        MvcResult sendResult = mockMvc.perform(post("/api/public/quiz/participant/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantEnterpriseId":"%s",
                                  "participantAuthToken":"%s",
                                  "recipientEnterpriseId":"%s",
                                  "message":"Hello from participant"
                                }
                                """.formatted(participant.getEnterpriseId(), participantToken, sme.getEnterpriseId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderEnterpriseId").value(participant.getEnterpriseId()))
                .andExpect(jsonPath("$.recipientEnterpriseId").value(sme.getEnterpriseId()))
                .andExpect(jsonPath("$.message").value("Hello from participant"))
                .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
                .andReturn();

        JsonNode sendJson = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        long messageId = sendJson.get("messageId").asLong();

        mockMvc.perform(post("/api/public/quiz/participant/chat/typing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantEnterpriseId":"%s",
                                  "participantAuthToken":"%s",
                                  "withEnterpriseId":"%s",
                                  "typing":true
                                }
                                """.formatted(participant.getEnterpriseId(), participantToken, sme.getEnterpriseId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/quiz/participant/chat/history")
                        .param("participantEnterpriseId", sme.getEnterpriseId())
                        .param("participantAuthToken", smeToken)
                        .param("withEnterpriseId", participant.getEnterpriseId())
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfEnterpriseId").value(sme.getEnterpriseId()))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].messageId").value(messageId));

        mockMvc.perform(post("/api/public/quiz/participant/chat/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantEnterpriseId":"%s",
                                  "participantAuthToken":"%s",
                                  "withEnterpriseId":"%s",
                                  "lastSeenMessageId":%d
                                }
                                """.formatted(sme.getEnterpriseId(), smeToken, participant.getEnterpriseId(), messageId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/quiz/participant/chat/history")
                        .param("participantEnterpriseId", participant.getEnterpriseId())
                        .param("participantAuthToken", participantToken)
                        .param("withEnterpriseId", sme.getEnterpriseId())
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1));
    }

    @Test
    @DisplayName("Attachment upload and authorized download work, unauthorized download is blocked")
    void participantChatAttachment_shouldEnforceAuthorization() throws Exception {
        User sender = createParticipant("participant.chat.02", "Participant Two");
        User recipient = createSME("sme.chat.02", "SME Two");
        User admin = createAdmin("admin.chat.unauthorized");

        String senderToken = tokenFor(sender);
        String recipientToken = tokenFor(recipient);
        String adminToken = tokenFor(admin);

        byte[] fileBytes = "Attachment integration payload".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "chat-proof.txt",
                MediaType.TEXT_PLAIN_VALUE,
                fileBytes
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/public/quiz/participant/chat/send-attachment")
                        .file(mockFile)
                        .param("participantEnterpriseId", sender.getEnterpriseId())
                        .param("participantAuthToken", senderToken)
                        .param("recipientEnterpriseId", recipient.getEnterpriseId())
                        .param("message", "Please review attachment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAttachment").value(true))
                .andExpect(jsonPath("$.attachmentName").value("chat-proof.txt"))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        long attachmentMessageId = uploadJson.get("messageId").asLong();

        mockMvc.perform(get("/api/public/quiz/participant/chat/attachment/{messageId}", attachmentMessageId)
                        .param("participantEnterpriseId", recipient.getEnterpriseId())
                        .param("participantAuthToken", recipientToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE))
                .andExpect(content().bytes(fileBytes));

        mockMvc.perform(get("/api/public/quiz/participant/chat/attachment/{messageId}", attachmentMessageId)
                        .param("participantEnterpriseId", admin.getEnterpriseId())
                        .param("participantAuthToken", adminToken))
                .andExpect(status().isForbidden());
    }

                @Test
                @DisplayName("Role matrix is enforced for chat users list and message routing")
                void participantChatRoleMatrix_shouldBeEnforced() throws Exception {
                                User participant = createParticipant("participant.chat.matrix", "Participant Matrix");
                                User participantTwo = createParticipant("participant.chat.matrix.2", "Participant Matrix Two");
                                User sme = createSME("sme.chat.matrix", "SME Matrix");
                                User admin = createAdmin("admin.chat.matrix");

                                String participantToken = tokenFor(participant);
                                String smeToken = tokenFor(sme);
                                String adminToken = tokenFor(admin);

                                MvcResult adminUsersResult = mockMvc.perform(get("/api/public/quiz/participant/chat/users")
                                                                                                .param("participantEnterpriseId", admin.getEnterpriseId())
                                                                                                .param("participantAuthToken", adminToken))
                                                                .andExpect(status().isOk())
                                                                .andReturn();

                                JsonNode adminUsers = objectMapper.readTree(adminUsersResult.getResponse().getContentAsString()).get("users");
                                boolean adminSeesSme = false;
                                boolean adminSeesParticipant = false;
                                for (JsonNode user : adminUsers) {
                                                String enterpriseId = user.path("enterpriseId").asText("");
                                                if (sme.getEnterpriseId().equals(enterpriseId)) {
                                                                adminSeesSme = true;
                                                }
                                                if (participant.getEnterpriseId().equals(enterpriseId)) {
                                                                adminSeesParticipant = true;
                                                }
                                }
                                org.assertj.core.api.Assertions.assertThat(adminSeesSme).isTrue();
                                org.assertj.core.api.Assertions.assertThat(adminSeesParticipant).isFalse();

                                MvcResult smeUsersResult = mockMvc.perform(get("/api/public/quiz/participant/chat/users")
                                                                                                .param("participantEnterpriseId", sme.getEnterpriseId())
                                                                                                .param("participantAuthToken", smeToken))
                                                                .andExpect(status().isOk())
                                                                .andReturn();

                                JsonNode smeUsers = objectMapper.readTree(smeUsersResult.getResponse().getContentAsString()).get("users");
                                boolean smeSeesAdmin = false;
                                boolean smeSeesParticipant = false;
                                for (JsonNode user : smeUsers) {
                                                String enterpriseId = user.path("enterpriseId").asText("");
                                                if (admin.getEnterpriseId().equals(enterpriseId)) {
                                                                smeSeesAdmin = true;
                                                }
                                                if (participant.getEnterpriseId().equals(enterpriseId)) {
                                                                smeSeesParticipant = true;
                                                }
                                }
                                org.assertj.core.api.Assertions.assertThat(smeSeesAdmin).isTrue();
                                org.assertj.core.api.Assertions.assertThat(smeSeesParticipant).isTrue();

                                MvcResult participantUsersResult = mockMvc.perform(get("/api/public/quiz/participant/chat/users")
                                                                                                .param("participantEnterpriseId", participant.getEnterpriseId())
                                                                                                .param("participantAuthToken", participantToken))
                                                                .andExpect(status().isOk())
                                                                .andReturn();

                                JsonNode participantUsers = objectMapper.readTree(participantUsersResult.getResponse().getContentAsString()).get("users");
                                boolean participantSeesSme = false;
                                boolean participantSeesAdmin = false;
                                boolean participantSeesParticipant = false;
                                for (JsonNode user : participantUsers) {
                                                String enterpriseId = user.path("enterpriseId").asText("");
                                                if (sme.getEnterpriseId().equals(enterpriseId)) {
                                                                participantSeesSme = true;
                                                }
                                                if (admin.getEnterpriseId().equals(enterpriseId)) {
                                                                participantSeesAdmin = true;
                                                }
                                                if (participantTwo.getEnterpriseId().equals(enterpriseId)) {
                                                                participantSeesParticipant = true;
                                                }
                                }
                                org.assertj.core.api.Assertions.assertThat(participantSeesSme).isTrue();
                                org.assertj.core.api.Assertions.assertThat(participantSeesAdmin).isFalse();
                                org.assertj.core.api.Assertions.assertThat(participantSeesParticipant).isTrue();

                                mockMvc.perform(post("/api/public/quiz/participant/chat/send")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "participantEnterpriseId":"%s",
                                                                                                                                        "participantAuthToken":"%s",
                                                                                                                                        "recipientEnterpriseId":"%s",
                                                                                                                                        "message":"Hello SME from admin"
                                                                                                                                }
                                                                                                                                """.formatted(admin.getEnterpriseId(), adminToken, sme.getEnterpriseId())))
                                                                .andExpect(status().isOk())
                                                                .andExpect(jsonPath("$.senderEnterpriseId").value(admin.getEnterpriseId()))
                                                                .andExpect(jsonPath("$.recipientEnterpriseId").value(sme.getEnterpriseId()));

                                mockMvc.perform(post("/api/public/quiz/participant/chat/send")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "participantEnterpriseId":"%s",
                                                                                                                                        "participantAuthToken":"%s",
                                                                                                                                        "recipientEnterpriseId":"%s",
                                                                                                                                        "message":"Hello admin from SME"
                                                                                                                                }
                                                                                                                                """.formatted(sme.getEnterpriseId(), smeToken, admin.getEnterpriseId())))
                                                                .andExpect(status().isOk())
                                                                .andExpect(jsonPath("$.senderEnterpriseId").value(sme.getEnterpriseId()))
                                                                .andExpect(jsonPath("$.recipientEnterpriseId").value(admin.getEnterpriseId()));

                                mockMvc.perform(post("/api/public/quiz/participant/chat/send")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "participantEnterpriseId":"%s",
                                                                                                                                        "participantAuthToken":"%s",
                                                                                                                                        "recipientEnterpriseId":"%s",
                                                                                                                                        "message":"Hello participant from SME"
                                                                                                                                }
                                                                                                                                """.formatted(sme.getEnterpriseId(), smeToken, participant.getEnterpriseId())))
                                                                .andExpect(status().isOk())
                                                                .andExpect(jsonPath("$.senderEnterpriseId").value(sme.getEnterpriseId()))
                                                                .andExpect(jsonPath("$.recipientEnterpriseId").value(participant.getEnterpriseId()));

                                                                                                mockMvc.perform(post("/api/public/quiz/participant/chat/send")
                                                                                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                                                                                .content("""
                                                                                                                                                                                                {
                                                                                                                                                                                                        "participantEnterpriseId":"%s",
                                                                                                                                                                                                        "participantAuthToken":"%s",
                                                                                                                                                                                                        "recipientEnterpriseId":"%s",
                                                                                                                                                                                                        "message":"Participant to participant should succeed"
                                                                                                                                                                                                }
                                                                                                                                                                                                """.formatted(participant.getEnterpriseId(), participantToken, participantTwo.getEnterpriseId())))
                                                                                                                                .andExpect(status().isOk())
                                                                                                                                .andExpect(jsonPath("$.senderEnterpriseId").value(participant.getEnterpriseId()))
                                                                                                                                .andExpect(jsonPath("$.recipientEnterpriseId").value(participantTwo.getEnterpriseId()));

                                mockMvc.perform(post("/api/public/quiz/participant/chat/send")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "participantEnterpriseId":"%s",
                                                                                                                                        "participantAuthToken":"%s",
                                                                                                                                        "recipientEnterpriseId":"%s",
                                                                                                                                        "message":"Admin to participant should fail"
                                                                                                                                }
                                                                                                                                """.formatted(admin.getEnterpriseId(), adminToken, participant.getEnterpriseId())))
                                                                .andExpect(status().isForbidden())
                                                                .andExpect(jsonPath("$.message").value("Recipient is not allowed for your role"));

                                mockMvc.perform(post("/api/public/quiz/participant/chat/send")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "participantEnterpriseId":"%s",
                                                                                                                                        "participantAuthToken":"%s",
                                                                                                                                        "recipientEnterpriseId":"%s",
                                                                                                                                        "message":"Participant to admin should fail"
                                                                                                                                }
                                                                                                                                """.formatted(participant.getEnterpriseId(), participantToken, admin.getEnterpriseId())))
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

    private String tokenFor(User user) {
        return jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());
    }
}
