package com.smartquiz.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimilarityCheckResponse {
    private boolean hasSimilar;
    private List<SimilarQuestion> similarQuestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarQuestion {
        private Long questionId;
        private int similarityPercent;
        private String questionStem;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String optionE;
        private String optionF;
        private String correctAnswer;
    }
}