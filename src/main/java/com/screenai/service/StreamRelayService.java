package com.screenai.service;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.screenai.model.Room;

/**
 * Service for relaying video streams from presenters to viewers
 */
@Service
public class StreamRelayService {
    private static final Logger logger = LoggerFactory.getLogger(StreamRelayService.class);
    
    @Autowired
    private SessionManager sessionManager;
    
    /**
     * Relay binary video data from presenter to all viewers in the room
     */
    public void relayVideoData(WebSocketSession presenterSession, byte[] videoData) {
        if (videoData == null || videoData.length == 0) {
            return;
        }
        
        Room room = sessionManager.getRoomForSession(presenterSession);
        if (room == null) {
            logger.warn("❌ Presenter {} not in any room", presenterSession.getId());
            return;
        }
        
        // Check if this is an init segment (ftyp or moov box)
        if (isInitSegment(videoData)) {
            logger.info("📦 Caching init segment for room {} ({} bytes)", room.getRoomId(), videoData.length);
            room.setCachedInitSegment(videoData);
        }
        
        // Relay to all viewers
        BinaryMessage binaryMessage = new BinaryMessage(videoData);
        Iterator<WebSocketSession> iterator = room.getViewers().iterator();
        int successfulSends = 0;
        
        while (iterator.hasNext()) {
            WebSocketSession viewer = iterator.next();
            
            if (!viewer.isOpen()) {
                logger.debug("Removing closed viewer: {}", viewer.getId());
                iterator.remove();
                sessionManager.removeSession(viewer);
                continue;
            }
            
            try {
                viewer.sendMessage(binaryMessage);
                successfulSends++;
            } catch (Exception e) {
                logger.warn("Failed to relay to viewer {}: {}", viewer.getId(), e.getMessage());
                iterator.remove();
                sessionManager.removeSession(viewer);
            }
        }
        
        if (successfulSends > 0) {
            logger.debug("📤 Relayed {} bytes to {}/{} viewers in room {}", 
                        videoData.length, successfulSends, room.getViewerCount(), room.getRoomId());
        }
    }
    
    /**
     * Send cached init segment to a new viewer
     */
    public void sendInitSegmentToViewer(WebSocketSession viewer, Room room) {
        byte[] initSegment = room.getCachedInitSegment();
        if (initSegment == null || initSegment.length == 0) {
            logger.warn("⚠️ No init segment cached for room {}", room.getRoomId());
            return;
        }
        
        try {
            viewer.sendMessage(new BinaryMessage(initSegment));
            logger.info("📤 Sent init segment ({} bytes) to new viewer in room {}", 
                       initSegment.length, room.getRoomId());
        } catch (Exception e) {
            logger.error("Failed to send init segment to viewer {}: {}", viewer.getId(), e.getMessage());
        }
    }
    
    /**
     * Broadcast text message to all viewers in a room
     */
    public void broadcastToRoom(Room room, String message) {
        TextMessage textMessage = new TextMessage(message);
        
        for (WebSocketSession viewer : room.getViewers()) {
            try {
                if (viewer.isOpen()) {
                    viewer.sendMessage(textMessage);
                }
            } catch (Exception e) {
                logger.debug("Failed to broadcast to viewer {}: {}", viewer.getId(), e.getMessage());
            }
        }
    }
    
    /**
     * Check if data is an H.264 init segment (contains ftyp or moov)
     */
    private boolean isInitSegment(byte[] data) {
        if (data.length < 8) {
            return false;
        }
        
        // Check for ftyp box (file type) or moov box (movie metadata)
        return (data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') ||
               (data[4] == 'm' && data[5] == 'o' && data[6] == 'o' && data[7] == 'v');
    }
}
