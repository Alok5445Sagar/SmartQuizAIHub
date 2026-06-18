package com.smartquiz.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionsResponse {
    private List<SessionSummary> sessions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSummary {
        private String sessionId;
        private String title;
        private long lastUpdatedAt;
    }
}
