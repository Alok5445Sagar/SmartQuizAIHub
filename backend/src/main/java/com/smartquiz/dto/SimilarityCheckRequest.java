package com.smartquiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimilarityCheckRequest {
    @NotBlank(message = "Question stem is required") @Size(max = 4000)
    private String questionStem;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String optionE;
    private String optionF;
    @NotNull(message = "Technology stack is required")
    private Long stackId;
    @NotNull(message = "Topic is required")
    private Long topicId;
    // For edit — exclude self
    private Long excludeQuestionId;
}