package com.smartquiz.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lets a Prometheus scraper read /actuator/prometheus with a static bearer token (METRICS_SCRAPE_TOKEN),
 * since JWTs expire. The token grants ROLE_METRICS on that one path and nothing else. Not a Spring bean on
 * purpose: SecurityConfig adds it to the security chain only, so it is not also registered as a servlet filter.
 */
public class MetricsScrapeTokenFilter extends OncePerRequestFilter {

    public static final String PATH = "/actuator/prometheus";
    static final int MIN_TOKEN_LENGTH = 16;

    private final byte[] token;

    public MetricsScrapeTokenFilter(String token) {
        if (token != null && !token.isBlank() && token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException("app.metrics.scrape-token must be at least " + MIN_TOKEN_LENGTH + " characters");
        }
        this.token = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return token == null || !PATH.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && MessageDigest.isEqual(token, header.substring(7).getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "metrics-scraper", null, List.of(new SimpleGrantedAuthority("ROLE_METRICS"))));
        }
        chain.doFilter(request, response);
    }
}
