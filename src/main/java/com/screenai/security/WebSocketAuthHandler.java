package com.screenai.security;

import com.screenai.service.JwtService;
import com.screenai.service.SecurityAuditService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Handles WebSocket authentication by validating JWT tokens.
 * Tokens must be provided via Authorization header: Bearer <JWT>
 */
@Component
public class WebSocketAuthHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthHandler.class);

    private final JwtService jwtService;
    private final SecurityAuditService auditService;

    public WebSocketAuthHandler(JwtService jwtService, SecurityAuditService auditService) {
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /**
     * Authenticate WebSocket connection from handshake headers.
     * If a valid JWT is provided, returns the authenticated user.
     * If no token is provided, returns a guest user (TeamViewer-style anonymous
     * access).
     * If an invalid token is provided, returns empty (connection will be rejected).
     */
    public Mono<AuthenticatedUser> authenticate(HttpHeaders headers, String sessionId, String ipAddress) {
        String token = extractTokenFromHeaders(headers);

        // No token → allow as guest (TeamViewer-style: no login required)
        if (token == null || token.isEmpty()) {
            String guestUsername = "guest_" + sessionId.substring(0, Math.min(8, sessionId.length()));
            log.info("Guest WebSocket connection: {} from IP: {}", guestUsername, ipAddress);
            return Mono.just(new AuthenticatedUser(guestUsername, "GUEST", null));
        }

        // Token provided → validate it
        try {
            Claims claims = jwtService.validateToken(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            if (username != null) {
                log.debug("WebSocket authenticated for user: {}", username);
                return Mono.just(new AuthenticatedUser(username, role, token));
            }
        } catch (JwtException e) {
            log.debug("WebSocket authentication failed (invalid token): {}", e.getMessage());
            auditService.logInvalidToken(sessionId, ipAddress, e.getMessage()).subscribe();
        }

        // Invalid token → reject (do NOT fall back to guest)
        return Mono.empty();
    }

    /**
     * Extract bearer token from Authorization header.
     */
    private String extractTokenFromHeaders(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }

        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (!authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring("Bearer ".length()).trim();
    }

    /**
     * Validate an existing token.
     */
    public boolean isTokenValid(String token) {
        return jwtService.isTokenValid(token);
    }

    /**
     * Extract username from token.
     */
    public Optional<String> extractUsername(String token) {
        try {
            return Optional.of(jwtService.extractUsername(token));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    /**
     * Authenticated user details.
     */
    public record AuthenticatedUser(String username, String role, String token) {
        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }
}
