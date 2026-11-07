package com.smartquiz.security;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartquiz.exception.TooManyRequestsException;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.EstimationProbe;

/**
 * In-memory token buckets (single instance). Failed logins are limited per client IP and enterprise ID,
 * chat messages per sender. A multi-instance deployment would move these buckets to Redis.
 */
@Component
public class RateLimitService {

    private final int loginFailuresPerMinute;
    private final int chatMessagesPerMinute;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public RateLimitService(@Value("${app.rate-limit.login-failures-per-minute:5}") int loginFailuresPerMinute,
                            @Value("${app.rate-limit.chat-messages-per-minute:30}") int chatMessagesPerMinute) {
        this.loginFailuresPerMinute = loginFailuresPerMinute;
        this.chatMessagesPerMinute = chatMessagesPerMinute;
    }

    /** Throws 429 once the caller has used up its failed-login allowance. */
    public void checkLoginAllowed(String clientIp, String enterpriseId) {
        Bucket bucket = loginBucket(clientIp, enterpriseId);
        if (bucket.getAvailableTokens() <= 0) {
            throw tooMany("Too many failed login attempts. Try again in a minute.", bucket);
        }
    }

    public void recordLoginFailure(String clientIp, String enterpriseId) {
        loginBucket(clientIp, enterpriseId).tryConsume(1);
    }

    public void consumeChatMessage(String senderEnterpriseId) {
        Bucket bucket = bucket("chat:" + normalize(senderEnterpriseId), chatMessagesPerMinute);
        if (!bucket.tryConsume(1)) {
            throw tooMany("You are sending messages too quickly. Slow down and try again.", bucket);
        }
    }

    private Bucket loginBucket(String clientIp, String enterpriseId) {
        return bucket("login:" + normalize(clientIp) + "|" + normalize(enterpriseId), loginFailuresPerMinute);
    }

    private Bucket bucket(String key, int perMinute) {
        return buckets.get(key, unused -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build());
    }

    private TooManyRequestsException tooMany(String message, Bucket bucket) {
        EstimationProbe probe = bucket.estimateAbilityToConsume(1);
        long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        return new TooManyRequestsException(message, waitSeconds);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
