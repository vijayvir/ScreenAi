package com.screenai.handler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.screenai.service.AccessCodeService;


/**
 * PHASE 3: WebSocket Handler with Token Authentication
 * 
 * Manages real-time WebSocket connections with authentication:
 * ✓ Validates viewer tokens from AccessCodeService
 * ✓ Extracts sessionId from URL path (/ws/{sessionId})
 * ✓ Extracts token from query parameter (?token=...)
 * ✓ Thread-safe storage (ConcurrentHashMap + CopyOnWriteArraySet)
 * ✓ Max 50 connections per session
 * ✓ Auto-cleanup after 1 hour
 * ✓ Periodic cleanup every 60 seconds
 * 
 * TESTING:
 * Valid: wscat -c "ws://localhost:8081/ws/{SESSION_ID}?token={TOKEN}"
 * Invalid: wscat -c "ws://localhost:8081/ws/{SESSION_ID}?token=WRONG"
 * Missing: wscat -c "ws://localhost:8081/ws/{SESSION_ID}"
 */
@Component
public class ScreenShareWebSocketHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ScreenShareWebSocketHandler.class);
    
    // PHASE 3: Thread-safe storage - Maps sessionId to set of WebSocket connections
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> sessionConnections 
        = new ConcurrentHashMap<>();
    
    // PHASE 3: Inject AccessCodeService to validate tokens from Phase 1/2 storage
    private final AccessCodeService accessCodeService;
    
    // Connection limits and session management
    private static final int MAX_CONNECTIONS = 50;
    private static final long MAX_SESSION_DURATION = 3600000; // 1 hour
    
    /**
     * Constructor: Spring injects AccessCodeService for token validation
     * @param accessCodeService Service containing token storage from Phase 1/2
     */
    public ScreenShareWebSocketHandler(AccessCodeService accessCodeService) {
        this.accessCodeService = accessCodeService;
    }
    
    /**
     * PHASE 3: Authenticate and establish WebSocket connection
     * 
     * This method is called when a WebSocket connection is attempted.
     * Steps:
     * 1. Extract sessionId from URL path (/ws/{sessionId})
     * 2. Extract token from query parameter (?token=...)
     * 3. Validate token using AccessCodeService
     * 4. Check connection limits (max 50 per session)
     * 5. Accept or reject the connection
     * 
     * @param session The WebSocket session attempting to connect
     * @throws Exception If an error occurs during connection handling
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        try {
            // STEP 1: Extract sessionId from URL path variable (/ws/{sessionId})
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> pathVariables = 
                (java.util.Map<String, String>) session.getAttributes()
                    .get("org.springframework.web.socket.server.ServletServerContainerFactoryBean.PATH_VARIABLES");
            
            String sessionId = null;
            if (pathVariables != null) {
                sessionId = pathVariables.get("sessionId");
            }
            
            // STEP 2: Extract token from query parameter (?token=...)
            String token = null;
            if (session.getUri() != null && session.getUri().getQuery() != null) {
                String query = session.getUri().getQuery();
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6); // Extract value after "token="
                        break;
                    }
                }
            }
            
            // STEP 3: Validate sessionId exists
            if (sessionId == null || sessionId.trim().isEmpty()) {
                logger.warn("✗ Connection REJECTED - Missing or invalid sessionId");
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing sessionId"));
                return;
            }
            
            // STEP 4: Validate token exists
            if (token == null || token.trim().isEmpty()) {
                logger.warn("✗ Connection REJECTED - Missing token for sessionId: {}", sessionId);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing or invalid token"));
                return;
            }
            
            // STEP 5: Validate token using AccessCodeService (Phase 1/2 storage)
            String validatedSessionId = accessCodeService.validateViewerToken(token);
            
            if (validatedSessionId == null) {
                logger.warn("✗ Connection REJECTED - Invalid/expired token for sessionId: {}", sessionId);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or expired token"));
                return;
            }
            
            // STEP 6: Verify token's sessionId matches URL sessionId
            if (!validatedSessionId.equals(sessionId)) {
                logger.warn("✗ Connection REJECTED - SessionId mismatch. URL: {}, Token: {}", 
                           sessionId, validatedSessionId);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("SessionId mismatch"));
                return;
            }
            
            // STEP 7: Check connection limits for this session
            CopyOnWriteArraySet<WebSocketSession> sessionSessions = 
                sessionConnections.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>());
            
            if (sessionSessions.size() >= MAX_CONNECTIONS) {
                logger.warn("✗ Connection REJECTED - Max connections ({}) reached for: {}", 
                           MAX_CONNECTIONS, sessionId);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Max connections reached"));
                return;
            }
            
            // STEP 8: All validation passed - accept connection
            sessionSessions.add(session);
            session.getAttributes().put("sessionId", sessionId);
            session.getAttributes().put("token", token);
            session.getAttributes().put("connectTime", System.currentTimeMillis());
            session.getAttributes().put("clientIP", getClientIP(session));
            
            logger.info("✓ WebSocket connection ACCEPTED");
            logger.info("  SessionId: {}", sessionId);
            logger.info("  Token: {}...", token.substring(0, Math.min(10, token.length())));
            logger.info("  Client IP: {}", session.getAttributes().get("clientIP"));
            logger.info("  Active connections: {}", sessionSessions.size());
            
            // Send welcome message
            TextMessage welcomeMessage = new TextMessage(
                "{\"type\":\"connected\",\"message\":\"Successfully connected to screen sharing session\"}"
            );
            session.sendMessage(welcomeMessage);
                       
        } catch (Exception e) {
            logger.error("Error establishing WebSocket connection", e);
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVER_ERROR.withReason("Connection error"));
                }
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
            
            var remoteAddress = session.getRemoteAddress();
            return remoteAddress != null ? remoteAddress.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    @Override
    public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message) throws Exception {
        // Handle any client messages if needed (e.g., viewer feedback)
        logger.debug("Received message from viewer {}: {}", session.getId(), message.getPayload());
    }
    
    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) throws Exception {
        logger.error("❌ Transport error for session {}: {}", session.getId(), exception.getMessage());
        logger.error("Exception details: ", exception);
        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId != null) {
            CopyOnWriteArraySet<WebSocketSession> sessionSessions = sessionConnections.get(sessionId);
            if (sessionSessions != null) {
                sessionSessions.remove(session);
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus closeStatus) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId != null) {
            CopyOnWriteArraySet<WebSocketSession> sessionSessions = sessionConnections.get(sessionId);
            if (sessionSessions != null) {
                sessionSessions.remove(session);
                logger.info("Viewer disconnected from session: {} (Remaining: {})", sessionId, sessionSessions.size());
                
                // If no more viewers, remove the session entry
                if (sessionSessions.isEmpty()) {
                    sessionConnections.remove(sessionId);
                }
            }
        } else {
            logger.info("Viewer disconnected: {}", session.getId());
        }
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    /**
     * Broadcasts a screen frame to all connected viewers in a session
     * @param sessionId The session to broadcast to
     * @param frameData Base64 encoded JPEG image data
     */
    public void broadcastScreenFrame(String sessionId, String frameData) {
        CopyOnWriteArraySet<WebSocketSession> sessionSessions = sessionConnections.get(sessionId);
        if (sessionSessions == null || sessionSessions.isEmpty()) {
            return; // No viewers connected
        }
        
        String message = "{\"type\":\"frame\",\"data\":\"data:image/jpeg;base64," + frameData + "\"}";
        TextMessage frameMessage = new TextMessage(message);
        
        for (WebSocketSession session : sessionSessions) {
            if (!session.isOpen()) {
                sessionSessions.remove(session);
                continue;
            }
            
            try {
                session.sendMessage(frameMessage);
            } catch (Exception e) {
                logger.debug("Failed to send frame to session {}: {}", session.getId(), e.getMessage());
                sessionSessions.remove(session);
            }
        }
    }
    
    /**
     * PHASE 2 BACKWARD COMPATIBILITY: Broadcast to all sessions
     * (Old method signature - broadcasts to all connected sessions)
     * @param frameData Base64 encoded JPEG image data
     */
    public void broadcastScreenFrame(String frameData) {
        // Broadcast to all sessions
        for (CopyOnWriteArraySet<WebSocketSession> sessionSessions : sessionConnections.values()) {
            if (sessionSessions == null || sessionSessions.isEmpty()) {
                continue;
            }
            
            String message = "{\"type\":\"frame\",\"data\":\"data:image/jpeg;base64," + frameData + "\"}";
            TextMessage frameMessage = new TextMessage(message);
            
            for (WebSocketSession session : sessionSessions) {
                if (!session.isOpen()) {
                    sessionSessions.remove(session);
                    continue;
                }
                
                try {
                    session.sendMessage(frameMessage);
                } catch (Exception e) {
                    logger.debug("Failed to send frame to session {}: {}", session.getId(), e.getMessage());
                    sessionSessions.remove(session);
                }
            }
        }
    }
    
    /**
     * Gets the number of connected viewers for a session
     * @param sessionId The session ID
     * @return Number of active WebSocket connections for that session
     */
    public int getViewerCount(String sessionId) {
        CopyOnWriteArraySet<WebSocketSession> sessionSessions = sessionConnections.get(sessionId);
        return (sessionSessions != null) ? sessionSessions.size() : 0;
    }
    
    /**
     * PHASE 2 BACKWARD COMPATIBILITY: Get total viewer count across all sessions
     * (Old method signature - returns total viewers)
     * @return Total number of active WebSocket connections across all sessions
     */
    public int getViewerCount() {
        int totalViewers = 0;
        for (CopyOnWriteArraySet<WebSocketSession> sessionSessions : sessionConnections.values()) {
            if (sessionSessions != null) {
                totalViewers += sessionSessions.size();
            }
        }
        return totalViewers;
    }
    
    /**
     * Periodic cleanup of expired sessions
     * Runs every 60 seconds to remove closed or timed-out sessions
     * Also removes viewer token expiries from AccessCodeService
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupStaleSessions() {
        long currentTime = System.currentTimeMillis();
        int totalRemoved = 0;
        
        // Iterate through all sessions
        for (String sessionId : sessionConnections.keySet()) {
            CopyOnWriteArraySet<WebSocketSession> sessionSessions = sessionConnections.get(sessionId);
            if (sessionSessions == null) continue;
            
            int initialCount = sessionSessions.size();
            
            sessionSessions.removeIf(session -> {
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
                            if (session.isOpen()) {
                                session.close(CloseStatus.GOING_AWAY.withReason("Session timeout"));
                            }
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
            
            int removedCount = initialCount - sessionSessions.size();
            totalRemoved += removedCount;
            
            // Remove session entry if no viewers left
            if (sessionSessions.isEmpty()) {
                sessionConnections.remove(sessionId);
            }
        }
        
        // Also cleanup expired viewer sessions from AccessCodeService
        accessCodeService.cleanupExpiredViewerSessions();
        
        if (totalRemoved > 0) {
            logger.info("Cleanup: Removed {} stale sessions. Active sessions: {}", 
                       totalRemoved, sessionConnections.size());
        }
    }
}
