package com.screenai.handler;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

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

    // Network latency measurement
    private final Map<String, LatencyData> sessionLatencies = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingPings = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2);
    private static final long HEARTBEAT_INTERVAL = 2000; // 2 seconds
    private static final long PING_TIMEOUT = 5000; // 5 seconds

    // Latency data structure
    public static class LatencyData {
        private long lastLatency = -1;
        private double averageLatency = 0.0;
        private long minLatency = Long.MAX_VALUE;
        private long maxLatency = 0;
        private int measurementCount = 0;
        private long lastPingTime = 0;
        private boolean connected = true;

        public void updateLatency(long latency) {
            this.lastLatency = latency;
            this.measurementCount++;

            // Update min/max
            this.minLatency = Math.min(this.minLatency, latency);
            this.maxLatency = Math.max(this.maxLatency, latency);

            // Update rolling average
            this.averageLatency = ((this.averageLatency * (measurementCount - 1)) + latency) / measurementCount;
        }

        // Getters
        public long getLastLatency() {
            return lastLatency;
        }

        public double getAverageLatency() {
            return averageLatency;
        }

        public long getMinLatency() {
            return minLatency == Long.MAX_VALUE ? 0 : minLatency;
        }

        public long getMaxLatency() {
            return maxLatency;
        }

        public int getMeasurementCount() {
            return measurementCount;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        public long getLastPingTime() {
            return lastPingTime;
        }

        public void setLastPingTime(long lastPingTime) {
            this.lastPingTime = lastPingTime;
        }
    }

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

            // Initialize latency tracking for this session
            LatencyData latencyData = new LatencyData();
            sessionLatencies.put(session.getId(), latencyData);

            // Start heartbeat for this session
            startHeartbeat(session);

            logger.info("WebSocket connection established for IP: {}. Total connections: {}",
                    clientIP, sessions.size());

            // Send welcome message
            TextMessage welcomeMessage = new TextMessage(
                    "{\"type\":\"connected\",\"message\":\"Connected to screen share\"}");
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

            return session.getRemoteAddress() != null ? session.getRemoteAddress().toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = message.getPayload().toString();
        logger.debug("Received message from viewer {}: {}", session.getId(), payload);

        try {
            // Handle ping/pong messages for latency measurement
            if (payload.startsWith("{\"type\":\"pong\"")) {
                handlePongMessage(session, payload);
            } else if (payload.startsWith("{\"type\":\"ping\"")) {
                handlePingMessage(session, payload);
            }
            // Handle other client messages as needed

        } catch (Exception e) {
            logger.error("Error processing message from session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        cleanupSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        cleanupSession(session);
        logger.info("Screen viewer disconnected: {} (Remaining viewers: {})", session.getId(), sessions.size());
    }

    private void cleanupSession(WebSocketSession session) {
        sessions.remove(session);
        // Clean up latency tracking data
        sessionLatencies.remove(session.getId());
        pendingPings.remove(session.getId());

        LatencyData latencyData = sessionLatencies.get(session.getId());
        if (latencyData != null) {
            latencyData.setConnected(false);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Broadcasts a screen frame to all connected viewers
     * 
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
     * 
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

    // ==================== NETWORK LATENCY MEASUREMENT METHODS ====================

    /**
     * Start heartbeat mechanism for a WebSocket session
     */
    private void startHeartbeat(WebSocketSession session) {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (session.isOpen()) {
                    sendPing(session);
                } else {
                    logger.debug("Session {} is closed, stopping heartbeat", session.getId());
                }
            } catch (Exception e) {
                logger.error("Error sending ping to session {}: {}", session.getId(), e.getMessage());
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Send ping message to measure latency
     */
    private void sendPing(WebSocketSession session) {
        try {
            long timestamp = System.currentTimeMillis();
            String pingMessage = String.format("{\"type\":\"ping\",\"timestamp\":%d}", timestamp);

            // Store pending ping
            pendingPings.put(session.getId(), timestamp);

            // Update last ping time
            LatencyData latencyData = sessionLatencies.get(session.getId());
            if (latencyData != null) {
                latencyData.setLastPingTime(timestamp);
            }

            session.sendMessage(new TextMessage(pingMessage));
            logger.debug("Sent ping to session {}", session.getId());

        } catch (Exception e) {
            logger.error("Failed to send ping to session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Handle pong response from client
     */
    private void handlePongMessage(WebSocketSession session, String payload) {
        try {
            // Extract timestamp from pong message
            // Expected format: {"type":"pong","timestamp":1234567890}
            int timestampIndex = payload.indexOf("\"timestamp\":");
            if (timestampIndex == -1) {
                logger.warn("Invalid pong message format from session {}: {}", session.getId(), payload);
                return;
            }

            String timestampStr = payload.substring(timestampIndex + 12);
            timestampStr = timestampStr.replaceAll("[^0-9]", ""); // Extract only numbers

            long pingTimestamp = Long.parseLong(timestampStr);
            Long pendingPingTime = pendingPings.remove(session.getId());

            if (pendingPingTime != null && pendingPingTime.equals(pingTimestamp)) {
                long currentTime = System.currentTimeMillis();
                long latency = currentTime - pingTimestamp;

                // Update latency data
                LatencyData latencyData = sessionLatencies.get(session.getId());
                if (latencyData != null) {
                    latencyData.updateLatency(latency);
                    logger.debug("Session {} latency: {}ms (avg: {:.1f}ms)",
                            session.getId(), latency, latencyData.getAverageLatency());
                }
            } else {
                logger.warn("Received pong for unknown or expired ping from session {}", session.getId());
            }

        } catch (Exception e) {
            logger.error("Error processing pong from session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Handle ping message from client (respond with pong)
     */
    private void handlePingMessage(WebSocketSession session, String payload) {
        try {
            // Echo back as pong
            String pongMessage = payload.replace("\"type\":\"ping\"", "\"type\":\"pong\"");
            session.sendMessage(new TextMessage(pongMessage));
            logger.debug("Responded to ping from session {} with pong", session.getId());

        } catch (Exception e) {
            logger.error("Failed to respond to ping from session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Get latency data for a specific session
     */
    public LatencyData getSessionLatency(String sessionId) {
        return sessionLatencies.get(sessionId);
    }

    /**
     * Get latency data for all active sessions
     */
    public Map<String, LatencyData> getAllSessionLatencies() {
        return new ConcurrentHashMap<>(sessionLatencies);
    }

    /**
     * Get average latency across all sessions
     */
    public double getAverageLatency() {
        return sessionLatencies.values().stream()
                .filter(data -> data.getMeasurementCount() > 0)
                .mapToDouble(LatencyData::getAverageLatency)
                .average()
                .orElse(0.0);
    }

    /**
     * Cleanup method for application shutdown
     */
    public void shutdown() {
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdown();
            try {
                if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
