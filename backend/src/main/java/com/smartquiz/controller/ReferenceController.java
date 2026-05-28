package com.smartquiz.controller;

import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.TechnologyStackRepository;
import com.smartquiz.repository.TopicRepository;
import com.smartquiz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    @Autowired private TechnologyStackRepository stackRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private UserRepository userRepository;

    @GetMapping("/stacks")
    public ResponseEntity<?> getAllStacks() {
        return ResponseEntity.ok(stackRepository.findAll().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("stackId", s.getStackId());
            m.put("stackName", s.getStackName());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/topics/{stackId}")
    public ResponseEntity<?> getTopicsByStack(@PathVariable Long stackId) {
        return ResponseEntity.ok(topicRepository.findByStackStackId(stackId).stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("topicId", t.getTopicId());
            m.put("topicName", t.getTopicName());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("enterpriseId", u.getEnterpriseId());
            m.put("fullName", u.getFullName());
            m.put("role", u.getRole().name());
            return m;
        }).collect(Collectors.toList()));
    }
}
