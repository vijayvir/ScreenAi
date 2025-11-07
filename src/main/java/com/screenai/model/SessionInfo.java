package com.screenai.model;

import java.time.LocalDateTime;


public class SessionInfo {

    private LocalDateTime createdAt;
    
    private String sessionId;
    
    private String token;
    
    private LocalDateTime expiryTime;
    
  
    public SessionInfo() {
        
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
        this.createdAt = LocalDateTime.now(); // Set creation timestamp
    }
    
    
    //Creation time as LocalDateTime 

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
  //createdAt The creation time to set
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    

    public String getSessionId() {
        return sessionId;
    }
    
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    

    public String getToken() {
        return token;
    }
    
   
    public void setToken(String token) {
        this.token = token;
    }
    
 
    public LocalDateTime getExpiryTime() {
        return expiryTime;
    }
    
   
    public void setExpiryTime(LocalDateTime expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    
    /**@return true if session has expired, false otherwise
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    

     /** Get a string representation of this session
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


