package com.screenai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private static final int SALT_LENGTH = 16;
    private static final String ACCESS_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Avoid confusing chars

    private final int accessCodeExpirationHours;

    public RoomSecurityService(
            @Value("${security.room.access-code-expiration-hours:24}") int accessCodeExpirationHours) {
        this.accessCodeExpirationHours = accessCodeExpirationHours;
        log.info("RoomSecurityService initialized with access code expiration: {} hours", accessCodeExpirationHours);
    }

    /**
     * Generate a random salt for password hashing.
     */
    public String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hash a password with the given salt using SHA-256.
     */
    public String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Combine salt and password
            String combined = salt + password;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verify a password against stored hash using timing-safe comparison.
     */
    public boolean verifyPassword(String inputPassword, String storedHash, String storedSalt) {
        if (inputPassword == null || storedHash == null || storedSalt == null) {
            return false;
        }

        String inputHash = hashPassword(inputPassword, storedSalt);
        return timingSafeEquals(inputHash, storedHash);
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
     * Create password hash result containing both hash and salt.
     */
    public PasswordHashResult createPasswordHash(String password) {
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        return new PasswordHashResult(hash, salt);
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
