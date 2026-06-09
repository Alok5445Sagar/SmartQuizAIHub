package com.smartquiz.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartquiz.ai.AIService;
import com.smartquiz.dto.AIGenerationRequest;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.dto.SimilarityCheckRequest;
import com.smartquiz.dto.SimilarityCheckResponse;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.repository.TechnologyStackRepository;
import com.smartquiz.repository.TopicRepository;
import com.smartquiz.repository.UserRepository;
import com.smartquiz.service.MCQService;

@RestController
@RequestMapping("/api/ai")
public class AIController {
    @Autowired
    @org.springframework.context.annotation.Lazy
    private AIService aiService;
    @Autowired private MCQService mcqService;
    @Autowired private TechnologyStackRepository stackRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private UserRepository userRepository;

    @PostMapping("/generate")
    public ResponseEntity<?> generateQuestions(
            @RequestBody AIGenerationRequest req,
            Authentication auth) {

        TechnologyStack stack = stackRepository.findById(req.getStackId())
                .orElseThrow(() -> new RuntimeException("Stack not found"));
        Topic topic = topicRepository.findById(req.getTopicId())
                .orElseThrow(() -> new RuntimeException("Topic not found"));

        if (req.getNumberOfQuestions() < 1 || req.getNumberOfQuestions() > 20) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Number of questions must be between 1 and 20"));
        }

        List<MCQDTO.CreateRequest> generated = aiService.generateQuestions(
                stack, topic, req.getDifficulty(),
                req.getNumberOfQuestions(),
                req.getStackId(), req.getTopicId());

        // Save all generated questions as DRAFT
        List<MCQDTO.Response> saved = new ArrayList<>();
        for (MCQDTO.CreateRequest createReq : generated) {
            try {
                createReq.setAiGenerated(true);  // ← add this line
                MCQDTO.Response response = mcqService.createMCQ(auth.getName(), createReq);
                saved.add(response);
            } catch (RuntimeException e) {
                // Skip duplicates that slipped through
            }
        }

        return ResponseEntity.ok(Map.of(
                "generated", saved.size(),
                "requested", req.getNumberOfQuestions(),
                "questions", saved,
                "message", saved.size() + " question(s) generated and saved as Draft."
        ));
    }

    @PostMapping("/similarity-check")
    public ResponseEntity<SimilarityCheckResponse> checkSimilarity(
            @RequestBody SimilarityCheckRequest req) {

        SimilarityCheckResponse response = aiService.checkSimilarity(
                req.getQuestionStem(),
                req.getOptionA(), req.getOptionB(),
                req.getOptionC(), req.getOptionD(),
                req.getOptionE(), req.getOptionF(),
                req.getStackId(), req.getTopicId(),
                req.getExcludeQuestionId());

        return ResponseEntity.ok(response);
    }
}