package com.smartquiz.dto;

import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.MCQTranslation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

public class MCQDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Question stem is required") @Size(max = 4000, message = "Question stem cannot exceed 4000 characters")
        private String questionStem;
        @Pattern(regexp = "(?i)MCQ|MSQ", message = "Question type must be MCQ or MSQ")
        private String questionType; // "MCQ" or "MSQ"
        @NotNull(message = "Technology stack is required")
        private Long stackId;
        @NotNull(message = "Topic is required")
        private Long topicId;
        @NotBlank(message = "Difficulty is required") @Pattern(regexp = "Easy|Medium|Hard", message = "Difficulty must be Easy, Medium or Hard")
        private String difficulty;
        @NotBlank(message = "Option A is required") @Size(max = 255, message = "Option A cannot exceed 255 characters")
        private String optionA;
        @NotBlank(message = "Option B is required") @Size(max = 255, message = "Option B cannot exceed 255 characters")
        private String optionB;
        @NotBlank(message = "Option C is required") @Size(max = 255, message = "Option C cannot exceed 255 characters")
        private String optionC;
        @NotBlank(message = "Option D is required") @Size(max = 255, message = "Option D cannot exceed 255 characters")
        private String optionD;
        @Size(max = 255, message = "Option E cannot exceed 255 characters")
        private String optionE;
        @Size(max = 255, message = "Option F cannot exceed 255 characters")
        private String optionF;
        @NotBlank(message = "Correct answer is required")
        private String correctAnswer; // "A" for MCQ, "A,C" for MSQ
        private boolean sendForReview;
        private boolean aiGenerated = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @NotBlank(message = "Question stem is required") @Size(max = 4000, message = "Question stem cannot exceed 4000 characters")
        private String questionStem;
        @Pattern(regexp = "(?i)MCQ|MSQ", message = "Question type must be MCQ or MSQ")
        private String questionType;
        @NotNull(message = "Technology stack is required")
        private Long stackId;
        @NotNull(message = "Topic is required")
        private Long topicId;
        @NotBlank(message = "Difficulty is required") @Pattern(regexp = "Easy|Medium|Hard", message = "Difficulty must be Easy, Medium or Hard")
        private String difficulty;
        @NotBlank(message = "Option A is required") @Size(max = 255, message = "Option A cannot exceed 255 characters")
        private String optionA;
        @NotBlank(message = "Option B is required") @Size(max = 255, message = "Option B cannot exceed 255 characters")
        private String optionB;
        @NotBlank(message = "Option C is required") @Size(max = 255, message = "Option C cannot exceed 255 characters")
        private String optionC;
        @NotBlank(message = "Option D is required") @Size(max = 255, message = "Option D cannot exceed 255 characters")
        private String optionD;
        @Size(max = 255, message = "Option E cannot exceed 255 characters")
        private String optionE;
        @Size(max = 255, message = "Option F cannot exceed 255 characters")
        private String optionF;
        @NotBlank(message = "Correct answer is required")
        private String correctAnswer;
        private boolean sendForReview;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long questionId;
        private String questionStem;
        private Long stackId;
        private String stackName;
        private Long topicId;
        private String topicName;
        private String difficulty;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String correctAnswer;
        private String status;
        private String creatorEnterpriseId;
        private String reviewerEnterpriseId;
        private String reviewerComments;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        // In the Response class fields, add:
        private String questionType;
        private String optionE;
        private String optionF;
        private boolean aiGenerated;
        private boolean bulkUploaded;

        public static Response fromEntity(MCQ mcq) {
            Response r = new Response();
            r.setQuestionId(mcq.getQuestionId());
            r.setQuestionStem(mcq.getQuestionStem());
            r.setStackId(mcq.getStack().getStackId());
            r.setStackName(mcq.getStack().getStackName());
            r.setTopicId(mcq.getTopic().getTopicId());
            r.setTopicName(mcq.getTopic().getTopicName());
            r.setDifficulty(mcq.getDifficulty().name());
            r.setOptionA(mcq.getOptionA());
            r.setOptionB(mcq.getOptionB());
            r.setOptionC(mcq.getOptionC());
            r.setOptionD(mcq.getOptionD());
            r.setCorrectAnswer(mcq.getCorrectAnswer());
            r.setQuestionType(mcq.getQuestionType() != null ? mcq.getQuestionType().name() : "MCQ");
            r.setOptionE(mcq.getOptionE());
            r.setOptionF(mcq.getOptionF());
            r.setStatus(mcq.getStatus().name());
            r.setCreatorEnterpriseId(mcq.getCreator().getEnterpriseId());
            r.setReviewerEnterpriseId(mcq.getReviewer() != null ? mcq.getReviewer().getEnterpriseId() : null);
            r.setReviewerComments(mcq.getReviewerComments());
            r.setCreatedAt(mcq.getCreatedAt());
            r.setUpdatedAt(mcq.getUpdatedAt());
            r.setAiGenerated(mcq.isAiGenerated());
            r.setBulkUploaded(mcq.isBulkUploaded());
            return r;
        }

        public static Response fromEntity(MCQ mcq, MCQTranslation translation) {
            Response response = fromEntity(mcq);
            if (translation == null) {
                return response;
            }
            response.setQuestionStem(translation.getQuestionStem());
            response.setOptionA(translation.getOptionA());
            response.setOptionB(translation.getOptionB());
            response.setOptionC(translation.getOptionC());
            response.setOptionD(translation.getOptionD());
            response.setOptionE(translation.getOptionE());
            response.setOptionF(translation.getOptionF());
            return response;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewRequest {
        @NotBlank(message = "Action is required") @Pattern(regexp = "APPROVE|REJECT", message = "Action must be APPROVE or REJECT")
        private String action; // APPROVE or REJECT
        @Size(max = 4000, message = "Comments cannot exceed 4000 characters")
        private String comments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignReviewerRequest {
        @NotBlank(message = "Reviewer is required")
        private String reviewerEnterpriseId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranslationRequest {
        private String languageCode;
        private String questionStem;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String optionE;
        private String optionF;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranslationResponse {
        private Long translationId;
        private Long questionId;
        private String languageCode;
        private String questionStem;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String optionE;
        private String optionF;
        private String translatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static TranslationResponse fromEntity(MCQTranslation translation) {
            return new TranslationResponse(
                translation.getTranslationId(),
                translation.getMcq() == null ? null : translation.getMcq().getQuestionId(),
                translation.getLanguageCode(),
                translation.getQuestionStem(),
                translation.getOptionA(),
                translation.getOptionB(),
                translation.getOptionC(),
                translation.getOptionD(),
                translation.getOptionE(),
                translation.getOptionF(),
                translation.getTranslatedBy() == null ? null : translation.getTranslatedBy().getEnterpriseId(),
                translation.getCreatedAt(),
                translation.getUpdatedAt()
            );
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranslationListResponse {
        private Long questionId;
        private List<TranslationResponse> translations;
    }
}
