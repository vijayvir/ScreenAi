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
     * Returns the authenticated username or empty if authentication fails.
     */
    public Mono<AuthenticatedUser> authenticate(HttpHeaders headers, String sessionId, String ipAddress) {
        String token = extractTokenFromHeaders(headers);

        if (token == null || token.isEmpty()) {
            log.debug("No token provided for WebSocket connection");
            return Mono.empty();
        }

        try {
            Claims claims = jwtService.validateToken(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            if (username != null) {
                log.debug("WebSocket authenticated for user: {}", username);
                return Mono.just(new AuthenticatedUser(username, role, token));
            }
        } catch (JwtException e) {
            log.debug("WebSocket authentication failed: {}", e.getMessage());
            auditService.logInvalidToken(sessionId, ipAddress, e.getMessage()).subscribe();
        }

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
