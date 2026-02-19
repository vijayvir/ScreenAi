package com.screenai.controller;

import com.screenai.dto.*;
import com.screenai.exception.AuthException;
import com.screenai.service.AuthService;
import com.screenai.service.JwtService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * REST controller for authentication endpoints.
 * Handles user registration, login, token refresh, and logout.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * Register a new user.
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            ServerHttpRequest httpRequest) {

        String ipAddress = extractIpAddress(httpRequest);
        log.info("Registration attempt for username: {}", request.getUsername());

        return authService.register(request, ipAddress)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .onErrorResume(AuthException.class, e -> {
                    log.warn("Registration failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(createErrorResponse(e)));
                });
    }

    /**
     * Authenticate user and get tokens.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            ServerHttpRequest httpRequest) {

        String ipAddress = extractIpAddress(httpRequest);
        log.info("Login attempt for username: {}", request.getUsername());

        return authService.login(request, ipAddress)
                .map(ResponseEntity::ok)
                .onErrorResume(AuthException.class, e -> {
                    log.warn("Login failed for {}: {}", request.getUsername(), e.getMessage());
                    HttpStatus status = switch (e.getErrorCode()) {
                        case AUTH_002 -> HttpStatus.FORBIDDEN; // Account locked
                        default -> HttpStatus.UNAUTHORIZED;
                    };
                    return Mono.just(ResponseEntity.status(status).body(createErrorResponse(e)));
                });
    }

    /**
     * Refresh access token using refresh token.
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            ServerHttpRequest httpRequest) {

        String ipAddress = extractIpAddress(httpRequest);
        log.debug("Token refresh attempt");

        return authService.refreshToken(request, ipAddress)
                .map(ResponseEntity::ok)
                .onErrorResume(AuthException.class, e -> {
                    log.warn("Token refresh failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(createErrorResponse(e)));
                });
    }

    /**
     * Logout user (invalidate refresh token).
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<AuthResponse>> logout(
            @RequestHeader("Authorization") String authHeader,
            ServerHttpRequest httpRequest) {

        String ipAddress = extractIpAddress(httpRequest);
        String token = jwtService.extractTokenFromHeader(authHeader);

        if (token == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.message("Invalid authorization header")));
        }

        try {
            String username = jwtService.extractUsername(token);
            log.info("Logout request for username: {}", username);

            return authService.logout(username, ipAddress)
                    .map(ResponseEntity::ok);
        } catch (Exception e) {
            log.warn("Logout failed - invalid token: {}", e.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.message("Invalid token")));
        }
    }

    /**
     * Validate token (useful for clients to check if token is still valid).
     * GET /api/auth/validate
     */
    @GetMapping("/validate")
    public Mono<ResponseEntity<AuthResponse>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = jwtService.extractTokenFromHeader(authHeader);

        if (token == null || !jwtService.isTokenValid(token)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.message("Invalid or expired token")));
        }

        try {
            String username = jwtService.extractUsername(token);
            String role = jwtService.extractRole(token);
            
            AuthResponse response = new AuthResponse();
            response.setUsername(username);
            response.setRole(role);
            response.setMessage("Token is valid");
            
            return Mono.just(ResponseEntity.ok(response));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.message("Invalid token")));
        }
    }

    // ==================== Helper Methods ====================

    private String extractIpAddress(ServerHttpRequest request) {
        // Check for forwarded headers (behind proxy/load balancer)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    private AuthResponse createErrorResponse(AuthException e) {
        AuthResponse response = new AuthResponse();
        response.setMessage(e.getErrorCode().getMessage());
        if (e.getDetails() != null) {
            response.setMessage(e.getDetails());
        }
        return response;
    }
}
