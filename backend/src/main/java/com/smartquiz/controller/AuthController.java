package com.smartquiz.controller;

import com.smartquiz.dto.AuthDTO;
import com.smartquiz.entity.User;
import com.smartquiz.repository.UserRepository;
import com.smartquiz.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final java.util.Set<String> SUPPORTED_LANGUAGES = java.util.Set.of("en", "es", "fr", "ro");

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDTO.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEnterpriseId(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEnterpriseId(request.getEnterpriseId()).orElseThrow();
        if (user.getRole() == User.Role.PARTICIPANT) {
            throw new RuntimeException("Participant accounts can only be used on the live quiz page");
        }
        String token = jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());

        return ResponseEntity.ok(new AuthDTO.LoginResponse(
                token,
                user.getEnterpriseId(),
                user.getFullName(),
            user.getRole().name(),
            normalizeLanguageCode(user.getPreferredLanguage())
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        String enterpriseId = authentication.getName();
        User user = userRepository.findByEnterpriseId(enterpriseId).orElseThrow();
        return ResponseEntity.ok(new AuthDTO.LoginResponse(
                null,
                user.getEnterpriseId(),
                user.getFullName(),
                user.getRole().name(),
                normalizeLanguageCode(user.getPreferredLanguage())
        ));
    }

    @PutMapping("/language")
    public ResponseEntity<?> updatePreferredLanguage(@RequestBody AuthDTO.UpdateLanguageRequest request,
                                                     Authentication authentication) {
        if (request == null) {
            throw new RuntimeException("Language request is required");
        }
        String enterpriseId = authentication.getName();
        User user = userRepository.findByEnterpriseId(enterpriseId).orElseThrow();
        user.setPreferredLanguage(normalizeLanguageCode(request.getPreferredLanguage()));
        User saved = userRepository.save(user);
        return ResponseEntity.ok(new AuthDTO.LoginResponse(
                null,
                saved.getEnterpriseId(),
                saved.getFullName(),
                saved.getRole().name(),
                normalizeLanguageCode(saved.getPreferredLanguage())
        ));
    }

    private String normalizeLanguageCode(String code) {
        if (code == null || code.isBlank()) {
            return "en";
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_LANGUAGES.contains(normalized) ? normalized : "en";
    }
}
