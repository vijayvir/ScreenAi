package com.screenai.handler;

import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;


@Component
public class ScreenShareWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ScreenShareWebSocketHandler.class);
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    
    // Connection limits and session management
    private static final int MAX_CONNECTIONS = 50;
    private static final long MAX_SESSION_DURATION = 3600000; // 1 hour
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            String clientIP = getClientIP(session);
            
            // Validate session
            if (session == null) {
                logger.error("Null session in afterConnectionEstablished");
                return;
            }
            
            // Check connection limits
            if (sessions.size() >= MAX_CONNECTIONS) {
                logger.warn("Maximum connections reached ({}), rejecting new connection from {}", 
                           MAX_CONNECTIONS, clientIP);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Maximum connections reached"));
                return;
            }
            
            sessions.add(session);
            session.getAttributes().put("clientIP", clientIP);
            session.getAttributes().put("connectTime", System.currentTimeMillis());
            
            logger.info("WebSocket connection established for IP: {}. Total connections: {}", 
                       clientIP, sessions.size());
            
            // Send welcome message
            TextMessage welcomeMessage = new TextMessage("{\"type\":\"connected\",\"message\":\"Connected to screen share\"}");
            session.sendMessage(welcomeMessage);
                       
        } catch (Exception e) {
            logger.error("Error establishing WebSocket connection", e);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception closeError) {
                logger.debug("Error closing session after connection error: {}", closeError.getMessage());
            }
        }
    }
    
    private String getClientIP(WebSocketSession session) {
        try {
            String xForwardedFor = (String) session.getHandshakeHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIP = (String) session.getHandshakeHeaders().getFirst("X-Real-IP");
            if (xRealIP != null && !xRealIP.isEmpty()) {
                return xRealIP;
            }
            
            return session.getRemoteAddress() != null ? 
                   session.getRemoteAddress().toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // Handle any client messages if needed (e.g., viewer feedback)
        logger.debug("Received message from viewer {}: {}", session.getId(), message.getPayload());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
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
        
        // Create a copy to avoid concurrent modification
        CopyOnWriteArraySet<WebSocketSession> sessionsCopy = new CopyOnWriteArraySet<>(sessions);
        
        for (WebSocketSession session : sessionsCopy) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            
            try {
                session.sendMessage(frameMessage);
            } catch (Exception e) {
                logger.debug("Failed to send frame to session {}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }
    
    /**
     * Gets the number of connected viewers
     * @return Number of active WebSocket sessions
     */
    public int getViewerCount() {
        return sessions.size();
    }
    
    /**
     * Periodic cleanup of stale sessions
     * Runs every minute to remove closed or timed-out sessions
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupStaleSessions() {
        long currentTime = System.currentTimeMillis();
        int initialCount = sessions.size();
        
        sessions.removeIf(session -> {
            try {
                if (!session.isOpen()) {
                    logger.debug("Removing closed session: {}", session.getId());
                    return true;
                }
                
                Long connectTime = (Long) session.getAttributes().get("connectTime");
                if (connectTime != null && (currentTime - connectTime) > MAX_SESSION_DURATION) {
                    logger.info("Closing stale session: {} (duration: {} minutes)", 
                               session.getId(), (currentTime - connectTime) / 60000);
                    try {
                        session.close(CloseStatus.GOING_AWAY.withReason("Session timeout"));
                    } catch (Exception e) {
                        logger.debug("Error closing stale session: {}", e.getMessage());
                    }
                    return true;
                }
                
                return false;
            } catch (Exception e) {
                logger.debug("Error checking session {}: {}", session.getId(), e.getMessage());
                return true; // Remove problematic sessions
            }
        });
        
        int removedCount = initialCount - sessions.size();
        if (removedCount > 0) {
            logger.debug("Cleaned up {} stale sessions. Active sessions: {}", removedCount, sessions.size());
        }
    }
}
