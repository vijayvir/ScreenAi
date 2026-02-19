package com.screenai.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for JWT token generation and validation.
 * Handles both access tokens (short-lived) and refresh token generation.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey signingKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final String issuer;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${security.jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${security.jwt.issuer}") String issuer) {
        
        // If no secret configured, generate a random one (dev mode — tokens won't survive restarts)
        if (secret == null || secret.isBlank()) {
            byte[] randomKey = new byte[64];
            SECURE_RANDOM.nextBytes(randomKey);
            secret = java.util.Base64.getEncoder().encodeToString(randomKey);
            log.warn("⚠️  JWT_SECRET not set — generated random key. Tokens will NOT survive server restarts.");
            log.warn("⚠️  Set JWT_SECRET env var for production use.");
        }
        
        // Ensure secret is at least 256 bits for HS256
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 characters)");
        }
        
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.issuer = issuer;
        
        log.info("JwtService initialized with {} ms access token expiration, {} ms refresh token expiration",
                accessTokenExpiration, refreshTokenExpiration);
    }

    /**
     * Generate an access token for a user.
     */
    public String generateAccessToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("type", "access");
        
        return buildToken(claims, username, accessTokenExpiration);
    }

    /**
     * Generate a refresh token (opaque, stored server-side).
     * This returns a random string, not a JWT.
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Build a JWT token with claims.
     */
    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validate a token and return the claims if valid.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.debug("Invalid token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extract username from token.
     */
    public String extractUsername(String token) {
        return validateToken(token).getSubject();
    }

    /**
     * Extract role from token.
     */
    public String extractRole(String token) {
        return validateToken(token).get("role", String.class);
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }

    /**
     * Validate token without throwing exceptions.
     */
    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Validate token for a specific user.
     */
    public boolean isTokenValidForUser(String token, String username) {
        try {
            String tokenUsername = extractUsername(token);
            return tokenUsername.equals(username) && !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Get access token expiration in seconds (for client response).
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    /**
     * Get refresh token expiration in milliseconds.
     */
    public long getRefreshTokenExpirationMillis() {
        return refreshTokenExpiration;
    }

    /**
     * Extract token from Authorization header.
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
