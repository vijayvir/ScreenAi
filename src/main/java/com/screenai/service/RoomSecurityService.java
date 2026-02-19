package com.screenai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Service for room-level security operations.
 * Handles password hashing, verification, and access code generation.
 */
@Service
public class RoomSecurityService {

    private static final Logger log = LoggerFactory.getLogger(RoomSecurityService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    private static final String ACCESS_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Avoid confusing chars

    private final int accessCodeExpirationHours;

    public RoomSecurityService(
            @Value("${security.room.access-code-expiration-hours:24}") int accessCodeExpirationHours) {
        this.accessCodeExpirationHours = accessCodeExpirationHours;
        log.info("RoomSecurityService initialized with access code expiration: {} hours", accessCodeExpirationHours);
    }

    /**
     * Hash a password.
     * Uses BCrypt by default; supports legacy salted SHA-256 when salt is provided.
     */
    public String hashPassword(String password, String salt) {
        if (salt == null || salt.isBlank()) {
            // Current default: BCrypt with embedded salt/work factor.
            return PASSWORD_ENCODER.encode(password);
        }
        // Legacy fallback for older in-memory rooms that used SHA-256+salt.
        return hashPasswordLegacySha256(password, salt);
    }

    /**
     * Verify a password against stored hash.
     */
    public boolean verifyPassword(String inputPassword, String storedHash, String storedSalt) {
        if (inputPassword == null || storedHash == null) {
            return false;
        }

        // BCrypt hashes include salt and cost in the hash itself.
        if (isBcryptHash(storedHash)) {
            return PASSWORD_ENCODER.matches(inputPassword, storedHash);
        }

        // Legacy SHA-256 + salt verification for backward compatibility.
        if (storedSalt == null || storedSalt.isBlank()) {
            return false;
        }
        String inputHash = hashPasswordLegacySha256(inputPassword, storedSalt);
        return timingSafeEquals(inputHash, storedHash);
    }

    private boolean isBcryptHash(String hash) {
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$");
    }

    private String hashPasswordLegacySha256(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = salt + password;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Timing-safe string comparison to prevent timing attacks.
     */
    private boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    /**
     * Generate a random access code for room sharing.
     */
    public String generateAccessCode() {
        return generateAccessCode(8);
    }

    /**
     * Generate a random access code with specified length.
     */
    public String generateAccessCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(ACCESS_CODE_CHARS.length());
            code.append(ACCESS_CODE_CHARS.charAt(index));
        }
        return code.toString();
    }

    /**
     * Calculate access code expiration time.
     */
    public LocalDateTime calculateAccessCodeExpiration() {
        return LocalDateTime.now().plusHours(accessCodeExpirationHours);
    }

    /**
     * Check if an access code is expired.
     */
    public boolean isAccessCodeExpired(LocalDateTime expiresAt) {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Create a password hash result for new rooms.
     * BCrypt embeds salt in the hash, so the explicit salt is null.
     */
    public PasswordHashResult createPasswordHash(String password) {
        String hash = PASSWORD_ENCODER.encode(password);
        return new PasswordHashResult(hash, null);
    }

    /**
     * Result containing password hash and salt.
     */
    public record PasswordHashResult(String hash, String salt) {}

    /**
     * Get access code expiration hours.
     */
    public int getAccessCodeExpirationHours() {
        return accessCodeExpirationHours;
    }
}
