package com.screenai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Standardized error response DTO.
 * Provides consistent error format across all API endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String type;
    private String code;
    private String message;
    private String details;
    private LocalDateTime timestamp;
    private String path;

    // Default constructor
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    // Constructor with code and message
    public ErrorResponse(String code, String message) {
        this();
        this.code = code;
        this.message = message;
        this.type = "error";
    }

    // Static factory methods
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message);
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String details) {
        ErrorResponse response = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
        response.setDetails(details);
        return response;
    }

    // Error code enum for standardized codes
    public enum ErrorCode {
        // Authentication errors (AUTH_XXX)
        AUTH_001("AUTH_001", "Invalid credentials"),
        AUTH_002("AUTH_002", "Account is locked"),
        AUTH_003("AUTH_003", "Token expired"),
        AUTH_004("AUTH_004", "Invalid token"),
        AUTH_005("AUTH_005", "Token refresh failed"),
        AUTH_006("AUTH_006", "Unauthorized access"),
        AUTH_007("AUTH_007", "Registration failed"),
        AUTH_008("AUTH_008", "Username already exists"),
        AUTH_009("AUTH_009", "Password does not meet requirements"),
        
        // Room errors (ROOM_XXX)
        ROOM_001("ROOM_001", "Room not found"),
        ROOM_002("ROOM_002", "Room already exists"),
        ROOM_003("ROOM_003", "Invalid room password"),
        ROOM_004("ROOM_004", "Room is full"),
        ROOM_005("ROOM_005", "Access denied to room"),
        ROOM_006("ROOM_006", "You are banned from this room"),
        ROOM_007("ROOM_007", "Waiting for host approval"),
        ROOM_008("ROOM_008", "Invalid room ID format"),
        ROOM_009("ROOM_009", "Room creation limit exceeded"),
        
        // Rate limiting errors (RATE_XXX)
        RATE_001("RATE_001", "Rate limit exceeded"),
        RATE_002("RATE_002", "Too many requests"),
        RATE_003("RATE_003", "IP address blocked"),
        
        // Validation errors (VAL_XXX)
        VAL_001("VAL_001", "Invalid input"),
        VAL_002("VAL_002", "Missing required field"),
        VAL_003("VAL_003", "Field too long"),
        VAL_004("VAL_004", "Invalid format"),
        
        // Server errors (SRV_XXX)
        SRV_001("SRV_001", "Internal server error"),
        SRV_002("SRV_002", "Service unavailable"),
        SRV_003("SRV_003", "Database error"),
        
        // General errors
        GENERAL("GEN_001", "An error occurred");

        private final String code;
        private final String message;

        ErrorCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
