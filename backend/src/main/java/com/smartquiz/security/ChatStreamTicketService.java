package com.smartquiz.security;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartquiz.exception.UnauthorizedException;

/**
 * EventSource cannot send an Authorization header, so the browser first exchanges its JWT for a
 * one-time ticket (valid 30 s) and opens the SSE stream with that ticket instead of the token.
 */
@Component
public class ChatStreamTicketService {

    public static final Duration TICKET_TTL = Duration.ofSeconds(30);

    private final Cache<String, String> tickets = Caffeine.newBuilder()
            .expireAfterWrite(TICKET_TTL)
            .maximumSize(50_000)
            .build();

    public String issue(String enterpriseId) {
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, enterpriseId);
        return ticket;
    }

    /** Returns the enterprise ID for the ticket and invalidates it. */
    public String redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new UnauthorizedException("Chat stream ticket is required");
        }
        String enterpriseId = tickets.asMap().remove(ticket);
        if (enterpriseId == null) {
            throw new UnauthorizedException("Chat stream ticket is invalid or expired");
        }
        return enterpriseId;
    }
}
