package com.screenai.handler;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.screenai.model.PerformanceMetrics;
import com.screenai.service.NetworkQualityService;
import com.screenai.service.PerformanceMonitorService;
import com.screenai.service.ScreenCaptureService;

import jakarta.annotation.PostConstruct;

@Component
public class ScreenShareWebSocketHandler implements WebSocketHandler, PerformanceMonitorService.MetricsListener {

    private static final Logger logger = LoggerFactory.getLogger(ScreenShareWebSocketHandler.class);
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Lazy
    @Autowired
    private ScreenCaptureService screenCaptureService;

    @Autowired
    private PerformanceMonitorService performanceMonitor;

    @Autowired
    private NetworkQualityService networkQualityService;

    @PostConstruct
    public void init() {
        // Register as metrics listener
        performanceMonitor.addMetricsListener(this);
        logger.info("WebSocketHandler registered as performance metrics listener");
    }

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
            TextMessage welcomeMessage = new TextMessage(
                    "{\"type\":\"connected\",\"message\":\"Connected to screen share\"}");
            session.sendMessage(welcomeMessage);

            // ✅ Send cached init segment to new viewer if available
            if (screenCaptureService != null) {
                byte[] initSeg = screenCaptureService.getInitSegment();
                if (initSeg != null && initSeg.length > 0) {
                    BinaryMessage initMessage = new BinaryMessage(initSeg);
                    session.sendMessage(initMessage);
                    logger.info("📤 Sent init segment ({} bytes) to new viewer", initSeg.length);
                }
            }

            // Broadcast updated viewer count to all clients
            broadcastViewerCount();

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

            return session.getRemoteAddress() != null ? session.getRemoteAddress().toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        try {
            String payload = message.getPayload().toString();
            logger.debug("Received message from viewer {}: {}", session.getId(), payload);

            // Handle pong responses for network quality measurement
            if (payload.startsWith("{\"type\":\"pong\"")) {
                handlePongMessage(session, payload);
            }

        } catch (Exception e) {
            logger.debug("Error handling message from session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Handle pong response to calculate network latency
     */
    private void handlePongMessage(WebSocketSession session, String pongPayload) {
        try {
            // Parse pong message to get original timestamp
            // Expected format: {"type":"pong","timestamp":1234567890}
            if (pongPayload.contains("timestamp")) {
                String timestampStr = pongPayload.substring(
                        pongPayload.indexOf("timestamp\":") + 11,
                        pongPayload.lastIndexOf("}"));

                long originalTimestamp = Long.parseLong(timestampStr);
                long currentTime = System.currentTimeMillis();
                double latency = currentTime - originalTimestamp;

                // Record the latency measurement
                networkQualityService.recordPing(session.getId(), latency);

                logger.debug("Recorded ping latency for session {}: {}ms", session.getId(), latency);
            }
        } catch (Exception e) {
            logger.debug("Error processing pong message from session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        sessions.remove(session);

        // Clean up network quality data for this session
        networkQualityService.removeSession(session.getId());

        logger.info("Screen viewer disconnected: {} (Remaining viewers: {})", session.getId(), sessions.size());

        // Broadcast updated viewer count to remaining clients
        broadcastViewerCount();
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Broadcasts H.264 binary video data to all connected viewers
     * 
     * @param videoData Binary H.264 fMP4 video data
     */
    public void broadcastVideoBinary(byte[] videoData) {
        // Fast early exit checks for performance
        if (videoData == null || videoData.length == 0) {
            return;
        }

        if (sessions.isEmpty()) {
            return; // No viewers connected
        }

        try {
            // Create binary message once for all sessions (efficiency)
            BinaryMessage binaryMessage = new BinaryMessage(videoData);

            // Use iterator for better performance than copying the set
            Iterator<WebSocketSession> iterator = sessions.iterator();
            int successfulSends = 0;

            while (iterator.hasNext()) {
                WebSocketSession session = iterator.next();

                // Quick check for closed sessions
                if (!session.isOpen()) {
                    logger.debug("Removing closed session: {}", session.getId());
                    iterator.remove(); // Safe removal during iteration
                    continue;
                }

                try {
                    // Send immediately without buffering for low latency
                    session.sendMessage(binaryMessage);
                    successfulSends++;

                } catch (Exception e) {
                    // Handle failed sessions with detailed logging
                    logger.warn("H.264 transmission failed to session {}: {}", session.getId(), e.getMessage());
                    iterator.remove(); // Remove failed session
                }
            }

            // Reduced logging - only log errors or every 100th frame
            if (successfulSends == 0 && sessions.size() > 0) {
                logger.warn("H.264 data failed to send to all {} sessions", sessions.size());
            } else if (successfulSends > 0) {
                // TEMP DEBUG: Log every send to verify data is flowing
                logger.info("📤 H.264 data sent: {} bytes to {}/{} sessions", videoData.length, successfulSends,
                        sessions.size());
            }

        } catch (Exception e) {
            logger.error("Error in H.264 binary broadcast: {}", e.getMessage());
        }
    }

    /**
     * Gets the number of connected viewers
     * 
     * @return Number of active WebSocket sessions
     */
    public int getViewerCount() {
        return sessions.size();
    }

    /**
     * Get number of active WebSocket sessions
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Get cached init segment for late-joining viewers
     */
    public byte[] getInitSegment() {
        if (screenCaptureService != null) {
            return screenCaptureService.getInitSegment();
        }
        return null;
    }

    /**
     * Get active session IDs for network quality monitoring
     */
    public java.util.Set<String> getActiveSessionIds() {
        return sessions.stream()
                .filter(WebSocketSession::isOpen)
                .map(WebSocketSession::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Trigger ping for all active sessions to measure network quality
     */
    public int triggerPingForAllSessions() {
        int pingCount = 0;
        long pingTimestamp = System.currentTimeMillis();

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    // Send ping message with timestamp
                    String pingMessage = String.format("{\"type\":\"ping\",\"timestamp\":%d}", pingTimestamp);
                    session.sendMessage(new TextMessage(pingMessage));
                    pingCount++;
                } catch (Exception e) {
                    logger.debug("Failed to send ping to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }

        logger.debug("Sent ping to {} active sessions for network quality assessment", pingCount);
        return pingCount;
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

    /**
     * Broadcast viewer count to all connected sessions
     */
    private void broadcastViewerCount() {
        int count = sessions.size();
        String message = String.format("{\"type\":\"viewerCount\",\"count\":%d}", count);
        TextMessage viewerCountMessage = new TextMessage(message);

        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(viewerCountMessage);
                }
            } catch (Exception e) {
                logger.debug("Failed to send viewer count to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * Implementation of MetricsListener interface
     * Called when performance metrics are updated
     */
    @Override
    public void onMetricsUpdate(PerformanceMetrics metrics) {
        try {
            // Create JSON message with performance metrics
            String jsonMessage = objectMapper.writeValueAsString(new PerformanceMessage(metrics));
            TextMessage message = new TextMessage(jsonMessage);

            // Broadcast to all connected sessions
            int sentCount = 0;
            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                        sentCount++;
                    }
                } catch (Exception e) {
                    logger.debug("Failed to send performance metrics to session {}: {}",
                            session.getId(), e.getMessage());
                }
            }

            logger.debug("📊 Performance metrics broadcasted to {} viewers", sentCount);

        } catch (Exception e) {
            logger.error("Error broadcasting performance metrics: {}", e.getMessage());
        }
    }

    /**
     * DTO for performance metrics WebSocket message
     */
    private static class PerformanceMessage {
        public String type = "performance";
        public PerformanceMetrics metrics;

        public PerformanceMessage(PerformanceMetrics metrics) {
            this.metrics = metrics;
        }

        public String getType() {
            return type;
        }

        public PerformanceMetrics getMetrics() {
            return metrics;
        }
    }
}
