package com.smartquiz.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIGenerationRequest {
    private Long stackId;
    private Long topicId;
    private String difficulty;
    private int numberOfQuestions;
    private Boolean generateMCQ;
    private Boolean generateMSQ;

    public boolean isGenerateMCQEnabled() {
        return generateMCQ == null || Boolean.TRUE.equals(generateMCQ);
    }

    public boolean isGenerateMSQEnabled() {
        return Boolean.TRUE.equals(generateMSQ);
    }
}