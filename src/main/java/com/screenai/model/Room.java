package com.screenai.model;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.web.socket.WebSocketSession;

/**
 * Represents a streaming room with one presenter and multiple viewers
 */
public class Room {
    private final String roomId;
    private final WebSocketSession presenter;
    private final Set<WebSocketSession> viewers = new CopyOnWriteArraySet<>();
    private final long createdAt;
    private byte[] cachedInitSegment; // H.264 init segment for late joiners
    
    public Room(String roomId, WebSocketSession presenter) {
        this.roomId = roomId;
        this.presenter = presenter;
        this.createdAt = System.currentTimeMillis();
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public WebSocketSession getPresenter() {
        return presenter;
    }
    
    public Set<WebSocketSession> getViewers() {
        return viewers;
    }
    
    public void addViewer(WebSocketSession viewer) {
        viewers.add(viewer);
    }
    
    public void removeViewer(WebSocketSession viewer) {
        viewers.remove(viewer);
    }
    
    public int getViewerCount() {
        return viewers.size();
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
        return presenter != null && presenter.isOpen();
    }
}
