package com.screenai.model;

import java.time.LocalDateTime;

/**
 * Data model class to store viewer session information
 * 
 * This class represents a viewer session that has joined a screen sharing session.
 * It contains:
 * - sessionId: The unique session identifier that links to the host session
 * - expiryTime: When this viewer session expires (for security)
 * 
 * This is used in Phase-2 to store viewer sessions in memory using HashMap
 * before implementing database storage in later phases.
 */
public class ViewerSession {
    
    /**
     * Unique session identifier
     * This links the viewer to the host's screen sharing session
     * Generated as UUID to ensure uniqueness across all sessions
     */
    private String sessionId;
    
    /**
     * Viewer session expiry time
     * When this viewer session becomes invalid for security purposes
     * Stored as LocalDateTime for easy comparison and cleanup
     */
    private LocalDateTime expiryTime;
    
    /**
     * Default constructor
     * Required for Spring Boot JSON serialization/deserialization
     */
    public ViewerSession() {
        // Empty constructor for Spring Boot
    }
    
    /**
     * Constructor to create a new viewer session with all required information
     * 
     * @param sessionId Unique identifier for the session (links to host session)
     * @param expiryTime When this viewer session expires
     */
    public ViewerSession(String sessionId, LocalDateTime expiryTime) {
        this.sessionId = sessionId;
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
     * Get when this viewer session expires
     * @return Expiry time as LocalDateTime
     */
    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }
    
    /**
     * Set when this viewer session expires
     * @param expiryTime The expiry time to set
     */
    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    /**
     * Check if this viewer session has expired
     * Compares current time with expiry time
     * @return true if session has expired, false otherwise
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    /**
     * Get a string representation of this viewer session
     * Useful for debugging and logging
     * @return String representation of ViewerSession
     */
    @Override
    public String toString() {
        return "ViewerSession{" +
                "sessionId='" + sessionId + '\'' +
                ", expiryTime=" + expiryTime +
                '}';
    }
}


