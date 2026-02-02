package com.screenai.handler;

import java.net.InetSocketAddress;
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
import com.screenai.dto.ErrorResponse.ErrorCode;
import com.screenai.exception.RateLimitException;
import com.screenai.exception.RoomException;
import com.screenai.model.ReactiveRoom;
import com.screenai.security.WebSocketAuthHandler;
import com.screenai.security.WebSocketAuthHandler.AuthenticatedUser;
import com.screenai.service.ConnectionThrottleService;
import com.screenai.service.RateLimitService;
import com.screenai.service.RoomSecurityService;
import com.screenai.service.SecurityAuditService;
import com.screenai.validation.InputValidator;

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
 * - JWT authentication
 * - Room password protection
 * - Viewer approval system
 * - Rate limiting
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
    
    // Session to authenticated user mapping
    private final Map<String, AuthenticatedUser> sessionUsers = new ConcurrentHashMap<>();
    
    // Session to IP address mapping
    private final Map<String, String> sessionIpAddresses = new ConcurrentHashMap<>();
    
    // Security services
    private final WebSocketAuthHandler authHandler;
    private final RoomSecurityService roomSecurityService;
    private final RateLimitService rateLimitService;
    private final ConnectionThrottleService connectionThrottleService;
    private final SecurityAuditService auditService;
    private final InputValidator inputValidator;
    
    public ReactiveScreenShareHandler(
            WebSocketAuthHandler authHandler,
            RoomSecurityService roomSecurityService,
            RateLimitService rateLimitService,
            ConnectionThrottleService connectionThrottleService,
            SecurityAuditService auditService,
            InputValidator inputValidator) {
        this.authHandler = authHandler;
        this.roomSecurityService = roomSecurityService;
        this.rateLimitService = rateLimitService;
        this.connectionThrottleService = connectionThrottleService;
        this.auditService = auditService;
        this.inputValidator = inputValidator;
    }
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        String ipAddress = extractIpAddress(session);
        
        logger.info("🔌 New WebSocket connection: {} from IP: {}", sessionId, ipAddress);
        
        // Check if IP is blocked
        if (connectionThrottleService.isBlockedSync(ipAddress)) {
            logger.warn("🚫 Blocked IP attempted connection: {}", ipAddress);
            return session.close();
        }
        
        // Store IP address for this session
        sessionIpAddresses.put(sessionId, ipAddress);
        
        // Create a sink for outbound messages to this session
        Sinks.Many<WebSocketMessage> outboundSink = Sinks.many().multicast().onBackpressureBuffer(1024);
        sessionSinks.put(sessionId, outboundSink);
        
        // Authenticate via token in query param - REQUIRED
        return authHandler.authenticate(session.getHandshakeInfo().getUri(), sessionId, ipAddress)
            .switchIfEmpty(Mono.defer(() -> {
                // No valid authentication - reject the connection
                logger.warn("🚫 Unauthenticated WebSocket connection rejected: {} from IP: {}", sessionId, ipAddress);
                auditService.logConnectionBlocked(ipAddress, "missing_or_invalid_token").subscribe();
                sendErrorAndClose(session, ErrorCode.AUTH_001, "Authentication required. Please provide a valid JWT token via ?token= query parameter.");
                return Mono.empty();
            }))
            .flatMap(user -> {
                sessionUsers.put(sessionId, user);
                logger.info("✅ Authenticated WebSocket user: {}", user.username());
                
                // Send welcome message
                sendTextToSession(session, createWelcomeMessage(sessionId, user));
                
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
            });
    }
    
    /**
     * Extract IP address from WebSocket session
     */
    private String extractIpAddress(WebSocketSession session) {
        InetSocketAddress remoteAddress = session.getHandshakeInfo().getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }
    
    /**
     * Handle incoming WebSocket message (text or binary)
     */
    private void handleMessage(WebSocketSession session, WebSocketMessage message) {
        String sessionId = session.getId();
        String ipAddress = sessionIpAddresses.getOrDefault(sessionId, "unknown");
        
        try {
            // Check rate limit
            rateLimitService.checkMessageRateLimit(sessionId, ipAddress);
            
            switch (message.getType()) {
                case TEXT -> handleTextMessage(session, message.getPayloadAsText());
                case BINARY -> {
                    org.springframework.core.io.buffer.DataBuffer payload = message.getPayload();
                    byte[] bytes = new byte[payload.readableByteCount()];
                    payload.read(bytes);
                    
                    // Validate binary size
                    inputValidator.validateBinarySize(bytes.length);
                    
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    handleBinaryMessage(session, buffer);
                }
                default -> logger.debug("Ignoring message type: {}", message.getType());
            }
        } catch (RateLimitException e) {
            sendError(session, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("Error handling message: {}", e.getMessage());
            sendError(session, ErrorCode.SRV_001, "Error processing message");
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
                sendError(session, ErrorCode.VAL_002, "Missing 'type' field");
                return;
            }
            
            logger.debug("📨 Received command: {} from {}", type, session.getId());
            
            switch (type) {
                case "create-room" -> handleCreateRoom(session, json);
                case "join-room" -> handleJoinRoom(session, json);
                case "leave-room" -> handleLeaveRoom(session);
                case "get-viewer-count" -> handleGetViewerCount(session);
                // New security commands
                case "approve-viewer" -> handleApproveViewer(session, json);
                case "deny-viewer" -> handleDenyViewer(session, json);
                case "ban-viewer" -> handleBanViewer(session, json);
                case "kick-viewer" -> handleKickViewer(session, json);
                default -> sendError(session, ErrorCode.VAL_001, "Unknown command: " + type);
            }
        } catch (RoomException e) {
            logger.warn("Room error: {}", e.getMessage());
            sendError(session, e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            logger.error("Error parsing JSON: {}", e.getMessage());
            sendError(session, ErrorCode.VAL_001, "Invalid JSON format");
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
        String password = json.has("password") ? json.get("password").asText() : null;
        int maxViewers = json.has("maxViewers") ? json.get("maxViewers").asInt(50) : 50;
        
        String sessionId = session.getId();
        String ipAddress = sessionIpAddresses.getOrDefault(sessionId, "unknown");
        AuthenticatedUser user = sessionUsers.get(sessionId);
        String username = user != null ? user.username() : null;
        
        // Validate room ID
        if (roomId == null || roomId.isEmpty()) {
            sendError(session, ErrorCode.VAL_002, "Missing roomId");
            return;
        }
        
        try {
            inputValidator.validateRoomId(roomId);
            inputValidator.validateRoomPassword(password);
        } catch (RoomException e) {
            sendError(session, e.getErrorCode(), e.getMessage());
            return;
        }
        
        // Check rate limit for room creation
        try {
            rateLimitService.checkRoomCreationRateLimit(ipAddress, username);
        } catch (RateLimitException e) {
            sendError(session, e.getErrorCode(), e.getMessage());
            return;
        }
        
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
        
        // Create room with optional password
        ReactiveRoom room = new ReactiveRoom(roomId, sessionId, session, username);
        room.setMaxViewers(Math.min(maxViewers, 100)); // Cap at 100
        
        // Set up password protection if provided
        boolean hasPassword = password != null && !password.isEmpty();
        if (hasPassword) {
            RoomSecurityService.PasswordHashResult hashResult = roomSecurityService.createPasswordHash(password);
            room.setPassword(hashResult.hash(), hashResult.salt());
            
            // Generate access code for easy sharing
            String accessCode = roomSecurityService.generateAccessCode();
            room.setAccessCode(accessCode, roomSecurityService.calculateAccessCodeExpiration());
        }
        
        rooms.put(roomId, room);
        sessionToRoom.put(sessionId, roomId);
        sessionRoles.put(sessionId, "presenter");
        
        // Build response
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("{\"type\":\"room-created\"");
        responseBuilder.append(",\"roomId\":\"").append(roomId).append("\"");
        responseBuilder.append(",\"role\":\"presenter\"");
        responseBuilder.append(",\"passwordProtected\":").append(hasPassword);
        responseBuilder.append(",\"requiresApproval\":").append(room.requiresApproval());
        if (room.getAccessCode() != null) {
            responseBuilder.append(",\"accessCode\":\"").append(room.getAccessCode()).append("\"");
        }
        responseBuilder.append("}");
        
        sendTextToSession(session, responseBuilder.toString());
        
        // Log audit event
        auditService.logRoomCreated(username, sessionId, roomId, ipAddress, hasPassword).subscribe();
        
        logger.info("✅ Room created: {} by presenter: {} (password protected: {})", roomId, sessionId, hasPassword);
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
        String password = json.has("password") ? json.get("password").asText() : null;
        String accessCode = json.has("accessCode") ? json.get("accessCode").asText() : null;
        
        String sessionId = session.getId();
        String ipAddress = sessionIpAddresses.getOrDefault(sessionId, "unknown");
        AuthenticatedUser user = sessionUsers.get(sessionId);
        String username = user != null ? user.username() : "anonymous";
        
        // Validate room ID
        if (roomId == null || roomId.isEmpty()) {
            sendError(session, ErrorCode.VAL_002, "Missing roomId");
            return;
        }
        
        try {
            inputValidator.validateRoomId(roomId);
        } catch (RoomException e) {
            sendError(session, e.getErrorCode(), e.getMessage());
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(session, ErrorCode.ROOM_001, "Room not found");
            auditService.logRoomAccessDenied(username, sessionId, roomId, ipAddress, "Room not found").subscribe();
            return;
        }
        
        // Check if user is banned
        if (room.isBanned(sessionId)) {
            sendError(session, ErrorCode.ROOM_006, "You are banned from this room");
            auditService.logRoomAccessDenied(username, sessionId, roomId, ipAddress, "Banned").subscribe();
            return;
        }
        
        // Check if room is full
        if (room.isFull()) {
            sendError(session, ErrorCode.ROOM_004, "Room is full");
            auditService.logRoomAccessDenied(username, sessionId, roomId, ipAddress, "Room full").subscribe();
            return;
        }
        
        // Validate password for password-protected rooms
        if (room.isPasswordProtected()) {
            // Can use either password or access code
            boolean accessGranted = false;
            
            if (accessCode != null && !accessCode.isEmpty()) {
                // Validate access code
                if (room.isAccessCodeValid() && accessCode.equals(room.getAccessCode())) {
                    accessGranted = true;
                }
            }
            
            if (!accessGranted && password != null) {
                // Validate password
                if (roomSecurityService.verifyPassword(password, room.getPasswordHash(), room.getPasswordSalt())) {
                    accessGranted = true;
                }
            }
            
            if (!accessGranted) {
                sendError(session, ErrorCode.ROOM_003, "Invalid password or access code");
                auditService.logRoomAccessDenied(username, sessionId, roomId, ipAddress, "Invalid credentials").subscribe();
                return;
            }
        }
        
        Sinks.Many<WebSocketMessage> sink = sessionSinks.get(sessionId);
        
        // If room requires approval, add to pending
        if (room.requiresApproval()) {
            room.addPendingViewer(sessionId, username, session, sink);
            sessionToRoom.put(sessionId, roomId);
            sessionRoles.put(sessionId, "pending-viewer");
            
            // Notify viewer they're waiting for approval
            String response = String.format(
                "{\"type\":\"waiting-approval\",\"roomId\":\"%s\",\"message\":\"Waiting for host approval\"}",
                roomId
            );
            sendTextToSession(session, response);
            
            // Notify presenter of pending viewer
            notifyPresenterOfPendingViewer(room, sessionId, username);
            
            logger.info("⏳ Viewer {} waiting for approval in room: {}", sessionId, roomId);
            return;
        }
        
        // Direct join (no approval required)
        addViewerToRoom(room, sessionId, username, session, sink, ipAddress);
    }
    
    /**
     * Add viewer to room and send appropriate messages
     */
    private void addViewerToRoom(ReactiveRoom room, String sessionId, String username, 
                                  WebSocketSession session, Sinks.Many<WebSocketMessage> sink, String ipAddress) {
        String roomId = room.getRoomId();
        
        room.addViewer(sessionId, username, session, sink);
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
        
        // Log audit event
        auditService.logRoomJoined(username, sessionId, roomId, ipAddress).subscribe();
        
        logger.info("✅ Viewer {} joined room: {} (Total: {})", sessionId, roomId, room.getViewerCount());
    }
    
    /**
     * Notify presenter of pending viewer request
     */
    private void notifyPresenterOfPendingViewer(ReactiveRoom room, String viewerSessionId, String viewerUsername) {
        String presenterSessionId = room.getPresenterSessionId();
        Sinks.Many<WebSocketMessage> sink = sessionSinks.get(presenterSessionId);
        
        if (sink != null && room.getPresenterSession() != null) {
            try {
                String message = String.format(
                    "{\"type\":\"viewer-request\",\"viewerSessionId\":\"%s\",\"viewerUsername\":\"%s\",\"pendingCount\":%d}",
                    viewerSessionId, viewerUsername != null ? viewerUsername : "anonymous", room.getPendingViewerCount()
                );
                WebSocketMessage msg = room.getPresenterSession().textMessage(message);
                sink.tryEmitNext(msg);
            } catch (Exception e) {
                logger.debug("Failed to notify presenter of pending viewer: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Approve a pending viewer (presenter only)
     */
    private void handleApproveViewer(WebSocketSession session, JsonNode json) {
        String sessionId = session.getId();
        String role = sessionRoles.get(sessionId);
        String roomId = sessionToRoom.get(sessionId);
        String ipAddress = sessionIpAddresses.getOrDefault(sessionId, "unknown");
        AuthenticatedUser user = sessionUsers.get(sessionId);
        String username = user != null ? user.username() : "anonymous";
        
        // Only presenter can approve
        if (!"presenter".equals(role)) {
            sendError(session, ErrorCode.AUTH_006, "Only presenter can approve viewers");
            return;
        }
        
        String viewerSessionId = json.has("viewerSessionId") ? json.get("viewerSessionId").asText() : null;
        if (viewerSessionId == null) {
            sendError(session, ErrorCode.VAL_002, "Missing viewerSessionId");
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(session, ErrorCode.ROOM_001, "Room not found");
            return;
        }
        
        ReactiveRoom.PendingViewer pending = room.getPendingViewer(viewerSessionId);
        if (pending == null) {
            sendError(session, ErrorCode.ROOM_005, "Viewer not found in pending list");
            return;
        }
        
        // Remove from pending
        room.removePendingViewer(viewerSessionId);
        
        // Add to room
        addViewerToRoom(room, viewerSessionId, pending.username(), pending.session(), pending.sink(), ipAddress);
        
        // Log audit
        auditService.logViewerApproved(username, sessionId, roomId, viewerSessionId, ipAddress).subscribe();
        
        logger.info("✅ Viewer {} approved by presenter in room {}", viewerSessionId, roomId);
        
        // Send confirmation to presenter
        String response = String.format(
            "{\"type\":\"viewer-approved\",\"viewerSessionId\":\"%s\",\"pendingCount\":%d}",
            viewerSessionId, room.getPendingViewerCount()
        );
        sendTextToSession(session, response);
    }
    
    /**
     * Deny a pending viewer (presenter only)
     */
    private void handleDenyViewer(WebSocketSession session, JsonNode json) {
        String sessionId = session.getId();
        String role = sessionRoles.get(sessionId);
        String roomId = sessionToRoom.get(sessionId);
        String ipAddress = sessionIpAddresses.getOrDefault(sessionId, "unknown");
        AuthenticatedUser user = sessionUsers.get(sessionId);
        String username = user != null ? user.username() : "anonymous";
        
        // Only presenter can deny
        if (!"presenter".equals(role)) {
            sendError(session, ErrorCode.AUTH_006, "Only presenter can deny viewers");
            return;
        }
        
        String viewerSessionId = json.has("viewerSessionId") ? json.get("viewerSessionId").asText() : null;
        if (viewerSessionId == null) {
            sendError(session, ErrorCode.VAL_002, "Missing viewerSessionId");
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(session, ErrorCode.ROOM_001, "Room not found");
            return;
        }
        
        ReactiveRoom.PendingViewer pending = room.getPendingViewer(viewerSessionId);
        if (pending == null) {
            sendError(session, ErrorCode.ROOM_005, "Viewer not found in pending list");
            return;
        }
        
        // Remove from pending
        room.removePendingViewer(viewerSessionId);
        
        // Clean up session mappings
        sessionToRoom.remove(viewerSessionId);
        sessionRoles.remove(viewerSessionId);
        
        // Notify denied viewer
        try {
            if (pending.sink() != null && pending.session() != null) {
                WebSocketMessage msg = pending.session().textMessage(
                    "{\"type\":\"access-denied\",\"message\":\"Your request to join was denied by the host\"}"
                );
                pending.sink().tryEmitNext(msg);
            }
        } catch (Exception e) {
            logger.debug("Failed to notify denied viewer: {}", e.getMessage());
        }
        
        // Log audit
        auditService.logViewerDenied(username, sessionId, roomId, viewerSessionId, ipAddress).subscribe();
        
        logger.info("❌ Viewer {} denied by presenter in room {}", viewerSessionId, roomId);
        
        // Send confirmation to presenter
        String response = String.format(
            "{\"type\":\"viewer-denied\",\"viewerSessionId\":\"%s\",\"pendingCount\":%d}",
            viewerSessionId, room.getPendingViewerCount()
        );
        sendTextToSession(session, response);
    }
    
    /**
     * Ban a viewer (presenter only) - they cannot rejoin
     */
    private void handleBanViewer(WebSocketSession session, JsonNode json) {
        String sessionId = session.getId();
        String role = sessionRoles.get(sessionId);
        String roomId = sessionToRoom.get(sessionId);
        String ipAddress = sessionIpAddresses.getOrDefault(sessionId, "unknown");
        AuthenticatedUser user = sessionUsers.get(sessionId);
        String username = user != null ? user.username() : "anonymous";
        
        // Only presenter can ban
        if (!"presenter".equals(role)) {
            sendError(session, ErrorCode.AUTH_006, "Only presenter can ban viewers");
            return;
        }
        
        String viewerSessionId = json.has("viewerSessionId") ? json.get("viewerSessionId").asText() : null;
        if (viewerSessionId == null) {
            sendError(session, ErrorCode.VAL_002, "Missing viewerSessionId");
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(session, ErrorCode.ROOM_001, "Room not found");
            return;
        }
        
        // Check if viewer is in room
        if (!room.hasViewer(viewerSessionId)) {
            sendError(session, ErrorCode.ROOM_005, "Viewer not in room");
            return;
        }
        
        // Get viewer's sink before removing
        Sinks.Many<WebSocketMessage> viewerSink = room.getViewerSinks().get(viewerSessionId)
            .map(info -> sessionSinks.get(viewerSessionId))
            .orElse(null);
        WebSocketSession viewerSession = room.getViewerSinks().get(viewerSessionId)
            .map(info -> info.session())
            .orElse(null);
        
        // Ban the viewer
        room.banSession(viewerSessionId);
        room.removeViewer(viewerSessionId);
        
        // Clean up session mappings
        sessionToRoom.remove(viewerSessionId);
        sessionRoles.remove(viewerSessionId);
        
        // Notify banned viewer
        try {
            if (viewerSink != null && viewerSession != null) {
                WebSocketMessage msg = viewerSession.textMessage(
                    "{\"type\":\"banned\",\"message\":\"You have been banned from this room\"}"
                );
                viewerSink.tryEmitNext(msg);
            }
        } catch (Exception e) {
            logger.debug("Failed to notify banned viewer: {}", e.getMessage());
        }
        
        // Log audit
        auditService.logViewerBanned(username, sessionId, roomId, viewerSessionId, ipAddress).subscribe();
        
        logger.info("🚫 Viewer {} banned from room {}", viewerSessionId, roomId);
        
        // Send confirmation to presenter
        String response = String.format(
            "{\"type\":\"viewer-banned\",\"viewerSessionId\":\"%s\",\"viewerCount\":%d}",
            viewerSessionId, room.getViewerCount()
        );
        sendTextToSession(session, response);
    }
    
    /**
     * Kick a viewer (presenter only) - they can rejoin
     */
    private void handleKickViewer(WebSocketSession session, JsonNode json) {
        String sessionId = session.getId();
        String role = sessionRoles.get(sessionId);
        String roomId = sessionToRoom.get(sessionId);
        String ipAddress = sessionIpAddresses.getOrDefault(sessionId, "unknown");
        AuthenticatedUser user = sessionUsers.get(sessionId);
        String username = user != null ? user.username() : "anonymous";
        
        // Only presenter can kick
        if (!"presenter".equals(role)) {
            sendError(session, ErrorCode.AUTH_006, "Only presenter can kick viewers");
            return;
        }
        
        String viewerSessionId = json.has("viewerSessionId") ? json.get("viewerSessionId").asText() : null;
        if (viewerSessionId == null) {
            sendError(session, ErrorCode.VAL_002, "Missing viewerSessionId");
            return;
        }
        
        ReactiveRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(session, ErrorCode.ROOM_001, "Room not found");
            return;
        }
        
        // Check if viewer is in room
        if (!room.hasViewer(viewerSessionId)) {
            sendError(session, ErrorCode.ROOM_005, "Viewer not in room");
            return;
        }
        
        // Get viewer's sink before removing
        Sinks.Many<WebSocketMessage> viewerSink = room.getViewerSinks().get(viewerSessionId)
            .map(info -> sessionSinks.get(viewerSessionId))
            .orElse(null);
        WebSocketSession viewerSession = room.getViewerSinks().get(viewerSessionId)
            .map(info -> info.session())
            .orElse(null);
        
        // Remove viewer (not ban)
        room.removeViewer(viewerSessionId);
        
        // Clean up session mappings
        sessionToRoom.remove(viewerSessionId);
        sessionRoles.remove(viewerSessionId);
        
        // Notify kicked viewer
        try {
            if (viewerSink != null && viewerSession != null) {
                WebSocketMessage msg = viewerSession.textMessage(
                    "{\"type\":\"kicked\",\"message\":\"You have been removed from this room\"}"
                );
                viewerSink.tryEmitNext(msg);
            }
        } catch (Exception e) {
            logger.debug("Failed to notify kicked viewer: {}", e.getMessage());
        }
        
        // Log audit
        auditService.logViewerKicked(username, sessionId, roomId, viewerSessionId, ipAddress).subscribe();
        
        logger.info("👢 Viewer {} kicked from room {}", viewerSessionId, roomId);
        
        // Send confirmation to presenter
        String response = String.format(
            "{\"type\":\"viewer-kicked\",\"viewerSessionId\":\"%s\",\"viewerCount\":%d}",
            viewerSessionId, room.getViewerCount()
        );
        sendTextToSession(session, response);
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
        String ipAddress = sessionIpAddresses.getOrDefault(sessionId, "unknown");
        AuthenticatedUser user = sessionUsers.get(sessionId);
        String username = user != null ? user.username() : "anonymous";
        
        logger.info("🚪 Session disconnected: {} (user: {})", sessionId, username);
        
        removeSessionFromRoom(sessionId);
        sessionSinks.remove(sessionId);
        
        // Clean up security tracking
        sessionUsers.remove(sessionId);
        sessionIpAddresses.remove(sessionId);
        rateLimitService.removeSession(sessionId);
        
        // Log disconnect
        auditService.logSessionDisconnected(username, sessionId, ipAddress).subscribe();
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
     * Send error message to a session with error code
     */
    private void sendError(WebSocketSession session, ErrorCode errorCode, String errorMessage) {
        String response = String.format(
            "{\"type\":\"error\",\"code\":\"%s\",\"message\":\"%s\"}",
            errorCode.getCode(),
            errorMessage.replace("\"", "\\\"")
        );
        sendTextToSession(session, response);
    }
    
    /**
     * Send error message to a session (legacy method for backwards compatibility)
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        sendError(session, ErrorCode.GENERAL, errorMessage);
    }
    
    /**
     * Send error message and close the WebSocket session.
     * Used for authentication failures where connection should not continue.
     */
    private void sendErrorAndClose(WebSocketSession session, ErrorCode errorCode, String errorMessage) {
        String response = String.format(
            "{\"type\":\"error\",\"code\":\"%s\",\"message\":\"%s\",\"action\":\"close\"}",
            errorCode.getCode(),
            errorMessage.replace("\"", "\\\"")
        );
        Sinks.Many<WebSocketMessage> sink = sessionSinks.get(session.getId());
        if (sink != null) {
            WebSocketMessage message = session.textMessage(response);
            sink.tryEmitNext(message);
            // Complete the sink to trigger session close
            sink.tryEmitComplete();
        }
    }
    
    /**
     * Create welcome message
     */
    private String createWelcomeMessage(String sessionId, AuthenticatedUser user) {
        String username = user != null ? user.username() : "anonymous";
        return String.format(
            "{\"type\":\"connected\",\"sessionId\":\"%s\",\"username\":\"%s\",\"message\":\"Connected to ScreenAI Relay Server (WebFlux+Netty)\",\"role\":\"pending\"}",
            sessionId, username
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
