package com.smartquiz.controller;

import com.smartquiz.dto.MCQDTO;
import com.smartquiz.service.MCQService;
import com.smartquiz.repository.TechnologyStackRepository;
import com.smartquiz.repository.TopicRepository;
import com.smartquiz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private MCQService mcqService;
    @Autowired private TechnologyStackRepository stackRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private UserRepository userRepository;

    @GetMapping("/question-bank")
    public ResponseEntity<Page<MCQDTO.Response>> getAllMCQs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long stackId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "en") String languageCode) {
        return ResponseEntity.ok(mcqService.getAllMCQs(page, size, stackId, topicId, difficulty, status, search, languageCode));
    }

    @GetMapping("/question-bank/stats")
    public ResponseEntity<Map<String, Long>> getQuestionBankStats(
            @RequestParam(required = false) Long stackId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(mcqService.getQuestionBankStats(stackId, topicId, difficulty, search));
    }

    @PostMapping("/assign-reviewer/{questionId}")
    public ResponseEntity<?> assignReviewer(@PathVariable Long questionId,
                                             @RequestBody MCQDTO.AssignReviewerRequest req) {
        return ResponseEntity.ok(mcqService.assignReviewer(questionId, req.getReviewerEnterpriseId()));
    }

    @GetMapping("/reviewers/{stackId}")
    public ResponseEntity<?> getReviewers(@PathVariable Long stackId,
                                           @RequestParam String excludeEnterpriseId) {
        return ResponseEntity.ok(mcqService.getReviewers(stackId, excludeEnterpriseId));
    }

    @PutMapping("/edit-question/{id}")
    public ResponseEntity<?> editQuestion(@PathVariable Long id,
                                        @RequestBody MCQDTO.UpdateRequest req,
                                        org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(mcqService.updateMCQ(authentication.getName(), id, req, true));
    }
}
