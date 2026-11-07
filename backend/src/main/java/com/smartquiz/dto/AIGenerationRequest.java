package com.smartquiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIGenerationRequest {
    @NotNull(message = "Technology stack is required")
    private Long stackId;
    @NotNull(message = "Topic is required")
    private Long topicId;
    @NotBlank(message = "Difficulty is required") @Pattern(regexp = "Easy|Medium|Hard", message = "Difficulty must be Easy, Medium or Hard")
    private String difficulty;
    @Min(value = 1, message = "Number of questions must be between 1 and 20") @Max(value = 20, message = "Number of questions must be between 1 and 20")
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