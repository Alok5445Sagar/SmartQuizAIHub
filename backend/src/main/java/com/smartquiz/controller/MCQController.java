package com.smartquiz.controller;

import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.User;
import com.smartquiz.repository.UserRepository;
import com.smartquiz.service.MCQService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@org.springframework.validation.annotation.Validated
@RestController
@RequestMapping("/api/mcq")
public class MCQController {

    @Autowired private MCQService mcqService;
    @Autowired private UserRepository userRepository;

    private boolean isAdmin(String enterpriseId) {
        return userRepository.findByEnterpriseId(enterpriseId)
                .map(u -> u.getRole() == User.Role.ADMIN)
                .orElse(false);
    }

    @PostMapping
    public ResponseEntity<?> createMCQ(@jakarta.validation.Valid @RequestBody MCQDTO.CreateRequest req, Authentication auth) {
        return ResponseEntity.ok(mcqService.createMCQ(auth.getName(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMCQ(@PathVariable Long id,
                                        @jakarta.validation.Valid @RequestBody MCQDTO.UpdateRequest req,
                                        Authentication auth) {
        return ResponseEntity.ok(mcqService.updateMCQ(auth.getName(), id, req, isAdmin(auth.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMCQ(@PathVariable Long id, Authentication auth) {
        mcqService.deleteMCQ(auth.getName(), id, isAdmin(auth.getName()));
        return ResponseEntity.ok(Map.of("message", "Question deleted successfully"));
    }

    @GetMapping("/my-questions/stats")
    public ResponseEntity<Map<String, Long>> getMyQuestionStats(
            @RequestParam(required = false) Long stackId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String difficulty,
            Authentication auth) {
        return ResponseEntity.ok(mcqService.getMyQuestionStats(auth.getName(), stackId, topicId, difficulty));
    }

    @GetMapping("/my-questions")
    public ResponseEntity<Page<MCQDTO.Response>> getMyQuestions(
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size,
            @RequestParam(required = false) Long stackId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "en") String languageCode,
            Authentication auth) {
        return ResponseEntity.ok(mcqService.getMyQuestions(
                auth.getName(), page, size, stackId, topicId, difficulty, status, languageCode));
    }

    @GetMapping("/my-pending-reviews")
    public ResponseEntity<Page<MCQDTO.Response>> getMyPendingReviews(
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size,
            @RequestParam(required = false, defaultValue = "en") String languageCode,
            Authentication auth) {
        return ResponseEntity.ok(mcqService.getMyPendingReviews(auth.getName(), page, size, languageCode));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMCQById(@PathVariable Long id,
                                        @RequestParam(required = false, defaultValue = "en") String languageCode) {
        return ResponseEntity.ok(mcqService.getMCQById(id, languageCode));
    }

    @GetMapping("/{id}/translations")
    public ResponseEntity<?> getTranslations(@PathVariable Long id) {
        return ResponseEntity.ok(mcqService.getTranslations(id));
    }

    @PostMapping("/{id}/translations/suggest")
    public ResponseEntity<?> suggestTranslation(
            @PathVariable Long id,
            @RequestParam String languageCode) {
        return ResponseEntity.ok(mcqService.suggestTranslation(id, languageCode));
    }

    @PutMapping("/{id}/translations")
    public ResponseEntity<?> saveTranslation(
            @PathVariable Long id,
            @RequestBody MCQDTO.TranslationRequest request,
            Authentication auth) {
        return ResponseEntity.ok(mcqService.saveTranslation(auth.getName(), id, request));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<?> reviewMCQ(@PathVariable Long id,
                                        @jakarta.validation.Valid @RequestBody MCQDTO.ReviewRequest req,
                                        Authentication auth) {
        return ResponseEntity.ok(mcqService.reviewMCQ(auth.getName(), id, req));
    }

    @PostMapping("/bulk-upload")
public ResponseEntity<?> bulkUpload(@RequestParam("file") MultipartFile file,
                                     Authentication auth) throws IOException {
    try {
        return ResponseEntity.ok(mcqService.bulkUpload(auth.getName(), file));
    } catch (com.smartquiz.service.PartialUploadException e) {
        return ResponseEntity.status(207).body(
            java.util.Map.of(
                "imported", e.getImported(),
                "errors", e.getErrors(),
                "message", e.getImported().size() + " questions imported. " + e.getErrors().size() + " rows had errors."
            )
        );
    }
}

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        return mcqService.downloadTemplate();
    }
}
