package com.smartquiz.service;

import com.smartquiz.exception.NotFoundException;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartquiz.dto.ChatHistoryResponse;
import com.smartquiz.dto.ChatRequest;
import com.smartquiz.dto.ChatSessionsResponse;
import com.smartquiz.entity.ChatMessage;
import com.smartquiz.entity.User;
import com.smartquiz.repository.ChatMessageRepository;
import com.smartquiz.repository.UserRepository;

@Service
public class ChatMemoryService {

    private static final int MAX_SESSION_STREAMS = 5;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserRepository userRepository;

    public String resolveSessionId(String enterpriseId, String requestedSessionId) {
        if (requestedSessionId != null && !requestedSessionId.isBlank()) {
            return trim(requestedSessionId, 100);
        }

        List<String> sessions = chatMessageRepository.findSessionIdsByRecentActivity(enterpriseId);
        if (!sessions.isEmpty()) {
            return sessions.get(0);
        }

        return "session-" + UUID.randomUUID();
    }

    @Transactional
    public void saveUserMessage(String enterpriseId, String sessionId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        User user = userRepository.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new NotFoundException("User not found for chat persistence."));

        ChatMessage message = new ChatMessage();
        message.setUser(user);
        message.setRole(ChatMessage.Role.USER);
        message.setContent(trim(content, 2000));
        message.setSessionId(trim(sessionId, 100));
        chatMessageRepository.save(message);

        pruneOldSessions(enterpriseId);
    }

    @Transactional
    public void saveAssistantMessage(String enterpriseId, String sessionId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        User user = userRepository.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new NotFoundException("User not found for chat persistence."));

        ChatMessage message = new ChatMessage();
        message.setUser(user);
        message.setRole(ChatMessage.Role.ASSISTANT);
        message.setContent(trim(content, 4000));
        message.setSessionId(trim(sessionId, 100));
        chatMessageRepository.save(message);

        pruneOldSessions(enterpriseId);
    }

    public List<ChatRequest.ChatMessage> getRecentHistoryForPrompt(String enterpriseId, String sessionId, int maxMessages) {
        int cap = Math.max(1, Math.min(maxMessages, 20));
        String resolvedSessionId = resolveSessionId(enterpriseId, sessionId);
        List<ChatMessage> rows = chatMessageRepository.findByUserEnterpriseIdAndSessionIdOrderByCreatedAtDesc(
                enterpriseId, resolvedSessionId, PageRequest.of(0, cap));

        List<ChatRequest.ChatMessage> history = new ArrayList<>();
        for (ChatMessage row : rows) {
            history.add(new ChatRequest.ChatMessage(
                    row.getRole() == ChatMessage.Role.USER ? "user" : "assistant",
                    row.getContent()));
        }
        Collections.reverse(history);
        return history;
    }

    public ChatHistoryResponse getRecentHistory(String enterpriseId, String sessionId, int maxMessages) {
        int cap = Math.max(1, Math.min(maxMessages, 100));
        String resolvedSessionId = resolveSessionId(enterpriseId, sessionId);
        List<ChatMessage> rows = chatMessageRepository.findByUserEnterpriseIdAndSessionIdOrderByCreatedAtDesc(
                enterpriseId, resolvedSessionId, PageRequest.of(0, cap));

        List<ChatHistoryResponse.HistoryMessage> messages = new ArrayList<>();
        for (ChatMessage row : rows) {
            long ts = toEpochMillis(row);
            messages.add(new ChatHistoryResponse.HistoryMessage(
                    row.getRole() == ChatMessage.Role.USER ? "user" : "assistant",
                    row.getContent(),
                    ts));
        }
        Collections.reverse(messages);
        return new ChatHistoryResponse(resolvedSessionId, messages);
    }

    public ChatSessionsResponse getRecentSessions(String enterpriseId, int maxSessions) {
        int keep = Math.max(1, Math.min(maxSessions, MAX_SESSION_STREAMS));
        List<String> sessionIds = chatMessageRepository.findSessionIdsByRecentActivity(enterpriseId);

        List<ChatSessionsResponse.SessionSummary> sessions = new ArrayList<>();
        for (String sessionId : sessionIds.stream().limit(keep).toList()) {
            List<ChatMessage> rows = chatMessageRepository.findByUserEnterpriseIdAndSessionIdOrderByCreatedAtDesc(
                    enterpriseId,
                    sessionId,
                    PageRequest.of(0, 25));
            if (rows.isEmpty()) {
                continue;
            }

            long lastUpdatedAt = toEpochMillis(rows.get(0));
            sessions.add(new ChatSessionsResponse.SessionSummary(
                    sessionId,
                    buildSessionTitle(rows),
                    lastUpdatedAt));
        }

        sessions.sort(Comparator.comparingLong(ChatSessionsResponse.SessionSummary::getLastUpdatedAt).reversed());
        return new ChatSessionsResponse(sessions);
    }

    @Transactional
    public void pruneOldSessions(String enterpriseId) {
        List<String> sessionIds = chatMessageRepository.findSessionIdsByRecentActivity(enterpriseId);
        if (sessionIds.size() <= MAX_SESSION_STREAMS) {
            return;
        }

        List<String> toDelete = new ArrayList<>(sessionIds.subList(MAX_SESSION_STREAMS, sessionIds.size()));
        if (!toDelete.isEmpty()) {
            chatMessageRepository.deleteByUserEnterpriseIdAndSessionIdIn(enterpriseId, toDelete);
        }
    }

    private long toEpochMillis(ChatMessage row) {
        return row.getCreatedAt() == null
                ? System.currentTimeMillis()
                : row.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String buildSessionTitle(List<ChatMessage> rows) {
        for (ChatMessage row : rows) {
            if (row.getRole() == ChatMessage.Role.USER && row.getContent() != null && !row.getContent().isBlank()) {
                return trim(row.getContent().replace('\n', ' ').trim(), 48);
            }
        }

        for (ChatMessage row : rows) {
            if (row.getContent() != null && !row.getContent().isBlank()) {
                return trim(row.getContent().replace('\n', ' ').trim(), 48);
            }
        }

        return "Chat session";
    }

    private String trim(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
}
