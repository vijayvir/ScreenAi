package com.screenai.handler;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.screenai.model.ReactiveRoom;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Reactive WebSocket Handler for Screen Sharing
 * Uses Spring WebFlux + Netty for non-blocking, high-performance streaming
 * 
 * Key features:
 * - Non-blocking I/O via Netty
 * - Reactive streams for video data relay
 * - Automatic backpressure handling
 * - Efficient binary data handling
 */
@Component
public class ReactiveScreenShareHandler implements WebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactiveScreenShareHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Room management
    private final Map<String, ReactiveRoom> rooms = new ConcurrentHashMap<>();
    
    // Session to room mapping
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    
    // Session roles
    private final Map<String, String> sessionRoles = new ConcurrentHashMap<>();
    
    // Session sinks for sending messages (session ID -> sink)
    private final Map<String, Sinks.Many<WebSocketMessage>> sessionSinks = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        logger.info("🔌 New WebSocket connection: {}", sessionId);
        
        // Create a sink for outbound messages to this session
        Sinks.Many<WebSocketMessage> outboundSink = Sinks.many().multicast().onBackpressureBuffer(1024);
        sessionSinks.put(sessionId, outboundSink);
        
        // Send welcome message
        sendTextToSession(session, createWelcomeMessage(sessionId));
        
        // Handle incoming messages
        Mono<Void> input = session.receive()
            .doOnNext(message -> handleMessage(session, message))
            .doOnError(error -> logger.error("❌ Error receiving message: {}", error.getMessage()))
            .doFinally(signalType -> handleDisconnect(session))
            .then();
        
        // Send outbound messages
        Mono<Void> output = session.send(outboundSink.asFlux());
        
        // Combine input and output - both must complete
        return Mono.zip(input, output).then();
    }
    
    /**
     * Handle incoming WebSocket message (text or binary)
     */
    private void handleMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            switch (message.getType()) {
                case TEXT -> handleTextMessage(session, message.getPayloadAsText());
                case BINARY -> handleBinaryMessage(session, message.getPayload().asByteBuffer());
                default -> logger.debug("Ignoring message type: {}", message.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling message: {}", e.getMessage());
        }
    }
    
    /**
     * Handle text messages (JSON commands)
     */
    private void handleTextMessage(WebSocketSession session, String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String type = json.has("type") ? json.get("type").asText() : null;
            
            if (type == null) {
                sendError(session, "Missing 'type' field");
                return;
            }
            
            logger.debug("📨 Received command: {} from {}", type, session.getId());
            
            switch (type) {
                case "create-room" -> handleCreateRoom(session, json);
                case "join-room" -> handleJoinRoom(session, json);
                case "leave-room" -> handleLeaveRoom(session);
                case "get-viewer-count" -> handleGetViewerCount(session);
                default -> sendError(session, "Unknown command: " + type);
            }
        } catch (Exception e) {
            logger.error("Error parsing JSON: {}", e.getMessage());
            sendError(session, "Invalid JSON: " + e.getMessage());
        }
    }
    
    /**
     * Handle binary video data from presenter
     */
    private void handleBinaryMessage(WebSocketSession session, ByteBuffer data) {
        String sessionId = session.getId();
        String role = sessionRoles.get(sessionId);
        
        // Only presenters can send binary data
        if (!"presenter".equals(role)) {
            logger.warn("❌ Non-presenter tried to send binary data: {}", sessionId);
            return;
        }
        
        String roomId = sessionToRoom.get(sessionId);
        if (roomId == null) {
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        
        byte[] videoData = new byte[data.remaining()];
        data.get(videoData);
        
        // Check if this is an init segment (SPS/PPS or ftyp/moov)
        if (isInitSegment(videoData)) {
            logger.info("🎬 Init segment received for room {} ({} bytes)", roomId, videoData.length);
            room.setCachedInitSegment(videoData);
        }
        
        // Relay to all viewers
        relayToViewers(room, videoData);
    }
    
    /**
     * Relay video data to all viewers in a room
     */
    private void relayToViewers(ReactiveRoom room, byte[] videoData) {
        int viewerCount = room.getViewerCount();
        if (viewerCount == 0) {
            return;
        }
        
        int successCount = 0;
        int failCount = 0;
        
        // Send to each viewer's sink
        for (String viewerSessionId : room.getViewerSessionIds()) {
            Sinks.Many<WebSocketMessage> sink = sessionSinks.get(viewerSessionId);
            if (sink != null) {
                // Get viewer info directly from room
                Optional<ReactiveRoom.ViewerInfo> viewerInfoOpt = room.getViewerSinks().get(viewerSessionId);
                if (viewerInfoOpt != null && viewerInfoOpt.isPresent()) {
                    ReactiveRoom.ViewerInfo viewerInfo = viewerInfoOpt.get();
                    try {
                        WebSocketSession viewerSession = viewerInfo.session();
                        if (viewerSession.isOpen()) {
                            WebSocketMessage binaryMsg = viewerSession.binaryMessage(factory -> 
                                factory.wrap(videoData)
                            );
                            Sinks.EmitResult result = sink.tryEmitNext(binaryMsg);
                            if (result.isSuccess()) {
                                successCount++;
                            } else {
                                failCount++;
                                logger.warn("Failed to emit to viewer {}: {}", viewerSessionId, result);
                            }
                        } else {
                            logger.debug("Viewer session {} is closed", viewerSessionId);
                            failCount++;
                        }
                    } catch (Exception e) {
                        failCount++;
                        logger.warn("Error relaying to viewer {}: {}", viewerSessionId, e.getMessage());
                    }
                } else {
                    logger.debug("No viewer info for session {}", viewerSessionId);
                }
            } else {
                logger.debug("No sink for viewer {}", viewerSessionId);
            }
        }
        
        // Log relay stats periodically
        if (successCount > 0 || failCount > 0) {
            logger.debug("📺 Relayed {} bytes to {}/{} viewers (room: {})", 
                videoData.length, successCount, viewerCount, room.getRoomId());
        }
    }
    
    /**
     * Create a new room with presenter
     */
    private void handleCreateRoom(WebSocketSession session, JsonNode json) {
        String roomId = json.has("roomId") ? json.get("roomId").asText() : null;
        if (roomId == null || roomId.isEmpty()) {
            sendError(session, "Missing roomId");
            return;
        }
        
        String sessionId = session.getId();
        
        // Check if room already exists
        if (rooms.containsKey(roomId)) {
            ReactiveRoom existingRoom = rooms.get(roomId);
            String existingPresenterId = existingRoom.getPresenterSessionId();
            
            // Check if the existing presenter is still connected
            if (existingPresenterId != null && sessionSinks.containsKey(existingPresenterId)) {
                // Presenter is still active - generate a new unique room ID
                String newRoomId = roomId + "-" + java.util.UUID.randomUUID().toString().substring(0, 4);
                logger.info("⚠️ Room {} exists with active presenter, creating new room: {}", roomId, newRoomId);
                roomId = newRoomId;
            } else {
                // Presenter is gone - reclaim the room
                logger.info("🔄 Reclaiming stale room: {} (previous presenter: {})", roomId, existingPresenterId);
                cleanupRoom(roomId);
            }
        }
        
        ReactiveRoom room = new ReactiveRoom(roomId, sessionId, session);
        rooms.put(roomId, room);
        sessionToRoom.put(sessionId, roomId);
        sessionRoles.put(sessionId, "presenter");
        
        String response = String.format(
            "{\"type\":\"room-created\",\"roomId\":\"%s\",\"role\":\"presenter\"}",
            roomId
        );
        sendTextToSession(session, response);
        
        logger.info("✅ Room created: {} by presenter: {}", roomId, sessionId);
    }
    
    /**
     * Clean up a room and its associated resources
     */
    private void cleanupRoom(String roomId) {
        ReactiveRoom room = rooms.remove(roomId);
        if (room != null) {
            // Clean up viewers' references to this room
            for (String viewerId : room.getViewerSessionIds()) {
                sessionToRoom.remove(viewerId);
                sessionRoles.remove(viewerId);
            }
            // Clean up presenter reference
            String presenterId = room.getPresenterSessionId();
            if (presenterId != null) {
                sessionToRoom.remove(presenterId);
                sessionRoles.remove(presenterId);
            }
            logger.info("🧹 Cleaned up room: {}", roomId);
        }
    }
    
    /**
     * Join an existing room as viewer
     */
    private void handleJoinRoom(WebSocketSession session, JsonNode json) {
        String roomId = json.has("roomId") ? json.get("roomId").asText() : null;
        if (roomId == null || roomId.isEmpty()) {
            sendError(session, "Missing roomId");
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(session, "Room not found: " + roomId);
            return;
        }
        
        String sessionId = session.getId();
        Sinks.Many<WebSocketMessage> sink = sessionSinks.get(sessionId);
        
        room.addViewer(sessionId, session, sink);
        sessionToRoom.put(sessionId, roomId);
        sessionRoles.put(sessionId, "viewer");
        
        // Send success response
        String response = String.format(
            "{\"type\":\"room-joined\",\"roomId\":\"%s\",\"role\":\"viewer\",\"viewerCount\":%d}",
            roomId, room.getViewerCount()
        );
        sendTextToSession(session, response);
        
        // Send cached init segment if available
        byte[] initSegment = room.getCachedInitSegment();
        if (initSegment != null && initSegment.length > 0) {
            try {
                WebSocketMessage binaryMsg = session.binaryMessage(factory -> 
                    factory.wrap(initSegment)
                );
                if (sink != null) {
                    sink.tryEmitNext(binaryMsg);
                    logger.info("📤 Sent init segment ({} bytes) to viewer {}", initSegment.length, sessionId);
                }
            } catch (Exception e) {
                logger.error("Failed to send init segment: {}", e.getMessage());
            }
        }
        
        // Notify presenter of viewer count
        notifyPresenterOfViewerCount(room);
        
        logger.info("✅ Viewer {} joined room: {} (Total: {})", sessionId, roomId, room.getViewerCount());
    }
    
    /**
     * Leave current room
     */
    private void handleLeaveRoom(WebSocketSession session) {
        String sessionId = session.getId();
        removeSessionFromRoom(sessionId);
        
        sendTextToSession(session, "{\"type\":\"room-left\",\"message\":\"Left room\"}");
    }
    
    /**
     * Get viewer count for current room
     */
    private void handleGetViewerCount(WebSocketSession session) {
        String sessionId = session.getId();
        String roomId = sessionToRoom.get(sessionId);
        
        if (roomId == null) {
            sendError(session, "Not in any room");
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(session, "Room not found");
            return;
        }
        
        String response = String.format("{\"type\":\"viewer-count\",\"count\":%d}", room.getViewerCount());
        sendTextToSession(session, response);
    }
    
    /**
     * Handle session disconnect
     */
    private void handleDisconnect(WebSocketSession session) {
        String sessionId = session.getId();
        logger.info("🚪 Session disconnected: {}", sessionId);
        
        removeSessionFromRoom(sessionId);
        sessionSinks.remove(sessionId);
    }
    
    /**
     * Remove session from its room
     */
    private void removeSessionFromRoom(String sessionId) {
        String roomId = sessionToRoom.remove(sessionId);
        String role = sessionRoles.remove(sessionId);
        
        if (roomId == null) {
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        
        if ("presenter".equals(role)) {
            // Presenter left - close room
            logger.info("🚪 Presenter left room: {} - closing", roomId);
            
            // Notify all viewers
            for (String viewerSessionId : room.getViewerSessionIds()) {
                Sinks.Many<WebSocketMessage> viewerSink = sessionSinks.get(viewerSessionId);
                if (viewerSink != null) {
                    room.getViewerSinks().get(viewerSessionId).ifPresent(viewerInfo -> {
                        try {
                            WebSocketMessage msg = viewerInfo.session().textMessage(
                                "{\"type\":\"presenter-left\",\"message\":\"Presenter disconnected\"}"
                            );
                            viewerSink.tryEmitNext(msg);
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
                }
                sessionToRoom.remove(viewerSessionId);
                sessionRoles.remove(viewerSessionId);
            }
            
            rooms.remove(roomId);
            logger.info("🗑️ Room closed: {} (Had {} viewers)", roomId, room.getViewerCount());
        } else {
            // Viewer left
            room.removeViewer(sessionId);
            notifyPresenterOfViewerCount(room);
            logger.info("👋 Viewer left room: {} (Remaining: {})", roomId, room.getViewerCount());
        }
    }
    
    /**
     * Notify presenter of current viewer count
     */
    private void notifyPresenterOfViewerCount(ReactiveRoom room) {
        String presenterSessionId = room.getPresenterSessionId();
        Sinks.Many<WebSocketMessage> sink = sessionSinks.get(presenterSessionId);
        
        if (sink != null && room.getPresenterSession() != null) {
            try {
                String message = String.format("{\"type\":\"viewer-count\",\"count\":%d}", room.getViewerCount());
                WebSocketMessage msg = room.getPresenterSession().textMessage(message);
                sink.tryEmitNext(msg);
            } catch (Exception e) {
                logger.debug("Failed to notify presenter: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Send text message to a session
     */
    private void sendTextToSession(WebSocketSession session, String text) {
        Sinks.Many<WebSocketMessage> sink = sessionSinks.get(session.getId());
        if (sink != null) {
            try {
                WebSocketMessage msg = session.textMessage(text);
                sink.tryEmitNext(msg);
            } catch (Exception e) {
                logger.error("Failed to send text: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Send error message to a session
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        String response = String.format(
            "{\"type\":\"error\",\"message\":\"%s\"}",
            errorMessage.replace("\"", "\\\"")
        );
        sendTextToSession(session, response);
    }
    
    /**
     * Create welcome message
     */
    private String createWelcomeMessage(String sessionId) {
        return String.format(
            "{\"type\":\"connected\",\"sessionId\":\"%s\",\"message\":\"Connected to ScreenAI Relay Server (WebFlux+Netty)\",\"role\":\"pending\"}",
            sessionId
        );
    }
    
    /**
     * Check if binary data is an init segment (H.264 SPS/PPS or fMP4 ftyp/moov)
     */
    private boolean isInitSegment(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        
        // Check for fMP4 boxes
        if (data.length >= 8) {
            String boxType = new String(data, 4, 4);
            if ("ftyp".equals(boxType) || "moov".equals(boxType)) {
                return true;
            }
        }
        
        // Check for H.264 Annex B NAL start codes (00 00 00 01 or 00 00 01)
        // SPS NAL type = 7, PPS NAL type = 8
        for (int i = 0; i < data.length - 4; i++) {
            // Check for 4-byte start code
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == 1) {
                if (i + 4 < data.length) {
                    int nalType = data[i + 4] & 0x1F;
                    if (nalType == 7 || nalType == 8) {
                        return true;
                    }
                }
            }
            // Check for 3-byte start code
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1) {
                if (i + 3 < data.length) {
                    int nalType = data[i + 3] & 0x1F;
                    if (nalType == 7 || nalType == 8) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get server statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalRooms", rooms.size());
        stats.put("totalSessions", sessionSinks.size());
        
        int totalViewers = rooms.values().stream()
            .mapToInt(ReactiveRoom::getViewerCount)
            .sum();
        stats.put("totalViewers", totalViewers);
        
        return stats;
    }
}
