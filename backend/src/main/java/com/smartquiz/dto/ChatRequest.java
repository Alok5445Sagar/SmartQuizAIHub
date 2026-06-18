package com.smartquiz.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;

    @Valid
    @Size(max = 20, message = "History can contain at most 20 messages")
    private List<ChatMessage> history;

    @Size(max = 100, message = "Session ID is too long")
    private String sessionId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        @NotBlank(message = "Role is required")
        @Size(max = 20, message = "Role is too long")
        private String role;

        @NotBlank(message = "Content is required")
        @Size(max = 2000, message = "Content cannot exceed 2000 characters")
        private String content;
    }
}
