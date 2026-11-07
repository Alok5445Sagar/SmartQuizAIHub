package com.smartquiz.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Single entry point for password logins (platform and participant). Applies the failed-login rate limit
 * per client IP and enterprise ID before delegating to the AuthenticationManager.
 */
@Component
public class LoginAttemptGuard {

    private final AuthenticationManager authenticationManager;
    private final RateLimitService rateLimitService;

    public LoginAttemptGuard(AuthenticationManager authenticationManager, RateLimitService rateLimitService) {
        this.authenticationManager = authenticationManager;
        this.rateLimitService = rateLimitService;
    }

    public Authentication authenticate(String enterpriseId, String password) {
        String clientIp = currentClientIp();
        rateLimitService.checkLoginAllowed(clientIp, enterpriseId);
        try {
            return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(enterpriseId, password));
        } catch (AuthenticationException ex) {
            rateLimitService.recordLoginFailure(clientIp, enterpriseId);
            throw ex;
        }
    }

    private String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            // Behind a reverse proxy, server.forward-headers-strategy makes getRemoteAddr() the real client.
            return attrs.getRequest().getRemoteAddr();
        }
        return "unknown";
    }
}
