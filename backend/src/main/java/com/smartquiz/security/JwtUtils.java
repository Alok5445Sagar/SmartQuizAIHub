package com.smartquiz.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    private static final int MIN_SECRET_BYTES = 32;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    /** Token lifetime in milliseconds (default 2 hours). */
    @Value("${app.jwt.expiration:7200000}")
    private long jwtExpiration = 7_200_000L;

    @Value("${app.jwt.issuer:smart-quiz-hub}")
    private String issuer = "smart-quiz-hub";

    @Value("${app.jwt.audience:smart-quiz-hub-api}")
    private String audience = "smart-quiz-hub-api";

    /**
     * Fail fast on startup when the signing secret is missing or too weak for HS256.
     */
    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured. Set the JWT_SECRET environment variable.");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long.");
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String enterpriseId, String role) {
        return Jwts.builder()
                .setSubject(enterpriseId)
                .setIssuer(issuer)
                .setAudience(audience)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseClaimsJws(token);
    }

    public String getEnterpriseIdFromToken(String token) {
        return parse(token).getBody().getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
