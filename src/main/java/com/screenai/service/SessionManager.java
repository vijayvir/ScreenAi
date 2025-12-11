package com.screenai.service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import com.screenai.model.Room;

/**
 * Manages streaming rooms, presenters, and viewers
 * A room can have one presenter (streaming) and multiple viewers (watching)
 */
@Service
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    
    // Room ID -> Room mapping
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    
    // Session ID -> Room ID mapping (for quick lookup)
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    
    // Session ID -> Role mapping ("presenter" or "viewer")
    private final Map<String, String> sessionRoles = new ConcurrentHashMap<>();
    
    /**
     * Create a new room with a presenter
     */
    public synchronized Room createRoom(String roomId, WebSocketSession presenterSession) {
        Room room = new Room(roomId, presenterSession);
        rooms.put(roomId, room);
        sessionToRoom.put(presenterSession.getId(), roomId);
        sessionRoles.put(presenterSession.getId(), "presenter");
        
        logger.info("📹 Room created: {} with presenter: {}", roomId, presenterSession.getId());
        return room;
    }
    
    /**
     * Add a viewer to an existing room
     */
    public synchronized boolean addViewer(String roomId, WebSocketSession viewerSession) {
        Room room = rooms.get(roomId);
        if (room == null) {
            logger.warn("❌ Room not found: {}", roomId);
            return false;
        }
        
        room.addViewer(viewerSession);
        sessionToRoom.put(viewerSession.getId(), roomId);
        sessionRoles.put(viewerSession.getId(), "viewer");
        
        logger.info("👁️ Viewer added to room {}: {} (Total viewers: {})", 
                   roomId, viewerSession.getId(), room.getViewerCount());
        return true;
    }
    
    /**
     * Remove a session from its room
     */
    public synchronized void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        String roomId = sessionToRoom.remove(sessionId);
        String role = sessionRoles.remove(sessionId);
        
        if (roomId == null) {
            return; // Session wasn't in any room
        }
        
        Room room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        
        if ("presenter".equals(role)) {
            // Presenter left - close the entire room
            logger.info("🚪 Presenter left room: {} - closing room", roomId);
            closeRoom(roomId);
        } else {
            // Viewer left
            room.removeViewer(session);
            logger.info("👋 Viewer left room {}: {} (Remaining: {})", 
                       roomId, sessionId, room.getViewerCount());
        }
    }
    
    /**
     * Get room by room ID
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }
    
    /**
     * Get room for a given session
     */
    public Room getRoomForSession(WebSocketSession session) {
        String roomId = sessionToRoom.get(session.getId());
        return roomId != null ? rooms.get(roomId) : null;
    }
    
    /**
     * Get role of a session ("presenter" or "viewer")
     */
    public String getSessionRole(WebSocketSession session) {
        return sessionRoles.get(session.getId());
    }
    
    /**
     * Check if session is a presenter
     */
    public boolean isPresenter(WebSocketSession session) {
        return "presenter".equals(sessionRoles.get(session.getId()));
    }
    
    /**
     * Close a room and disconnect all participants
     */
    public synchronized void closeRoom(String roomId) {
        Room room = rooms.remove(roomId);
        if (room == null) {
            return;
        }
        
        // Remove all session mappings
        if (room.getPresenter() != null) {
            String presenterId = room.getPresenter().getId();
            sessionToRoom.remove(presenterId);
            sessionRoles.remove(presenterId);
        }
        
        for (WebSocketSession viewer : room.getViewers()) {
            sessionToRoom.remove(viewer.getId());
            sessionRoles.remove(viewer.getId());
        }
        
        logger.info("🗑️ Room closed: {} (Had {} viewers)", roomId, room.getViewerCount());
    }
    
    /**
     * Get all active rooms
     */
    public Collection<Room> getAllRooms() {
        return rooms.values();
    }
    
    /**
     * Get number of active rooms
     */
    public int getRoomCount() {
        return rooms.size();
    }
    
    /**
     * Get total number of active sessions (presenters + viewers)
     */
    public int getTotalSessionCount() {
        return sessionToRoom.size();
    }
}
