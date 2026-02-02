package com.screenai.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Sinks;

/**
 * Reactive Room model for WebFlux-based screen sharing
 * Stores viewer sessions and their message sinks for efficient relay.
 * Includes security features: password protection, viewer approval, and banning.
 */
public class ReactiveRoom {
    private final String roomId;
    private final String presenterSessionId;
    private final WebSocketSession presenterSession;
    private final String presenterUsername;
    private final long createdAt;
    
    // Viewer session ID -> ViewerInfo
    private final Map<String, ViewerInfo> viewers = new ConcurrentHashMap<>();
    
    // Cached init segment (SPS/PPS or ftyp/moov)
    private volatile byte[] cachedInitSegment;
    
    // ==================== Security Fields ====================
    
    // Password protection
    private String passwordHash;
    private String passwordSalt;
    private boolean passwordProtected;
    
    // Viewer approval system (required for password-protected rooms)
    private boolean requiresApproval;
    private final Map<String, PendingViewer> pendingViewers = new ConcurrentHashMap<>();
    
    // Banned sessions (cannot rejoin this room)
    private final Set<String> bannedSessions = ConcurrentHashMap.newKeySet();
    
    // Access code for easy sharing
    private String accessCode;
    private LocalDateTime accessCodeExpiresAt;
    
    // Room limits
    private int maxViewers = 50;
    
    // Last activity timestamp for idle timeout
    private volatile long lastActivityAt;
    
    public ReactiveRoom(String roomId, String presenterSessionId, WebSocketSession presenterSession) {
        this(roomId, presenterSessionId, presenterSession, null);
    }
    
    public ReactiveRoom(String roomId, String presenterSessionId, WebSocketSession presenterSession, String presenterUsername) {
        this.roomId = roomId;
        this.presenterSessionId = presenterSessionId;
        this.presenterSession = presenterSession;
        this.presenterUsername = presenterUsername;
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = System.currentTimeMillis();
        this.passwordProtected = false;
        this.requiresApproval = false;
    }
    
    // ==================== Password Protection ====================
    
    /**
     * Set password protection for this room.
     */
    public void setPassword(String hash, String salt) {
        this.passwordHash = hash;
        this.passwordSalt = salt;
        this.passwordProtected = true;
        this.requiresApproval = true; // Password-protected rooms require approval
    }
    
    /**
     * Check if room is password protected.
     */
    public boolean isPasswordProtected() {
        return passwordProtected;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public String getPasswordSalt() {
        return passwordSalt;
    }
    
    // ==================== Viewer Approval ====================
    
    /**
     * Add a viewer to pending approval queue.
     */
    public void addPendingViewer(String sessionId, String username, WebSocketSession session, Sinks.Many<WebSocketMessage> sink) {
        pendingViewers.put(sessionId, new PendingViewer(sessionId, username, session, sink, System.currentTimeMillis()));
    }
    
    /**
     * Approve a pending viewer (move to active viewers).
     */
    public boolean approveViewer(String sessionId) {
        PendingViewer pending = pendingViewers.remove(sessionId);
        if (pending != null) {
            viewers.put(sessionId, new ViewerInfo(sessionId, pending.username(), pending.session(), pending.sink()));
            return true;
        }
        return false;
    }
    
    /**
     * Deny a pending viewer.
     */
    public PendingViewer denyViewer(String sessionId) {
        return pendingViewers.remove(sessionId);
    }
    
    /**
     * Get all pending viewers.
     */
    public Map<String, PendingViewer> getPendingViewers() {
        return pendingViewers;
    }
    
    /**
     * Get pending viewer by session ID.
     */
    public PendingViewer getPendingViewer(String sessionId) {
        return pendingViewers.get(sessionId);
    }
    
    /**
     * Remove pending viewer
     */
    public void removePendingViewer(String sessionId) {
        pendingViewers.remove(sessionId);
    }

    public boolean requiresApproval() {
        return requiresApproval;
    }
    
    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }
    
    // ==================== Banning ====================
    
    /**
     * Ban a viewer from this room.
     */
    public void banViewer(String sessionId) {
        bannedSessions.add(sessionId);
        viewers.remove(sessionId);
        pendingViewers.remove(sessionId);
    }
    
    /**
     * Ban a session (alias for banViewer for semantic clarity)
     */
    public void banSession(String sessionId) {
        bannedSessions.add(sessionId);
    }
    
    /**
     * Check if a session is banned.
     */
    public boolean isBanned(String sessionId) {
        return bannedSessions.contains(sessionId);
    }
    
    /**
     * Get banned session IDs.
     */
    public Set<String> getBannedSessions() {
        return bannedSessions;
    }
    
    // ==================== Access Code ====================
    
    public String getAccessCode() {
        return accessCode;
    }
    
    public void setAccessCode(String accessCode, LocalDateTime expiresAt) {
        this.accessCode = accessCode;
        this.accessCodeExpiresAt = expiresAt;
    }
    
    public LocalDateTime getAccessCodeExpiresAt() {
        return accessCodeExpiresAt;
    }
    
    public boolean isAccessCodeValid() {
        return accessCode != null && 
               (accessCodeExpiresAt == null || LocalDateTime.now().isBefore(accessCodeExpiresAt));
    }
    
    // ==================== Room Limits ====================
    
    public int getMaxViewers() {
        return maxViewers;
    }
    
    public void setMaxViewers(int maxViewers) {
        this.maxViewers = maxViewers;
    }
    
    public boolean isFull() {
        return viewers.size() >= maxViewers;
    }
    
    // ==================== Activity Tracking ====================
    
    public void updateActivity() {
        this.lastActivityAt = System.currentTimeMillis();
    }
    
    public long getLastActivityAt() {
        return lastActivityAt;
    }
    
    public boolean isIdle(long idleTimeoutMillis) {
        return System.currentTimeMillis() - lastActivityAt > idleTimeoutMillis;
    }
    
    // ==================== Viewer Management ====================
    
    /**
     * Add a viewer to this room (for non-approval-required rooms or after approval)
     */
    public void addViewer(String sessionId, WebSocketSession session, Sinks.Many<WebSocketMessage> sink) {
        addViewer(sessionId, null, session, sink);
    }
    
    /**
     * Add a viewer with username
     */
    public void addViewer(String sessionId, String username, WebSocketSession session, Sinks.Many<WebSocketMessage> sink) {
        viewers.put(sessionId, new ViewerInfo(sessionId, username, session, sink));
        updateActivity();
    }
    
    /**
     * Remove a viewer from this room
     */
    public void removeViewer(String sessionId) {
        viewers.remove(sessionId);
        pendingViewers.remove(sessionId);
    }
    
    /**
     * Get all viewer session IDs
     */
    public Set<String> getViewerSessionIds() {
        return viewers.keySet();
    }
    
    /**
     * Get viewer info by session ID
     */
    public Optional<ViewerInfo> getViewer(String sessionId) {
        return Optional.ofNullable(viewers.get(sessionId));
    }
    
    /**
     * Get viewer info map
     */
    public Map<String, Optional<ViewerInfo>> getViewerSinks() {
        Map<String, Optional<ViewerInfo>> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, ViewerInfo> entry : viewers.entrySet()) {
            result.put(entry.getKey(), Optional.of(entry.getValue()));
        }
        return result;
    }
    
    /**
     * Get all viewers
     */
    public Map<String, ViewerInfo> getViewers() {
        return viewers;
    }
    
    /**
     * Get viewer count
     */
    public int getViewerCount() {
        return viewers.size();
    }
    
    /**
     * Check if a viewer exists in this room
     */
    public boolean hasViewer(String sessionId) {
        return viewers.containsKey(sessionId);
    }
    
    /**
     * Get pending viewer count
     */
    public int getPendingViewerCount() {
        return pendingViewers.size();
    }
    
    // ==================== Basic Getters ====================
    
    public String getRoomId() {
        return roomId;
    }
    
    public String getPresenterSessionId() {
        return presenterSessionId;
    }
    
    public WebSocketSession getPresenterSession() {
        return presenterSession;
    }
    
    public String getPresenterUsername() {
        return presenterUsername;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public byte[] getCachedInitSegment() {
        return cachedInitSegment;
    }
    
    public void setCachedInitSegment(byte[] initSegment) {
        this.cachedInitSegment = initSegment;
        updateActivity();
    }
    
    /**
     * Check if presenter session is still open
     */
    public boolean isPresenterActive() {
        return presenterSession != null && presenterSession.isOpen();
    }
    
    // ==================== Records ====================
    
    /**
     * Viewer information record
     */
    public record ViewerInfo(String sessionId, String username, WebSocketSession session, Sinks.Many<WebSocketMessage> sink) {
        // Constructor for backward compatibility
        public ViewerInfo(String sessionId, WebSocketSession session, Sinks.Many<WebSocketMessage> sink) {
            this(sessionId, null, session, sink);
        }
    }
    
    /**
     * Pending viewer information record (awaiting approval)
     */
    public record PendingViewer(String sessionId, String username, WebSocketSession session, 
                                 Sinks.Many<WebSocketMessage> sink, long requestedAt) {}
}
