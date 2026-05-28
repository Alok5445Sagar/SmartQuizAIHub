package com.smartquiz.dto;

import com.smartquiz.entity.MCQ;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

public class MCQDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String questionStem;
        private String questionType; // "MCQ" or "MSQ"
        private Long stackId;
        private Long topicId;
        private String difficulty;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String optionE;
        private String optionF;
        private String correctAnswer; // "A" for MCQ, "A,C" for MSQ
        private boolean sendForReview;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String questionStem;
        private String questionType;
        private Long stackId;
        private Long topicId;
        private String difficulty;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String optionE;
        private String optionF;
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
            return r;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewRequest {
        private String action; // APPROVE or REJECT
        private String comments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignReviewerRequest {
        private String reviewerEnterpriseId;
    }
}
