package com.screenai.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.screenai.model.Room;
import com.screenai.service.PerformanceMonitorService;
import com.screenai.service.SessionManager;
import com.screenai.service.StreamRelayService;

import jakarta.annotation.PostConstruct;

/**
 * WebSocket handler for screen sharing RELAY functionality
 * Relays real-time binary video streams from presenters to viewers
 * 
 * This handler:
 * - Accepts connections from presenters (sources) and viewers (watchers)
 * - Relays H.264 fMP4 video data from presenters to viewers in the same room
 * - Manages rooms with one presenter and multiple viewers
 * - Handles connection lifecycle (open, close, error)
 * - Caches init segments for late-joining viewers
 */
@Component
public class ScreenShareRelayHandler implements WebSocketHandler, PerformanceMonitorService.MetricsListener {
    
    private static final Logger logger = LoggerFactory.getLogger(ScreenShareRelayHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final int MAX_CONNECTIONS = 100; // Increased since server is lightweight
    private static final long MAX_SESSION_DURATION = 3600000; // 1 hour
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private StreamRelayService relayService;
    
    @Autowired
    private PerformanceMonitorService performanceMonitor;
    
    @PostConstruct
    public void init() {
        // Register as metrics listener
        performanceMonitor.addMetricsListener(this);
        logger.info("✅ WebSocket RelayHandler initialized and registered for performance metrics");
    }
    
    // ==========================================
    // CONNECTION LIFECYCLE METHODS
    // ==========================================
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Validate connection limits
        if (sessionManager.getTotalSessionCount() >= MAX_CONNECTIONS) {
            logger.warn("❌ Connection limit reached ({}), rejecting: {}", MAX_CONNECTIONS, session.getId());
            session.close(new CloseStatus(1008, "Server at capacity"));
            return;
        }
        
        String clientIP = getClientIP(session);
        session.getAttributes().put("ip", clientIP);
        session.getAttributes().put("connectTime", System.currentTimeMillis());
        
        // Send welcome message with connection info
        String welcomeMsg = String.format(
            "{\"type\":\"connected\",\"sessionId\":\"%s\",\"message\":\"Connected to ScreenAI Relay Server\",\"role\":\"pending\"}",
            session.getId()
        );
        session.sendMessage(new TextMessage(welcomeMsg));
        
        logger.info("🔌 New connection: {} from IP: {}", session.getId(), clientIP);
        logger.info("📊 Server stats: {} total sessions, {} rooms", 
                   sessionManager.getTotalSessionCount(), sessionManager.getRoomCount());
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof BinaryMessage binaryMessage) {
            handleBinaryMessage(session, binaryMessage);
        } else if (message instanceof TextMessage textMessage) {
            handleTextMessage(session, textMessage);
        }
    }
    
    /**
     * Handle binary video data from presenter
     */
    private void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // Only presenters can send binary data
        if (!sessionManager.isPresenter(session)) {
            logger.warn("❌ Non-presenter {} tried to send binary data", session.getId());
            return;
        }
        
        byte[] videoData = message.getPayload().array();
        
        // Relay to all viewers in the room
        relayService.relayVideoData(session, videoData);
    }
    
    /**
     * Handle control messages (JSON commands)
     * 
     * Expected message formats:
     * - Create room: {"type":"create-room","roomId":"room123"}
     * - Join room: {"type":"join-room","roomId":"room123"}
     * - Leave room: {"type":"leave-room"}
     * - Viewer count request: {"type":"get-viewer-count"}
     */
    private void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            logger.debug("📨 Received text message from {}: {}", session.getId(), payload);
            
            // Parse JSON command
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> command = objectMapper.readValue(payload, java.util.Map.class);
            String type = (String) command.get("type");
            
            if (type == null) {
                sendError(session, "Missing 'type' field in command");
                return;
            }
            
            switch (type) {
                case "create-room" -> handleCreateRoom(session, command);
                case "join-room" -> handleJoinRoom(session, command);
                case "leave-room" -> handleLeaveRoom(session);
                case "get-viewer-count" -> handleGetViewerCount(session);
                default -> sendError(session, "Unknown command type: " + type);
            }
            
        } catch (Exception e) {
            logger.error("Error handling text message from {}: {}", session.getId(), e.getMessage());
            sendError(session, "Failed to process command: " + e.getMessage());
        }
    }
    
    /**
     * Handle "create-room" command - Makes this session a presenter
     */
    private void handleCreateRoom(WebSocketSession session, java.util.Map<String, Object> command) throws Exception {
        String roomId = (String) command.get("roomId");
        if (roomId == null || roomId.isEmpty()) {
            sendError(session, "Missing or invalid roomId");
            return;
        }
        
        // Check if room already exists
        if (sessionManager.getRoom(roomId) != null) {
            sendError(session, "Room already exists: " + roomId);
            return;
        }
        
        // Create room with this session as presenter
        sessionManager.createRoom(roomId, session);
        
        String response = String.format(
            "{\"type\":\"room-created\",\"roomId\":\"%s\",\"role\":\"presenter\"}",
            roomId
        );
        session.sendMessage(new TextMessage(response));
        
        logger.info("✅ Room created: {} by presenter: {}", roomId, session.getId());
    }
    
    /**
     * Handle "join-room" command - Makes this session a viewer
     */
    private void handleJoinRoom(WebSocketSession session, java.util.Map<String, Object> command) throws Exception {
        String roomId = (String) command.get("roomId");
        if (roomId == null || roomId.isEmpty()) {
            sendError(session, "Missing or invalid roomId");
            return;
        }
        
        Room room = sessionManager.getRoom(roomId);
        if (room == null) {
            sendError(session, "Room not found: " + roomId);
            return;
        }
        
        // Add as viewer
        boolean added = sessionManager.addViewer(roomId, session);
        if (!added) {
            sendError(session, "Failed to join room: " + roomId);
            return;
        }
        
        // Send success response
        String response = String.format(
            "{\"type\":\"room-joined\",\"roomId\":\"%s\",\"role\":\"viewer\",\"viewerCount\":%d}",
            roomId, room.getViewerCount()
        );
        session.sendMessage(new TextMessage(response));
        
        // Send cached init segment if available
        relayService.sendInitSegmentToViewer(session, room);
        
        // Notify presenter of new viewer
        notifyPresenterOfViewerCount(room);
        
        logger.info("✅ Viewer {} joined room: {} (Total viewers: {})", 
                   session.getId(), roomId, room.getViewerCount());
    }
    
    /**
     * Handle "leave-room" command
     */
    private void handleLeaveRoom(WebSocketSession session) throws Exception {
        sessionManager.removeSession(session);
        
        String response = "{\"type\":\"room-left\",\"message\":\"Successfully left room\"}";
        session.sendMessage(new TextMessage(response));
    }
    
    /**
     * Handle "get-viewer-count" command
     */
    private void handleGetViewerCount(WebSocketSession session) throws Exception {
        Room room = sessionManager.getRoomForSession(session);
        if (room == null) {
            sendError(session, "Not in any room");
            return;
        }
        
        String response = String.format(
            "{\"type\":\"viewer-count\",\"count\":%d}",
            room.getViewerCount()
        );
        session.sendMessage(new TextMessage(response));
    }
    
    /**
     * Send error message to client
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            String response = String.format(
                "{\"type\":\"error\",\"message\":\"%s\"}",
                errorMessage.replace("\"", "\\\"")
            );
            session.sendMessage(new TextMessage(response));
        } catch (Exception e) {
            logger.error("Failed to send error message: {}", e.getMessage());
        }
    }
    
    /**
     * Notify presenter about current viewer count
     */
    private void notifyPresenterOfViewerCount(Room room) {
        try {
            if (room.getPresenter() != null && room.getPresenter().isOpen()) {
                String message = String.format(
                    "{\"type\":\"viewer-count\",\"count\":%d}",
                    room.getViewerCount()
                );
                room.getPresenter().sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            logger.error("Failed to notify presenter: {}", e.getMessage());
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("❌ Transport error for session {}: {}", session.getId(), exception.getMessage());
        sessionManager.removeSession(session);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        sessionManager.removeSession(session);
        logger.info("🚪 Connection closed: {} - {} (Remaining sessions: {})", 
                   session.getId(), closeStatus, sessionManager.getTotalSessionCount());
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    // ==========================================
    // UTILITY METHODS
    // ==========================================
    
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
            
            if (session.getRemoteAddress() != null) {
                return session.getRemoteAddress().toString();
            }
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Periodic cleanup of stale sessions
     * Runs every minute to remove closed or timed-out sessions
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupStaleSessions() {
        long currentTime = System.currentTimeMillis();
        int roomsCleaned = 0;
        
        for (Room room : sessionManager.getAllRooms()) {
            // Check if room is too old
            if (currentTime - room.getCreatedAt() > MAX_SESSION_DURATION) {
                logger.info("🗑️ Closing stale room: {} (age: {} minutes)", 
                           room.getRoomId(), (currentTime - room.getCreatedAt()) / 60000);
                sessionManager.closeRoom(room.getRoomId());
                roomsCleaned++;
                continue;
            }
            
            // Check if presenter is still active
            if (!room.isPresenterActive()) {
                logger.info("🗑️ Closing room with inactive presenter: {}", room.getRoomId());
                sessionManager.closeRoom(room.getRoomId());
                roomsCleaned++;
            }
        }
        
        if (roomsCleaned > 0) {
            logger.debug("Cleaned up {} stale rooms. Active rooms: {}", roomsCleaned, sessionManager.getRoomCount());
        }
    }
    
    // ==========================================
    // PERFORMANCE METRICS
    // ==========================================
    
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
            
            // Broadcast to all presenters (viewers don't need server metrics)
            int sentCount = 0;
            for (Room room : sessionManager.getAllRooms()) {
                WebSocketSession presenter = room.getPresenter();
                if (presenter != null && presenter.isOpen()) {
                    try {
                        presenter.sendMessage(message);
                        sentCount++;
                    } catch (Exception e) {
                        logger.debug("Failed to send metrics to presenter {}: {}", 
                                    presenter.getId(), e.getMessage());
                    }
                }
            }
            
            if (sentCount > 0) {
                logger.debug("📊 Performance metrics sent to {} presenters", sentCount);
            }
            
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
        
        public String getType() { return type; }
        public PerformanceMetrics getMetrics() { return metrics; }
    }
}
