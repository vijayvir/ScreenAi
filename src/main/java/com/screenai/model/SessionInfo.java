package com.screenai.model;

import java.time.LocalDateTime;

/**
 * Data model class to store session information for screen sharing
 * 
 * This class represents a single screen sharing session and contains:
 * - sessionId: Unique identifier for the session (UUID format)
 * - token: Short-lived authentication token for secure access
 * - expiryTime: When this session expires (for security)
 * 
 * This is used in Phase-1 to store session data in memory using HashMap
 * before implementing database storage in later phases.
 */
public class SessionInfo {
    
    /**
     * Unique session identifier
     * Generated as UUID to ensure uniqueness across all sessions
     */
    private String sessionId;
    
    /**
     * Short-lived authentication token
     * Used for secure access validation (will be JWT in future phases)
     * For now, it's a random secure string
     */
    private String token;
    
    /**
     * Session expiry time
     * When this session becomes invalid for security purposes
     * Stored as LocalDateTime for easy comparison and cleanup
     */
    private LocalDateTime expiryTime;
    
    /**
     * Default constructor
     * Required for Spring Boot JSON serialization/deserialization
     */
    public SessionInfo() {
        // Empty constructor for Spring Boot
    }
    
    /**
     * Constructor to create a new session with all required information
     * 
     * @param sessionId Unique identifier for this session
     * @param token Authentication token for this session
     * @param expiryTime When this session expires
     */
    public SessionInfo(String sessionId, String token, LocalDateTime expiryTime) {
        this.sessionId = sessionId;
        this.token = token;
        this.expiryTime = expiryTime;
    }
    
    /**
     * Get the unique session identifier
     * @return Session ID as String
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Set the unique session identifier
     * @param sessionId The session ID to set
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    /**
     * Get the authentication token for this session
     * @return Token as String
     */
    public String getToken() {
        return token;
    }
    
    /**
     * Set the authentication token for this session
     * @param token The token to set
     */
    public void setToken(String token) {
        this.token = token;
    }
    
    /**
     * Get when this session expires
     * @return Expiry time as LocalDateTime
     */
    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }
    
    /**
     * Set when this session expires
     * @param expiryTime The expiry time to set
     */
    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    /**
     * Check if this session has expired
     * Compares current time with expiry time
     * @return true if session has expired, false otherwise
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    /**
     * Get a string representation of this session
     * Useful for debugging and logging
     * @return String representation of SessionInfo
     */
    @Override
    public String toString() {
        return "SessionInfo{" +
                "sessionId='" + sessionId + '\'' +
                ", token='" + token + '\'' +
                ", expiryTime=" + expiryTime +
                '}';
    }
}

