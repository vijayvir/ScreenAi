package com.screenai.handler;

import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket handler for broadcasting screen frames to connected viewers
 * Supports multiple concurrent viewers on the same WiFi network
 */
@Component
public class ScreenShareWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ScreenShareWebSocketHandler.class);
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        sessions.add(session);
        logger.info("New screen viewer connected: {} (Total viewers: {})", session.getId(), sessions.size());
        
        // Send welcome message
        TextMessage welcomeMessage = new TextMessage("{\"type\":\"connected\",\"message\":\"Connected to screen share\"}");
        session.sendMessage(welcomeMessage);
    }
    
    public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message) throws Exception {
        // Handle any client messages if needed (e.g., viewer feedback)
        logger.debug("Received message from viewer {}: {}", session.getId(), message.getPayload());
    }
    
    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
        logger.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }
    
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus closeStatus) throws Exception {
        sessions.remove(session);
        logger.info("Screen viewer disconnected: {} (Remaining viewers: {})", session.getId(), sessions.size());
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    /**
     * Broadcasts a screen frame to all connected viewers
     * @param frameData Base64 encoded JPEG image data
     */
    public void broadcastScreenFrame(String frameData) {
        if (sessions.isEmpty()) {
            return; // No viewers connected
        }
        
        String message = "{\"type\":\"frame\",\"data\":\"data:image/jpeg;base64," + frameData + "\"}";
        TextMessage frameMessage = new TextMessage(message);
        
        // Remove invalid sessions while broadcasting
        sessions.removeIf(session -> {
            if (!session.isOpen()) {
                return true; // Remove closed sessions
            }
            
            try {
                session.sendMessage(frameMessage);
                return false; // Keep valid sessions
            } catch (Exception e) {
                logger.debug("Failed to send frame to session {}: {}", session.getId(), e.getMessage());
                return true; // Remove failed sessions
            }
        });
    }
    
    /**
     * Gets the number of connected viewers
     * @return Number of active WebSocket sessions
     */
    public int getViewerCount() {
        return sessions.size();
    }
}
