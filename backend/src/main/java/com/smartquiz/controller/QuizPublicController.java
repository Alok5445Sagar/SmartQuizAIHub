package com.smartquiz.controller;

import com.smartquiz.dto.QuizDTO;
import com.smartquiz.service.ParticipantChatService;
import com.smartquiz.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/public/quiz")
public class QuizPublicController {

    @Autowired private QuizService quizService;
    @Autowired private ParticipantChatService participantChatService;

    @GetMapping("/{quizCode}")
    public ResponseEntity<QuizDTO.PublicQuizResponse> getQuiz(
            @PathVariable String quizCode,
            @RequestParam(required = false) String participantEnterpriseId,
            @RequestParam(required = false) String participantName,
            @RequestParam(required = false, defaultValue = "en") String languageCode) {
        return ResponseEntity.ok(quizService.getPublicQuiz(quizCode, participantEnterpriseId, participantName, languageCode));
    }

    @PostMapping("/{quizCode}/participant-login")
    public ResponseEntity<QuizDTO.ParticipantLoginResponse> participantLogin(
            @PathVariable String quizCode,
            @RequestBody QuizDTO.ParticipantLoginRequest request) {
        return ResponseEntity.ok(quizService.loginParticipant(quizCode, request));
    }

    @PostMapping("/participant/login")
    public ResponseEntity<QuizDTO.ParticipantLoginResponse> participantPortalLogin(
            @RequestBody QuizDTO.ParticipantLoginRequest request) {
        return ResponseEntity.ok(quizService.loginParticipantPortal(request));
    }

    @GetMapping("/participant/dashboard")
    public ResponseEntity<QuizDTO.ParticipantPortalDashboardResponse> getParticipantPortalDashboard(
            @RequestParam(required = false) String participantEnterpriseId,
            @RequestParam(required = false) String participantName,
            @RequestParam(required = false, defaultValue = "en") String languageCode) {
        return ResponseEntity.ok(quizService.getParticipantPortalDashboard(participantEnterpriseId, participantName, languageCode));
    }

    @PostMapping("/{quizCode}/attempt")
    public ResponseEntity<QuizDTO.AttemptResultResponse> submitAttempt(
            @PathVariable String quizCode,
            @RequestBody QuizDTO.SubmitAttemptRequest request) {
        return ResponseEntity.ok(quizService.submitAttempt(quizCode, request));
    }

    @GetMapping("/{quizCode}/leaderboard")
    public ResponseEntity<QuizDTO.LeaderboardResponse> getLeaderboard(@PathVariable String quizCode) {
        return ResponseEntity.ok(quizService.getLeaderboard(quizCode));
    }

    @GetMapping("/participant/chat/users")
    public ResponseEntity<QuizDTO.ParticipantChatUsersResponse> getParticipantChatUsers(
            @RequestParam String participantEnterpriseId,
            @RequestParam String participantAuthToken) {
        return ResponseEntity.ok(participantChatService.getChatUsers(participantEnterpriseId, participantAuthToken));
    }

    @GetMapping("/participant/chat/history")
    public ResponseEntity<QuizDTO.ParticipantChatHistoryResponse> getParticipantChatHistory(
            @RequestParam String participantEnterpriseId,
            @RequestParam String participantAuthToken,
            @RequestParam String withEnterpriseId,
            @RequestParam(required = false, defaultValue = "60") Integer limit) {
        return ResponseEntity.ok(participantChatService.getConversationHistory(
                participantEnterpriseId,
                participantAuthToken,
                withEnterpriseId,
                limit
        ));
    }

    @PostMapping("/participant/chat/send")
    public ResponseEntity<QuizDTO.ParticipantChatMessageView> sendParticipantChatMessage(
            @RequestBody QuizDTO.ParticipantChatMessageRequest request) {
        return ResponseEntity.ok(participantChatService.sendMessage(request));
    }

    @PostMapping(value = "/participant/chat/send-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QuizDTO.ParticipantChatMessageView> sendParticipantChatAttachment(
            @RequestParam String participantEnterpriseId,
            @RequestParam String participantAuthToken,
            @RequestParam String recipientEnterpriseId,
            @RequestParam(required = false) String message,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(participantChatService.sendAttachment(
                participantEnterpriseId,
                participantAuthToken,
                recipientEnterpriseId,
                message,
                file
        ));
    }

    @PostMapping("/participant/chat/typing")
    public ResponseEntity<Void> publishParticipantTyping(
            @RequestBody QuizDTO.ParticipantChatTypingRequest request) {
        participantChatService.publishTyping(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/participant/chat/read")
    public ResponseEntity<Void> markParticipantChatRead(
            @RequestBody QuizDTO.ParticipantChatReadRequest request) {
        participantChatService.markConversationRead(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/participant/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamParticipantChat(
            @RequestParam String participantEnterpriseId,
            @RequestParam String participantAuthToken) {
        return participantChatService.openStream(participantEnterpriseId, participantAuthToken);
    }

    @GetMapping("/participant/chat/attachment/{messageId}")
    public ResponseEntity<Resource> downloadParticipantChatAttachment(
            @PathVariable Long messageId,
            @RequestParam String participantEnterpriseId,
            @RequestParam String participantAuthToken) {
        ParticipantChatService.ChatAttachmentDownload download = participantChatService.downloadAttachment(
                messageId,
                participantEnterpriseId,
                participantAuthToken
        );

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.getFileName() + "\"")
                .body(download.getResource());
    }
}
