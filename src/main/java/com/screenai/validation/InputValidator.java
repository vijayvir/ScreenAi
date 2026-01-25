package com.screenai.validation;

import com.screenai.dto.ErrorResponse.ErrorCode;
import com.screenai.exception.RoomException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Centralized input validation for security-sensitive fields.
 * Validates room IDs, passwords, usernames, and other inputs.
 */
@Component
public class InputValidator {

    // Room ID: alphanumeric, hyphens, underscores, max 64 chars
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    
    // Username: alphanumeric, underscores, hyphens, 3-32 chars
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
    
    // Access code: alphanumeric, 6-12 chars
    private static final Pattern ACCESS_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6,12}$");

    // Maximum lengths
    private static final int MAX_ROOM_ID_LENGTH = 64;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_BINARY_SIZE = 10 * 1024 * 1024; // 10 MB

    /**
     * Validate room ID format.
     */
    public void validateRoomId(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            throw new RoomException(ErrorCode.VAL_002, "Room ID is required");
        }

        if (roomId.length() > MAX_ROOM_ID_LENGTH) {
            throw new RoomException(ErrorCode.VAL_003, "Room ID exceeds maximum length of " + MAX_ROOM_ID_LENGTH);
        }

        if (!ROOM_ID_PATTERN.matcher(roomId).matches()) {
            throw new RoomException(ErrorCode.ROOM_008, 
                    "Room ID can only contain letters, numbers, hyphens, and underscores");
        }
    }

    /**
     * Validate room password (if provided).
     */
    public void validateRoomPassword(String password) {
        if (password != null && password.length() > MAX_PASSWORD_LENGTH) {
            throw new RoomException(ErrorCode.VAL_003, "Password exceeds maximum length of " + MAX_PASSWORD_LENGTH);
        }
    }

    /**
     * Validate username format.
     */
    public void validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (username.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException("Username exceeds maximum length of " + MAX_USERNAME_LENGTH);
        }

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Username can only contain letters, numbers, underscores, and hyphens (3-32 chars)");
        }
    }

    /**
     * Validate access code format.
     */
    public void validateAccessCode(String accessCode) {
        if (accessCode == null || accessCode.isEmpty()) {
            throw new RoomException(ErrorCode.VAL_002, "Access code is required");
        }

        if (!ACCESS_CODE_PATTERN.matcher(accessCode).matches()) {
            throw new RoomException(ErrorCode.VAL_004, "Invalid access code format");
        }
    }

    /**
     * Validate text message length.
     */
    public void validateMessageLength(String message) {
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message exceeds maximum length of " + MAX_MESSAGE_LENGTH);
        }
    }

    /**
     * Validate binary message size.
     */
    public void validateBinarySize(int size) {
        if (size > MAX_BINARY_SIZE) {
            throw new IllegalArgumentException("Binary message exceeds maximum size of " + MAX_BINARY_SIZE + " bytes");
        }
    }

    /**
     * Sanitize string input (remove control characters, trim).
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // Remove control characters and trim
        return input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
    }

    /**
     * Check if room ID is valid (non-throwing version).
     */
    public boolean isValidRoomId(String roomId) {
        if (roomId == null || roomId.isEmpty() || roomId.length() > MAX_ROOM_ID_LENGTH) {
            return false;
        }
        return ROOM_ID_PATTERN.matcher(roomId).matches();
    }

    /**
     * Check if username is valid (non-throwing version).
     */
    public boolean isValidUsername(String username) {
        if (username == null || username.isEmpty() || username.length() > MAX_USERNAME_LENGTH) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Check if password meets basic requirements (non-throwing version).
     */
    public boolean isValidPassword(String password) {
        return password != null && 
               password.length() >= 8 && 
               password.length() <= MAX_PASSWORD_LENGTH;
    }

    // Getters for limits (for configuration)
    public int getMaxRoomIdLength() {
        return MAX_ROOM_ID_LENGTH;
    }

    public int getMaxPasswordLength() {
        return MAX_PASSWORD_LENGTH;
    }

    public int getMaxUsernameLength() {
        return MAX_USERNAME_LENGTH;
    }

    public int getMaxBinarySize() {
        return MAX_BINARY_SIZE;
    }
}
