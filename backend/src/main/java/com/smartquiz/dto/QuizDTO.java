package com.smartquiz.dto;

import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.MCQTranslation;
import com.smartquiz.entity.Quiz;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class QuizDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishRequest {
        private Long topicId;
        private List<Long> topicIds;
        private Integer totalQuestions;
        private Integer mcqCount;
        private Integer msqCount;
        @Min(value = 1, message = "Quiz duration must be greater than zero minutes") @Max(value = 600, message = "Quiz duration cannot exceed 600 minutes")
        private Integer durationMinutes;
        @Size(max = 300, message = "Title cannot exceed 300 characters")
        private String title;
        @NotEmpty(message = "Please select quiz questions") @Size(max = 200, message = "A quiz cannot have more than 200 questions")
        private List<Long> questionIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoPickRequest {
        private Long topicId;
        private List<Long> topicIds;
        private List<Long> stackIds;
        private Integer totalQuestions;
        private Integer mcqCount;
        private Integer msqCount;
        private Integer easyCount;
        private Integer mediumCount;
        private Integer hardCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoPickResponse {
        private List<Long> questionIds;
        private int selectedCount;
        private int unusedSelectedCount;
        private int reusedSelectedCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionChoice {
        private Long questionId;
        private String questionStem;
        private String questionType;
        private String difficulty;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String optionE;
        private String optionF;

        public static QuestionChoice from(MCQ mcq) {
            return new QuestionChoice(
                    mcq.getQuestionId(),
                    mcq.getQuestionStem(),
                    mcq.getQuestionType() != null ? mcq.getQuestionType().name() : "MCQ",
                    mcq.getDifficulty().name(),
                    mcq.getOptionA(),
                    mcq.getOptionB(),
                    mcq.getOptionC(),
                    mcq.getOptionD(),
                    mcq.getOptionE(),
                    mcq.getOptionF()
            );
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishedQuizSummary {
        private Long quizId;
        private String quizCode;
        private String title;
        private Long stackId;
        private Long topicId;
        private String stackName;
        private String topicName;
        private String status;
        private String quizLink;
        private LocalDateTime publishedAt;
        private LocalDateTime closedAt;
        private int questionCount;
        private int durationMinutes;

        public static PublishedQuizSummary from(Quiz quiz, String link, int questionCount, int durationMinutes) {
            return new PublishedQuizSummary(
                    quiz.getQuizId(),
                    quiz.getQuizCode(),
                    quiz.getTitle(),
                    quiz.getStack() == null ? null : quiz.getStack().getStackId(),
                    quiz.getTopic() == null ? null : quiz.getTopic().getTopicId(),
                    quiz.getStack().getStackName(),
                    quiz.getTopic().getTopicName(),
                    quiz.getStatus().name(),
                    link,
                    quiz.getPublishedAt(),
                    quiz.getClosedAt(),
                    questionCount,
                    durationMinutes
            );
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantQuestion {
        private Long questionId;
        private String questionStem;
        private String questionType;
        private Integer msqRequiredSelections;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String optionE;
        private String optionF;

        public static ParticipantQuestion from(MCQ mcq) {
            return new ParticipantQuestion(
                    mcq.getQuestionId(),
                    mcq.getQuestionStem(),
                    mcq.getQuestionType() != null ? mcq.getQuestionType().name() : "MCQ",
                    resolveMsqRequiredSelections(mcq),
                    mcq.getOptionA(),
                    mcq.getOptionB(),
                    mcq.getOptionC(),
                    mcq.getOptionD(),
                    mcq.getOptionE(),
                    mcq.getOptionF()
            );
        }

        public static ParticipantQuestion from(MCQ mcq, MCQTranslation translation) {
            if (translation == null) {
                return from(mcq);
            }

            return new ParticipantQuestion(
                mcq.getQuestionId(),
                translation.getQuestionStem(),
                mcq.getQuestionType() != null ? mcq.getQuestionType().name() : "MCQ",
                resolveMsqRequiredSelections(mcq),
                translation.getOptionA(),
                translation.getOptionB(),
                translation.getOptionC(),
                translation.getOptionD(),
                translation.getOptionE(),
                translation.getOptionF()
            );
        }

        private static Integer resolveMsqRequiredSelections(MCQ mcq) {
            if (mcq.getQuestionType() != MCQ.QuestionType.MSQ || mcq.getCorrectAnswer() == null) {
                return null;
            }
            return (int) java.util.Arrays.stream(mcq.getCorrectAnswer().split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(value -> value.matches("^[A-F]$"))
                    .distinct()
                    .count();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicQuizResponse {
        private Long quizId;
        private String quizCode;
        private String title;
        private String stackName;
        private String topicName;
        private String status;
        private boolean canAttempt;
        private boolean alreadyAttempted;
        private String participantNotice;
        private int questionCount;
        private int durationMinutes;
        private List<ParticipantQuestion> questions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantLoginRequest {
        private String enterpriseId;
        private String password;
        @Size(max = 200, message = "Participant name cannot exceed 200 characters")
        private String participantName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantLoginResponse {
        private String enterpriseId;
        private String fullName;
        private String authToken;
        private String participantType;
        private String preferredLanguage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantPortalQuizCard {
        private Long quizId;
        private String quizCode;
        private String title;
        private String stackName;
        private String topicName;
        private String status;
        private Integer durationMinutes;
        private LocalDateTime publishedAt;
        private LocalDateTime closedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantAttemptHistoryRow {
        private Long attemptId;
        private Long quizId;
        private String quizCode;
        private String quizTitle;
        private String stackName;
        private String topicName;
        private Integer score;
        private Integer totalQuestions;
        private Integer timeTakenSeconds;
        private LocalDateTime submittedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantSuggestion {
        private String stackName;
        private String topicName;
        private String message;
        private double averageScorePercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantPortalDashboardResponse {
        private String participantName;
        private String participantEnterpriseId;
        private String participantType;
        private int liveQuizCount;
        private int attemptedQuizCount;
        private List<ParticipantPortalQuizCard> liveQuizzes;
        private List<ParticipantAttemptHistoryRow> previousAttempts;
        private List<ParticipantSuggestion> suggestions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantChatUserSummary {
        private String enterpriseId;
        private String fullName;
        private String role;
        private boolean online;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantChatUsersResponse {
        private String selfEnterpriseId;
        private List<ParticipantChatUserSummary> users;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantChatMessageView {
        private Long messageId;
        private String senderEnterpriseId;
        private String senderName;
        private String recipientEnterpriseId;
        private String recipientName;
        private String message;
        private Boolean hasAttachment;
        private String attachmentName;
        private String attachmentContentType;
        private Long attachmentSizeBytes;
        private LocalDateTime createdAt;
        private LocalDateTime deliveredAt;
        private LocalDateTime readAt;
        private String deliveryStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantChatHistoryResponse {
        private String selfEnterpriseId;
        private String withEnterpriseId;
        private List<ParticipantChatMessageView> messages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantChatMessageRequest {
        private String recipientEnterpriseId;
        @Size(max = 2000, message = "Message text exceeds allowed length")
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantChatTypingRequest {
        private String withEnterpriseId;
        private Boolean typing;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantChatReadRequest {
        private String withEnterpriseId;
        private Long lastSeenMessageId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantChatStreamEvent {
        private String eventType;
        private ParticipantChatMessageView message;
        private List<ParticipantChatUserSummary> users;
        private String withEnterpriseId;
        private String typingEnterpriseId;
        private Boolean typing;
        private Long messageId;
        private String messageStatus;
        private LocalDateTime readAt;
        private LocalDateTime serverTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitAttemptRequest {
        /** From POST /start. Identity and timing come from the server-side session, not this body. */
        @Size(max = 36)
        private String attemptToken;
        @Size(max = 200)
        private List<AnswerRequest> answers;
        private Boolean autoSubmitted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptStartRequest {
        /** Guests only; employees are identified by their JWT. */
        @Size(max = 200, message = "Participant name cannot exceed 200 characters")
        private String participantName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptStartResponse {
        private String attemptToken;
        private String participantName;
        private int durationSeconds;
        private int elapsedSeconds;
        private int remainingSeconds;
        /** True when this participant had already started; the clock keeps running from the first start. */
        private boolean resumed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerRequest {
        private Long questionId;
        private List<String> selectedAnswers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptResultResponse {
        private Long attemptId;
        private String participantName;
        private String participantEnterpriseId;
        private int score;
        private int totalQuestions;
        private int correctCount;
        private int timeTakenSeconds;
        private boolean autoSubmitted;
        private LocalDateTime submittedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardEntry {
        private int rank;
        private String rankBadge;
        private String participantName;
        private String participantEnterpriseId;
        private int score;
        private int totalQuestions;
        private int attemptedCount;
        private int correctCount;
        private int wrongCount;
        private int timeTakenSeconds;
        private LocalDateTime submittedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardResponse {
        private String quizCode;
        private String title;
        private String status;
        private int totalParticipants;
        private List<LeaderboardEntry> leaderboard;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantTopicAnalyticsRow {
        private String participantName;
        private String participantEnterpriseId;
        private String participantType;
        private String stackName;
        private String topicName;
        private int hostedQuizCount;
        private int attendedQuizCount;
        private int correctAnswers;
        private int totalQuestions;
        private double engagementPercent;
        private double performancePercent;
        private String recommendation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantTopicAnalyticsResponse {
        private String participantType;
        private int totalHostedQuizzes;
        private int totalRows;
        private List<ParticipantTopicAnalyticsRow> rows;
        private QuizAnalyticsSummary quizAnalytics;
        private List<TopicPerformanceRow> topicPerformance;
        private List<QuestionAnalyticsRow> questionAnalytics;
        private UserEngagementSummary userEngagement;
        private List<KnowledgeGapInsight> knowledgeGaps;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizAnalyticsSummary {
        private int totalQuizzesConducted;
        private int totalParticipants;
        private double averageScorePercent;
        private double highestScorePercent;
        private double lowestScorePercent;
        private double completionRatePercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicPerformanceRow {
        private Long topicId;
        private String stackName;
        private String topicName;
        private double averageScorePercent;
        private String status;
        private int attemptCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionAnalyticsRow {
        private Long questionId;
        private String questionStem;
        private String stackName;
        private String topicName;
        private int attemptedCount;
        private int incorrectCount;
        private double successRatePercent;
        private String difficultySignal;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngagementUserRow {
        private String participantName;
        private String participantEnterpriseId;
        private int attemptsCount;
        private int distinctQuizzes;
        private int distinctTopics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngagementTopicRow {
        private String stackName;
        private String topicName;
        private int attemptsCount;
        private int distinctParticipants;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngagementQuizRow {
        private Long quizId;
        private String quizCode;
        private String title;
        private String stackName;
        private String topicName;
        private int participantsCount;
        private int attemptsCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserEngagementSummary {
        private int activeUsers;
        private List<EngagementUserRow> activeParticipants;
        private List<EngagementTopicRow> topTopics;
        private List<EngagementQuizRow> topQuizzes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeGapInsight {
        private String stackName;
        private String topicName;
        private double averageScorePercent;
        private int attemptsCount;
        private int learnersBelow50Percent;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicLeaderboardEntry {
        private int rank;
        private String rankBadge;
        private String participantName;
        private String participantEnterpriseId;
        private int quizzesPlayed;
        private int attemptedCount;
        private int correctCount;
        private int wrongCount;
        private double scorePercent;
        private int averageTimeTakenSeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicLeaderboardResponse {
        private Long topicId;
        private String stackName;
        private String topicName;
        private String participantType;
        private int totalParticipants;
        private List<TopicLeaderboardEntry> leaderboard;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HostedTopicOption {
        private Long topicId;
        private String stackName;
        private String topicName;
        private long quizzesHosted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallLeaderboardEntry {
        private int rank;
        private String rankBadge;
        private String participantName;
        private String participantEnterpriseId;
        private int quizzesPlayed;
        private int topicsCovered;
        private int attemptedCount;
        private int correctCount;
        private int wrongCount;
        private double scorePercent;
        private int averageTimeTakenSeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallLeaderboardResponse {
        private String participantType;
        private int totalParticipants;
        private List<OverallLeaderboardEntry> leaderboard;
    }
}
