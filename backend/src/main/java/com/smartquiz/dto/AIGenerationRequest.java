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
}