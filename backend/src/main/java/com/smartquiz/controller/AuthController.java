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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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
        String token = jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());

        return ResponseEntity.ok(new AuthDTO.LoginResponse(
                token,
                user.getEnterpriseId(),
                user.getFullName(),
                user.getRole().name()
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
                user.getRole().name()
        ));
    }
}
