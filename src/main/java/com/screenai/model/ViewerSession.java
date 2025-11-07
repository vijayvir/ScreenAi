package com.screenai.model;

import java.time.LocalDateTime;


 //This class represents a viewer session that has joined a screen sharing session.
 // contains:
//sessionId: The unique session identifier that links to the host session
//expiryTime: When this viewer session expires (for security)

public class ViewerSession {
    
    private String sessionId;
    
    private LocalDateTime expiryTime;
    
    private LocalDateTime hostCreatedAt;
    
    private LocalDateTime viewerJoinedAt;
    
    public ViewerSession() {
        }
    
    /**
     * Constructor to create a new viewer session with all required information
     * 
     * @param sessionId Unique identifier for the session (links to host session)
     * @param expiryTime When this viewer session expires
     */
    public ViewerSession(String sessionId, LocalDateTime expiryTime, LocalDateTime hostCreatedAt) {
        this.sessionId = sessionId;
        this.expiryTime = expiryTime;
        this.hostCreatedAt = hostCreatedAt;
        this.viewerJoinedAt = LocalDateTime.now(); // Set viewer join time automatically
    }
    
   
    public String getSessionId() {
        return sessionId;
    }
    
    //sessionId The session ID to set
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
   //exiry time as LocalDateTime
    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }
    
   
    //expiryTime The expiry time to set
    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }

    //Check if this viewer session has expired
   //Compares current time with expiry time
   //true if session has expired, false otherwise
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }

    //Host's session creation time
    public LocalDateTime getHostCreatedAt() {
        return hostCreatedAt;
    }

    //hostCreatedAt The host's session creation time
    public void setHostCreatedAt(LocalDateTime hostCreatedAt) {
        this.hostCreatedAt = hostCreatedAt;
    }

    //viewer's join time
    public LocalDateTime getViewerJoinedAt() {
        return viewerJoinedAt;
    }

    //viewer's join time
    public void setViewerJoinedAt(LocalDateTime viewerJoinedAt) {
        this.viewerJoinedAt = viewerJoinedAt;
    }
    
 //String representation of ViewerSession
    @Override
    public String toString() {
        return "ViewerSession{" +
                "sessionId='" + sessionId + '\'' +", expiryTime=" + expiryTime +
                '}';
    }
}


