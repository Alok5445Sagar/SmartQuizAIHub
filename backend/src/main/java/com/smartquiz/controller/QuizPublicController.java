package com.smartquiz.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.smartquiz.dto.QuizDTO;
import com.smartquiz.exception.ForbiddenException;
import com.smartquiz.exception.UnauthorizedException;
import com.smartquiz.security.ChatStreamTicketService;
import com.smartquiz.service.ParticipantChatService;
import com.smartquiz.service.QuizService;

/**
 * Quiz-taker endpoints. Guests are anonymous and identified by name; employees are identified only by the
 * JWT in the Authorization header (never by an ID or token in the URL or body).
 */
@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/public/quiz")
public class QuizPublicController {

    @Autowired private QuizService quizService;
    @Autowired private ParticipantChatService participantChatService;
    @Autowired private ChatStreamTicketService chatStreamTicketService;

    private static String principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    private static String requirePrincipal(Authentication authentication) {
        String enterpriseId = principal(authentication);
        if (enterpriseId == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        return enterpriseId;
    }

    @GetMapping("/{quizCode}")
    public ResponseEntity<QuizDTO.PublicQuizResponse> getQuiz(
            @PathVariable String quizCode,
            @RequestParam(required = false) String participantName,
            @RequestParam(required = false, defaultValue = "en") String languageCode,
            Authentication authentication) {
        return ResponseEntity.ok(quizService.getPublicQuiz(quizCode, principal(authentication), participantName, languageCode));
    }

    @PostMapping("/{quizCode}/participant-login")
    public ResponseEntity<QuizDTO.ParticipantLoginResponse> participantLogin(
            @PathVariable String quizCode,
            @jakarta.validation.Valid @RequestBody QuizDTO.ParticipantLoginRequest request) {
        return ResponseEntity.ok(quizService.loginParticipant(quizCode, request));
    }

    @PostMapping("/participant/login")
    public ResponseEntity<QuizDTO.ParticipantLoginResponse> participantPortalLogin(
            @jakarta.validation.Valid @RequestBody QuizDTO.ParticipantLoginRequest request) {
        return ResponseEntity.ok(quizService.loginParticipantPortal(request));
    }

    /**
     * Employees: identity from the JWT. Guests: by name, guest attempts only.
     * Asking for an employee's dashboard without (or with someone else's) token is rejected.
     */
    @GetMapping("/participant/dashboard")
    public ResponseEntity<QuizDTO.ParticipantPortalDashboardResponse> getParticipantPortalDashboard(
            @RequestParam(required = false) String participantEnterpriseId,
            @RequestParam(required = false) String participantName,
            @RequestParam(required = false, defaultValue = "en") String languageCode,
            Authentication authentication) {
        String self = principal(authentication);
        if (participantEnterpriseId != null && !participantEnterpriseId.isBlank()) {
            if (self == null) {
                throw new UnauthorizedException("Employee login is required to view this dashboard");
            }
            if (!participantEnterpriseId.trim().equals(self)) {
                throw new ForbiddenException("You can only view your own dashboard");
            }
        }
        return ResponseEntity.ok(quizService.getParticipantPortalDashboard(self, participantName, languageCode));
    }

    @PostMapping("/{quizCode}/attempt")
    public ResponseEntity<QuizDTO.AttemptResultResponse> submitAttempt(
            @PathVariable String quizCode,
            @jakarta.validation.Valid @RequestBody QuizDTO.SubmitAttemptRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(quizService.submitAttempt(quizCode, request, principal(authentication)));
    }

    @GetMapping("/{quizCode}/leaderboard")
    public ResponseEntity<QuizDTO.LeaderboardResponse> getLeaderboard(@PathVariable String quizCode) {
        return ResponseEntity.ok(quizService.getLeaderboard(quizCode));
    }

    // ---------------------------------------------------------------- chat (authenticated)

    @GetMapping("/participant/chat/users")
    public ResponseEntity<QuizDTO.ParticipantChatUsersResponse> getParticipantChatUsers(Authentication authentication) {
        return ResponseEntity.ok(participantChatService.getChatUsers(requirePrincipal(authentication)));
    }

    @GetMapping("/participant/chat/history")
    public ResponseEntity<QuizDTO.ParticipantChatHistoryResponse> getParticipantChatHistory(
            @RequestParam String withEnterpriseId,
            @RequestParam(required = false, defaultValue = "60") Integer limit,
            Authentication authentication) {
        return ResponseEntity.ok(participantChatService.getConversationHistory(
                requirePrincipal(authentication), withEnterpriseId, limit));
    }

    @PostMapping("/participant/chat/send")
    public ResponseEntity<QuizDTO.ParticipantChatMessageView> sendParticipantChatMessage(
            @jakarta.validation.Valid @RequestBody QuizDTO.ParticipantChatMessageRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(participantChatService.sendMessage(requirePrincipal(authentication), request));
    }

    @PostMapping(value = "/participant/chat/send-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QuizDTO.ParticipantChatMessageView> sendParticipantChatAttachment(
            @RequestParam String recipientEnterpriseId,
            @RequestParam(required = false) String message,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(participantChatService.sendAttachment(
                requirePrincipal(authentication), recipientEnterpriseId, message, file));
    }

    @PostMapping("/participant/chat/typing")
    public ResponseEntity<Void> publishParticipantTyping(
            @RequestBody QuizDTO.ParticipantChatTypingRequest request,
            Authentication authentication) {
        participantChatService.publishTyping(requirePrincipal(authentication), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/participant/chat/read")
    public ResponseEntity<Void> markParticipantChatRead(
            @RequestBody QuizDTO.ParticipantChatReadRequest request,
            Authentication authentication) {
        participantChatService.markConversationRead(requirePrincipal(authentication), request);
        return ResponseEntity.ok().build();
    }

    /** Exchanges the caller's JWT for a one-time ticket used to open the SSE stream. */
    @PostMapping("/participant/chat/stream-ticket")
    public ResponseEntity<Map<String, Object>> createStreamTicket(Authentication authentication) {
        String ticket = chatStreamTicketService.issue(requirePrincipal(authentication));
        return ResponseEntity.ok(Map.of(
                "ticket", ticket,
                "expiresInSeconds", ChatStreamTicketService.TICKET_TTL.toSeconds()));
    }

    @GetMapping(value = "/participant/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamParticipantChat(@RequestParam String ticket) {
        return participantChatService.openStream(chatStreamTicketService.redeem(ticket));
    }

    @GetMapping("/participant/chat/attachment/{messageId}")
    public ResponseEntity<Resource> downloadParticipantChatAttachment(
            @PathVariable Long messageId,
            Authentication authentication) {
        ParticipantChatService.ChatAttachmentDownload download = participantChatService.downloadAttachment(
                messageId, requirePrincipal(authentication));

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.getResource());
    }
}
