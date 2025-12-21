package com.screenai.model;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Sinks;

/**
 * Reactive Room model for WebFlux-based screen sharing
 * Stores viewer sessions and their message sinks for efficient relay
 */
public class ReactiveRoom {
    private final String roomId;
    private final String presenterSessionId;
    private final WebSocketSession presenterSession;
    private final long createdAt;
    
    // Viewer session ID -> ViewerInfo
    private final Map<String, ViewerInfo> viewers = new ConcurrentHashMap<>();
    
    // Cached init segment (SPS/PPS or ftyp/moov)
    private volatile byte[] cachedInitSegment;
    
    public ReactiveRoom(String roomId, String presenterSessionId, WebSocketSession presenterSession) {
        this.roomId = roomId;
        this.presenterSessionId = presenterSessionId;
        this.presenterSession = presenterSession;
        this.createdAt = System.currentTimeMillis();
    }
    
    /**
     * Add a viewer to this room
     */
    public void addViewer(String sessionId, WebSocketSession session, Sinks.Many<WebSocketMessage> sink) {
        viewers.put(sessionId, new ViewerInfo(sessionId, session, sink));
    }
    
    /**
     * Remove a viewer from this room
     */
    public void removeViewer(String sessionId) {
        viewers.remove(sessionId);
    }
    
    /**
     * Get all viewer session IDs
     */
    public Set<String> getViewerSessionIds() {
        return viewers.keySet();
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
     * Get viewer count
     */
    public int getViewerCount() {
        return viewers.size();
    }
    
    // Getters
    public String getRoomId() {
        return roomId;
    }
    
    public String getPresenterSessionId() {
        return presenterSessionId;
    }
    
    public WebSocketSession getPresenterSession() {
        return presenterSession;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public byte[] getCachedInitSegment() {
        return cachedInitSegment;
    }
    
    public void setCachedInitSegment(byte[] initSegment) {
        this.cachedInitSegment = initSegment;
    }
    
    /**
     * Check if presenter session is still open
     */
    public boolean isPresenterActive() {
        return presenterSession != null && presenterSession.isOpen();
    }
    
    /**
     * Viewer information record
     */
    public record ViewerInfo(String sessionId, WebSocketSession session, Sinks.Many<WebSocketMessage> sink) {}
}
