package com.smartquiz.controller;

import com.smartquiz.entity.User;
import com.smartquiz.repository.UserRepository;
import com.smartquiz.service.ReferenceDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    @Autowired private ReferenceDataService referenceDataService;
    @Autowired private UserRepository userRepository;

    @GetMapping("/stacks")
    public ResponseEntity<List<ReferenceDataService.StackOption>> getAllStacks() {
        return ResponseEntity.ok(referenceDataService.stacks());
    }

    @GetMapping("/topics/{stackId}")
    public ResponseEntity<List<ReferenceDataService.TopicOption>> getTopicsByStack(@PathVariable Long stackId) {
        return ResponseEntity.ok(referenceDataService.topics(stackId));
    }
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        // Participants are quiz takers, not platform users; never list them here.
        return ResponseEntity.ok(userRepository.findByRoleInOrderByFullNameAsc(List.of(User.Role.SME, User.Role.ADMIN)).stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("enterpriseId", u.getEnterpriseId());
            m.put("fullName", u.getFullName());
            m.put("role", u.getRole().name());
            return m;
        }).collect(Collectors.toList()));
    }
}
