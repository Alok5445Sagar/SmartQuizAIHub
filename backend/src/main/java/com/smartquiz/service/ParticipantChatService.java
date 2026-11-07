package com.smartquiz.service;

import com.smartquiz.exception.NotFoundException;
import com.smartquiz.exception.ForbiddenException;
import com.smartquiz.exception.BusinessRuleException;
import com.smartquiz.exception.UnauthorizedException;

import com.smartquiz.dto.QuizDTO;
import com.smartquiz.entity.ParticipantDirectMessage;
import com.smartquiz.entity.User;
import com.smartquiz.repository.ParticipantDirectMessageRepository;
import com.smartquiz.repository.UserRepository;
import com.smartquiz.security.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class ParticipantChatService {

    private static final long SSE_TIMEOUT_MS = 0L;
    private static final int DEFAULT_HISTORY_LIMIT = 60;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L;

    private static final Set<User.Role> CHAT_ROLES = EnumSet.of(User.Role.PARTICIPANT, User.Role.SME, User.Role.ADMIN);

    @Value("${app.chat.attachments.dir:${CHAT_ATTACHMENTS_DIR:backend-data/chat-attachments}}")
    private String attachmentsDir;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParticipantDirectMessageRepository participantDirectMessageRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private AttachmentPolicy attachmentPolicy;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> activeEmittersByEnterpriseId = new ConcurrentHashMap<>();

    @Autowired
    private com.smartquiz.observability.ActiveStreams activeStreams;

    public QuizDTO.ParticipantChatUsersResponse getChatUsers(String selfEnterpriseId) {
        User self = requireChatUser(selfEnterpriseId);

        List<User> candidates = userRepository.findByRoleInOrderByFullNameAsc(CHAT_ROLES);
        List<QuizDTO.ParticipantChatUserSummary> users = new ArrayList<>();

        for (User candidate : candidates) {
            if (candidate.getEnterpriseId().equals(self.getEnterpriseId())) {
                continue;
            }
            if (!canChatWith(self.getRole(), candidate.getRole())) {
                continue;
            }
            users.add(toUserSummary(candidate));
        }

        users.sort(Comparator
                .comparing(QuizDTO.ParticipantChatUserSummary::isOnline).reversed()
                .thenComparing(QuizDTO.ParticipantChatUserSummary::getFullName));

        return new QuizDTO.ParticipantChatUsersResponse(self.getEnterpriseId(), users);
    }

        @Transactional(readOnly = true)
        public QuizDTO.ParticipantChatHistoryResponse getConversationHistory(
            String selfEnterpriseId,
            String withEnterpriseId,
            Integer limit
    ) {
        User self = requireChatUser(selfEnterpriseId);
        User other = resolveEligibleUser(withEnterpriseId, self.getRole());

        int safeLimit = limit == null ? DEFAULT_HISTORY_LIMIT : Math.max(1, Math.min(limit, 200));
        List<ParticipantDirectMessage> rows = participantDirectMessageRepository.findConversation(
                self.getEnterpriseId(),
                other.getEnterpriseId(),
                PageRequest.of(0, safeLimit)
        );

        Map<String, String> nameByEnterpriseId = new HashMap<>();
        nameByEnterpriseId.put(self.getEnterpriseId(), self.getFullName());
        nameByEnterpriseId.put(other.getEnterpriseId(), other.getFullName());

        List<QuizDTO.ParticipantChatMessageView> messages = rows.stream()
                .sorted(Comparator.comparing(ParticipantDirectMessage::getCreatedAt))
            .map(row -> toMessageView(row, nameByEnterpriseId, self.getEnterpriseId()))
                .toList();

        return new QuizDTO.ParticipantChatHistoryResponse(self.getEnterpriseId(), other.getEnterpriseId(), messages);
    }

    public QuizDTO.ParticipantChatMessageView sendMessage(String selfEnterpriseId, QuizDTO.ParticipantChatMessageRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Chat request is required");
        }

        User sender = requireChatUser(selfEnterpriseId);
        User recipient = resolveEligibleUser(request.getRecipientEnterpriseId(), sender.getRole());
        rateLimitService.consumeChatMessage(sender.getEnterpriseId());

        if (sender.getEnterpriseId().equals(recipient.getEnterpriseId())) {
            throw new BusinessRuleException("Cannot send chat message to yourself");
        }

        String normalizedText = normalizeMessage(request.getMessage(), false);
        ParticipantDirectMessage saved = persistMessage(
            sender,
            recipient,
            normalizedText,
            null,
            null,
            null,
            null
        );

        Map<String, String> nameByEnterpriseId = new HashMap<>();
        nameByEnterpriseId.put(sender.getEnterpriseId(), sender.getFullName());
        nameByEnterpriseId.put(recipient.getEnterpriseId(), recipient.getFullName());

        QuizDTO.ParticipantChatMessageView messageView = toMessageView(saved, nameByEnterpriseId, sender.getEnterpriseId());
        QuizDTO.ParticipantChatStreamEvent event = new QuizDTO.ParticipantChatStreamEvent(
                "message",
                messageView,
                null,
            null,
            null,
            null,
            null,
            null,
            null,
                LocalDateTime.now()
        );

        sendEventToUser(sender.getEnterpriseId(), event);
        sendEventToUser(recipient.getEnterpriseId(), event);

        return messageView;
    }

    public QuizDTO.ParticipantChatMessageView sendAttachment(
            String selfEnterpriseId,
            String recipientEnterpriseId,
            String message,
            MultipartFile file
    ) {
        User sender = requireChatUser(selfEnterpriseId);
        User recipient = resolveEligibleUser(recipientEnterpriseId, sender.getRole());
        rateLimitService.consumeChatMessage(sender.getEnterpriseId());

        if (sender.getEnterpriseId().equals(recipient.getEnterpriseId())) {
            throw new BusinessRuleException("Cannot send chat message to yourself");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Attachment file is required");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BusinessRuleException("Attachment exceeds 10MB size limit");
        }

        String normalizedMessage = normalizeMessage(message, true);
        StoredAttachment stored = storeAttachment(file);

        ParticipantDirectMessage saved = persistMessage(
                sender,
                recipient,
                normalizedMessage,
                stored.fileName,
                stored.contentType,
                stored.sizeBytes,
                stored.storagePath
        );

        Map<String, String> nameByEnterpriseId = new HashMap<>();
        nameByEnterpriseId.put(sender.getEnterpriseId(), sender.getFullName());
        nameByEnterpriseId.put(recipient.getEnterpriseId(), recipient.getFullName());

        QuizDTO.ParticipantChatMessageView messageView = toMessageView(saved, nameByEnterpriseId, sender.getEnterpriseId());
        QuizDTO.ParticipantChatStreamEvent event = new QuizDTO.ParticipantChatStreamEvent(
                "message",
                messageView,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );

        sendEventToUser(sender.getEnterpriseId(), event);
        sendEventToUser(recipient.getEnterpriseId(), event);

        return messageView;
    }

    public ChatAttachmentDownload downloadAttachment(Long messageId, String selfEnterpriseId) {
        if (messageId == null) {
            throw new BusinessRuleException("Message ID is required");
        }

        User self = requireChatUser(selfEnterpriseId);
        ParticipantDirectMessage message = participantDirectMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Attachment message not found"));

        boolean isParticipant = self.getEnterpriseId().equals(message.getSenderEnterpriseId())
                || self.getEnterpriseId().equals(message.getRecipientEnterpriseId());
        if (!isParticipant) {
            throw new ForbiddenException("Not authorized to access this attachment");
        }

        if (!StringUtils.hasText(message.getAttachmentStoragePath())) {
            throw new NotFoundException("No attachment found for this message");
        }

        try {
            Path path = Paths.get(message.getAttachmentStoragePath()).toAbsolutePath().normalize();
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new NotFoundException("Attachment file is not available");
            }

            String contentType = StringUtils.hasText(message.getAttachmentContentType())
                    ? message.getAttachmentContentType()
                    : "application/octet-stream";
            String fileName = StringUtils.hasText(message.getAttachmentName())
                    ? message.getAttachmentName()
                    : "attachment.bin";

            return new ChatAttachmentDownload(resource, fileName, contentType);
        } catch (MalformedURLException ex) {
            throw new BusinessRuleException("Invalid attachment path");
        }
    }

    public SseEmitter openStream(String selfEnterpriseId) {
        User self = requireChatUser(selfEnterpriseId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String enterpriseId = self.getEnterpriseId();

        activeEmittersByEnterpriseId.computeIfAbsent(enterpriseId, unused -> new CopyOnWriteArrayList<>()).add(emitter);
        activeStreams.register(com.smartquiz.observability.ActiveStreams.Type.PARTICIPANT_CHAT, emitter);

        emitter.onCompletion(() -> removeEmitter(enterpriseId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(enterpriseId, emitter);
        });
        emitter.onError(ex -> removeEmitter(enterpriseId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("participant-chat")
                    .data(new QuizDTO.ParticipantChatStreamEvent("connected", null, null, null, null, null, null, null, null, LocalDateTime.now())));
            sendPresenceUpdateToUser(enterpriseId);
            broadcastPresenceUpdate();
        } catch (IOException ex) {
            removeEmitter(enterpriseId, emitter);
            throw new IllegalStateException("Unable to establish chat stream");
        }

        return emitter;
    }

    private void removeEmitter(String enterpriseId, SseEmitter emitter) {
        activeStreams.unregister(emitter);
        CopyOnWriteArrayList<SseEmitter> emitters = activeEmittersByEnterpriseId.get(enterpriseId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            activeEmittersByEnterpriseId.remove(enterpriseId);
        }
        broadcastPresenceUpdate();
    }

    private void sendPresenceUpdateToUser(String enterpriseId) {
        try {
            User self = userRepository.findByEnterpriseId(enterpriseId)
                    .orElseThrow(() -> new NotFoundException("User not found"));
            List<User> candidates = userRepository.findByRoleInOrderByFullNameAsc(CHAT_ROLES);
            List<QuizDTO.ParticipantChatUserSummary> users = new ArrayList<>();
            for (User candidate : candidates) {
                if (candidate.getEnterpriseId().equals(self.getEnterpriseId())) {
                    continue;
                }
                if (!canChatWith(self.getRole(), candidate.getRole())) {
                    continue;
                }
                users.add(toUserSummary(candidate));
            }
            users.sort(Comparator
                    .comparing(QuizDTO.ParticipantChatUserSummary::isOnline).reversed()
                    .thenComparing(QuizDTO.ParticipantChatUserSummary::getFullName));

            QuizDTO.ParticipantChatStreamEvent event = new QuizDTO.ParticipantChatStreamEvent(
                    "presence",
                    null,
                    users,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    LocalDateTime.now()
            );
            sendEventToUser(enterpriseId, event);
        } catch (RuntimeException ex) {
            // Ignore ephemeral presence push failures for removed users.
        }
    }

    private void broadcastPresenceUpdate() {
        for (String enterpriseId : activeEmittersByEnterpriseId.keySet()) {
            sendPresenceUpdateToUser(enterpriseId);
        }
    }

    private void sendEventToUser(String enterpriseId, QuizDTO.ParticipantChatStreamEvent event) {
        CopyOnWriteArrayList<SseEmitter> emitters = activeEmittersByEnterpriseId.get(enterpriseId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> failed = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("participant-chat").data(event));
            } catch (IOException ex) {
                failed.add(emitter);
            }
        }

        if (!failed.isEmpty()) {
            failed.forEach(activeStreams::unregister);
            emitters.removeAll(failed);
            if (emitters.isEmpty()) {
                activeEmittersByEnterpriseId.remove(enterpriseId);
            }
        }
    }

    public void publishTyping(String selfEnterpriseId, QuizDTO.ParticipantChatTypingRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Typing request is required");
        }

        User self = requireChatUser(selfEnterpriseId);
        User other = resolveEligibleUser(request.getWithEnterpriseId(), self.getRole());

        if (self.getEnterpriseId().equals(other.getEnterpriseId())) {
            return;
        }

        QuizDTO.ParticipantChatStreamEvent event = new QuizDTO.ParticipantChatStreamEvent(
                "typing",
                null,
                null,
                self.getEnterpriseId(),
                self.getEnterpriseId(),
                Boolean.TRUE.equals(request.getTyping()),
                null,
                null,
                null,
                LocalDateTime.now()
        );

        sendEventToUser(other.getEnterpriseId(), event);
    }

    @Transactional
    public void markConversationRead(String selfEnterpriseId, QuizDTO.ParticipantChatReadRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Read request is required");
        }

        User self = requireChatUser(selfEnterpriseId);
        User other = resolveEligibleUser(request.getWithEnterpriseId(), self.getRole());

        int updated = participantDirectMessageRepository.markMessagesAsRead(
                other.getEnterpriseId(),
                self.getEnterpriseId(),
                LocalDateTime.now(),
                request.getLastSeenMessageId()
        );

        if (updated > 0) {
            sendReadReceiptEvent(other.getEnterpriseId(), self.getEnterpriseId(), request.getLastSeenMessageId());
        }
    }

    private void sendReadReceiptEvent(String recipientEnterpriseId, String withEnterpriseId, Long lastSeenMessageId) {
        QuizDTO.ParticipantChatStreamEvent event = new QuizDTO.ParticipantChatStreamEvent(
                "message-status",
                null,
                null,
                withEnterpriseId,
                null,
                null,
                lastSeenMessageId,
                "READ",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        sendEventToUser(recipientEnterpriseId, event);
    }

    /** The caller is identified by the authenticated principal (JWT or redeemed stream ticket). */
    private User requireChatUser(String enterpriseId) {
        if (enterpriseId == null || enterpriseId.isBlank()) {
            throw new UnauthorizedException("Authentication is required to use chat");
        }
        User user = userRepository.findByEnterpriseId(enterpriseId.trim())
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Authentication is required to use chat"));
        if (!CHAT_ROLES.contains(user.getRole())) {
            throw new ForbiddenException("Current role is not allowed to use chat");
        }
        return user;
    }

    private User resolveEligibleUser(String enterpriseId, User.Role senderRole) {
        if (enterpriseId == null || enterpriseId.isBlank()) {
            throw new BusinessRuleException("Recipient enterprise ID is required");
        }
        User user = userRepository.findByEnterpriseId(enterpriseId.trim())
                .orElseThrow(() -> new NotFoundException("Recipient not found"));

        if (!CHAT_ROLES.contains(user.getRole())) {
            throw new BusinessRuleException("Recipient is not available for participant chat");
        }
        if (!canChatWith(senderRole, user.getRole())) {
            throw new ForbiddenException("Recipient is not allowed for your role");
        }
        return user;
    }

    private boolean canChatWith(User.Role senderRole, User.Role recipientRole) {
        if (senderRole == null || recipientRole == null) {
            return false;
        }

        if (senderRole == User.Role.ADMIN) {
            return recipientRole == User.Role.SME;
        }

        if (senderRole == User.Role.SME) {
            return recipientRole == User.Role.SME
                    || recipientRole == User.Role.ADMIN
                    || recipientRole == User.Role.PARTICIPANT;
        }

        // Employee participants can chat with SMEs and other employee participants.
        return senderRole == User.Role.PARTICIPANT
            && (recipientRole == User.Role.SME || recipientRole == User.Role.PARTICIPANT);
    }

    private QuizDTO.ParticipantChatUserSummary toUserSummary(User user) {
        CopyOnWriteArrayList<SseEmitter> emitters = activeEmittersByEnterpriseId.get(user.getEnterpriseId());
        boolean online = emitters != null && !emitters.isEmpty();
        return new QuizDTO.ParticipantChatUserSummary(
                user.getEnterpriseId(),
                user.getFullName(),
                user.getRole().name(),
                online
        );
    }

    private QuizDTO.ParticipantChatMessageView toMessageView(
            ParticipantDirectMessage row,
            Map<String, String> nameByEnterpriseId,
            String viewerEnterpriseId
    ) {
        String senderName = nameByEnterpriseId.getOrDefault(row.getSenderEnterpriseId(), row.getSenderEnterpriseId());
        String recipientName = nameByEnterpriseId.getOrDefault(row.getRecipientEnterpriseId(), row.getRecipientEnterpriseId());

        String deliveryStatus;
        if (!row.getSenderEnterpriseId().equals(viewerEnterpriseId)) {
            deliveryStatus = "READ";
        } else if (row.getReadAt() != null) {
            deliveryStatus = "READ";
        } else if (row.getDeliveredAt() != null) {
            deliveryStatus = "DELIVERED";
        } else {
            deliveryStatus = "SENT";
        }

        return new QuizDTO.ParticipantChatMessageView(
                row.getMessageId(),
                row.getSenderEnterpriseId(),
                senderName,
                row.getRecipientEnterpriseId(),
                recipientName,
                row.getMessageText(),
                StringUtils.hasText(row.getAttachmentStoragePath()),
                row.getAttachmentName(),
                row.getAttachmentContentType(),
                row.getAttachmentSizeBytes(),
                row.getCreatedAt(),
                row.getDeliveredAt(),
                row.getReadAt(),
                deliveryStatus
        );
    }

    private ParticipantDirectMessage persistMessage(
            User sender,
            User recipient,
            String normalizedText,
            String attachmentName,
            String attachmentContentType,
            Long attachmentSizeBytes,
            String attachmentStoragePath
    ) {
        ParticipantDirectMessage row = new ParticipantDirectMessage();
        row.setSenderEnterpriseId(sender.getEnterpriseId());
        row.setRecipientEnterpriseId(recipient.getEnterpriseId());
        row.setMessageText(normalizedText);
        row.setAttachmentName(attachmentName);
        row.setAttachmentContentType(attachmentContentType);
        row.setAttachmentSizeBytes(attachmentSizeBytes);
        row.setAttachmentStoragePath(attachmentStoragePath);
        row.setDeliveredAt(LocalDateTime.now());
        return participantDirectMessageRepository.save(row);
    }

    private StoredAttachment storeAttachment(MultipartFile file) {
        try {
            Path root = Paths.get(attachmentsDir).toAbsolutePath().normalize();
            Files.createDirectories(root);

            String originalFileName = Paths.get(StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment.bin" : file.getOriginalFilename()))
                    .getFileName().toString();
            if (originalFileName.isBlank() || originalFileName.contains("..")) {
                throw new BusinessRuleException("Invalid attachment name");
            }
            String contentType = attachmentPolicy.validate(file, originalFileName);

            // Never use the client's file name on disk; it is only kept for display and download.
            String storedName = UUID.randomUUID() + ".bin";
            Path target = root.resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessRuleException("Invalid attachment path");
            }

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredAttachment(originalFileName, contentType, file.getSize(), target.toString());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store attachment");
        }
    }

    private String normalizeMessage(String message, boolean allowBlank) {
        if (message == null) {
            if (allowBlank) {
                return null;
            }
            throw new BusinessRuleException("Message text is required");
        }

        String normalized = message.trim();
        if (normalized.isBlank()) {
            if (allowBlank) {
                return null;
            }
            throw new BusinessRuleException("Message text cannot be blank");
        }

        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessRuleException("Message text exceeds allowed length");
        }

        return normalized;
    }

    private static class StoredAttachment {
        private final String fileName;
        private final String contentType;
        private final long sizeBytes;
        private final String storagePath;

        private StoredAttachment(String fileName, String contentType, long sizeBytes, String storagePath) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.storagePath = storagePath;
        }
    }

    public static class ChatAttachmentDownload {
        private final Resource resource;
        private final String fileName;
        private final String contentType;

        public ChatAttachmentDownload(Resource resource, String fileName, String contentType) {
            this.resource = resource;
            this.fileName = fileName;
            this.contentType = contentType;
        }

        public Resource getResource() {
            return resource;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
