package com.smartquiz.controller;

import com.smartquiz.dto.QuizDTO;
import com.smartquiz.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/quizzes")
public class QuizAdminController {

    @Autowired private QuizService quizService;

    @GetMapping("/approved-questions")
    public ResponseEntity<?> getApprovedQuestions(
            @RequestParam(required = false) List<Long> stackIds,
            @RequestParam(required = false) List<Long> topicIds,
            @RequestParam(required = false) Long topicId) {
        if (topicId != null && (topicIds == null || topicIds.isEmpty())) {
            topicIds = List.of(topicId);
        }
        return ResponseEntity.ok(quizService.getApprovedQuestionsByFilters(stackIds, topicIds));
    }

    @PostMapping("/auto-pick")
    public ResponseEntity<QuizDTO.AutoPickResponse> autoPickQuestions(@RequestBody QuizDTO.AutoPickRequest request) {
        return ResponseEntity.ok(quizService.autoPickQuestions(request));
    }

    @PostMapping("/publish")
    public ResponseEntity<QuizDTO.PublishedQuizSummary> publishQuiz(
            @RequestBody QuizDTO.PublishRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(quizService.publishQuiz(authentication.getName(), request));
    }

    @GetMapping
    public ResponseEntity<Page<QuizDTO.PublishedQuizSummary>> getPublishedQuizzes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(quizService.getPublishedQuizzes(page, size));
    }

    @GetMapping("/participant-analytics")
    public ResponseEntity<QuizDTO.ParticipantTopicAnalyticsResponse> getParticipantAnalytics(
            @RequestParam(defaultValue = "ALL") String participantType) {
        return ResponseEntity.ok(quizService.getParticipantTopicAnalytics(participantType));
    }

    @GetMapping("/leaderboard/topic/{topicId}")
    public ResponseEntity<QuizDTO.TopicLeaderboardResponse> getTopicLeaderboard(
            @PathVariable Long topicId,
            @RequestParam(defaultValue = "ALL") String participantType) {
        return ResponseEntity.ok(quizService.getTopicLeaderboard(topicId, participantType));
    }

    @GetMapping("/leaderboard/topics")
    public ResponseEntity<List<QuizDTO.HostedTopicOption>> getHostedTopicsForLeaderboard() {
        return ResponseEntity.ok(quizService.getHostedTopicsForLeaderboard());
    }

    @GetMapping("/leaderboard/overall")
    public ResponseEntity<QuizDTO.OverallLeaderboardResponse> getOverallLeaderboard(
            @RequestParam(defaultValue = "ALL") String participantType) {
        return ResponseEntity.ok(quizService.getOverallLeaderboard(participantType));
    }

    @PostMapping("/{quizId}/close")
    public ResponseEntity<QuizDTO.PublishedQuizSummary> closeQuiz(@PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.closeQuiz(quizId));
    }

    @PostMapping("/{quizId}/reopen")
    public ResponseEntity<QuizDTO.PublishedQuizSummary> reopenQuiz(@PathVariable Long quizId) {
        return ResponseEntity.ok(quizService.reopenQuiz(quizId));
    }

    @PostMapping("/{quizId}/rehost")
    public ResponseEntity<QuizDTO.PublishedQuizSummary> rehostQuiz(@PathVariable Long quizId, Authentication authentication) {
        return ResponseEntity.ok(quizService.rehostClosedQuiz(authentication.getName(), quizId));
    }

    @GetMapping("/{quizId}/leaderboard/export")
    public ResponseEntity<byte[]> exportLeaderboard(@PathVariable Long quizId) {
        byte[] content = quizService.exportLeaderboardExcel(quizId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=quiz-" + quizId + "-leaderboard.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
