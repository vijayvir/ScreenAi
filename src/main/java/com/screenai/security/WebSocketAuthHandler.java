package com.screenai.security;

import com.screenai.service.JwtService;
import com.screenai.service.SecurityAuditService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;

/**
 * Handles WebSocket authentication by validating JWT tokens.
 * Tokens can be passed via query parameter: ws://host/screenshare?token=JWT
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
     * Authenticate WebSocket connection from URI query params.
     * Returns the authenticated username or empty if authentication fails.
     */
    public Mono<AuthenticatedUser> authenticate(URI uri, String sessionId, String ipAddress) {
        String token = extractTokenFromUri(uri);

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
     * Extract token from WebSocket URI query parameters.
     */
    private String extractTokenFromUri(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }

        String query = uri.getQuery();
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                return keyValue[1];
            }
        }

        return null;
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
