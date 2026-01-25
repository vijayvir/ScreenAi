package com.screenai.service;

import com.screenai.model.AuditEvent;
import com.screenai.model.AuditEvent.EventType;
import com.screenai.model.AuditEvent.Severity;
import com.screenai.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Service for logging security-related events.
 * All events are persisted to database for audit trail and monitoring.
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final AuditEventRepository auditEventRepository;

    public SecurityAuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    // ==================== Authentication Events ====================

    public Mono<Void> logLoginSuccess(String username, String ipAddress) {
        return logEvent(EventType.LOGIN_SUCCESS, username, null, null, ipAddress, 
                "User logged in successfully", Severity.INFO);
    }

    public Mono<Void> logLoginFailure(String username, String ipAddress, String reason) {
        return logEvent(EventType.LOGIN_FAILURE, username, null, null, ipAddress, 
                reason, Severity.WARN);
    }

    public Mono<Void> logLogout(String username, String ipAddress) {
        return logEvent(EventType.LOGOUT, username, null, null, ipAddress, 
                "User logged out", Severity.INFO);
    }

    public Mono<Void> logTokenRefresh(String username, String ipAddress, boolean success, String reason) {
        return logEvent(EventType.TOKEN_REFRESH, username, null, null, ipAddress, 
                success ? "Token refreshed successfully" : "Token refresh failed: " + reason,
                success ? Severity.INFO : Severity.WARN);
    }

    public Mono<Void> logAccountLocked(String username, String ipAddress, int durationMinutes) {
        return logEvent(EventType.ACCOUNT_LOCKED, username, null, null, ipAddress, 
                "Account locked for " + durationMinutes + " minutes due to failed login attempts", 
                Severity.WARN);
    }

    public Mono<Void> logAccountUnlocked(String username, String adminUsername) {
        return logEvent(EventType.ACCOUNT_UNLOCKED, username, null, null, null, 
                "Account unlocked by admin: " + adminUsername, Severity.INFO);
    }

    // ==================== Registration Events ====================

    public Mono<Void> logRegistrationSuccess(String username, String ipAddress) {
        return logEvent(EventType.REGISTRATION_SUCCESS, username, null, null, ipAddress, 
                "User registered successfully", Severity.INFO);
    }

    public Mono<Void> logRegistrationFailure(String username, String ipAddress, String reason) {
        return logEvent(EventType.REGISTRATION_FAILURE, username, null, null, ipAddress, 
                reason, Severity.WARN);
    }

    // ==================== Room Events ====================

    public Mono<Void> logRoomCreated(String username, String sessionId, String roomId, String ipAddress, boolean hasPassword) {
        return logEvent(EventType.ROOM_CREATED, username, sessionId, roomId, ipAddress, 
                "Room created" + (hasPassword ? " (password protected)" : ""), Severity.INFO);
    }

    public Mono<Void> logRoomJoined(String username, String sessionId, String roomId, String ipAddress) {
        return logEvent(EventType.ROOM_JOINED, username, sessionId, roomId, ipAddress, 
                "User joined room", Severity.INFO);
    }

    public Mono<Void> logRoomLeft(String username, String sessionId, String roomId, String ipAddress) {
        return logEvent(EventType.ROOM_LEFT, username, sessionId, roomId, ipAddress, 
                "User left room", Severity.INFO);
    }

    public Mono<Void> logRoomDeleted(String username, String roomId, String ipAddress, String reason) {
        return logEvent(EventType.ROOM_DELETED, username, null, roomId, ipAddress, 
                "Room deleted: " + reason, Severity.INFO);
    }

    public Mono<Void> logRoomAccessDenied(String username, String sessionId, String roomId, String ipAddress, String reason) {
        return logEvent(EventType.ROOM_ACCESS_DENIED, username, sessionId, roomId, ipAddress, 
                "Room access denied: " + reason, Severity.WARN);
    }

    // ==================== Viewer Management Events ====================

    public Mono<Void> logViewerApproved(String hostUsername, String hostSessionId, String roomId, String viewerSessionId, String ipAddress) {
        return logEvent(EventType.VIEWER_APPROVED, hostUsername, hostSessionId, roomId, ipAddress, 
                "Viewer approved: " + viewerSessionId, Severity.INFO);
    }

    public Mono<Void> logViewerDenied(String hostUsername, String hostSessionId, String roomId, String viewerSessionId, String ipAddress) {
        return logEvent(EventType.VIEWER_DENIED, hostUsername, hostSessionId, roomId, ipAddress, 
                "Viewer denied: " + viewerSessionId, Severity.INFO);
    }

    public Mono<Void> logViewerBanned(String hostUsername, String hostSessionId, String roomId, String viewerSessionId, String ipAddress) {
        return logEvent(EventType.VIEWER_BANNED, hostUsername, hostSessionId, roomId, ipAddress, 
                "Viewer banned: " + viewerSessionId, Severity.WARN);
    }

    public Mono<Void> logViewerKicked(String hostUsername, String hostSessionId, String roomId, String viewerSessionId, String ipAddress) {
        return logEvent(EventType.VIEWER_KICKED, hostUsername, hostSessionId, roomId, ipAddress, 
                "Viewer kicked: " + viewerSessionId, Severity.INFO);
    }
    
    // ==================== Session Events ====================

    public Mono<Void> logSessionConnected(String username, String sessionId, String ipAddress) {
        return logEvent(EventType.SESSION_CONNECTED, username, sessionId, null, ipAddress, 
                "Session connected", Severity.INFO);
    }

    public Mono<Void> logSessionDisconnected(String username, String sessionId, String ipAddress) {
        return logEvent(EventType.SESSION_DISCONNECTED, username, sessionId, null, ipAddress, 
                "Session disconnected", Severity.INFO);
    }

    public Mono<Void> logConnectionBlocked(String ipAddress, String reason) {
        return logEvent(EventType.CONNECTION_BLOCKED, null, null, null, ipAddress, 
                "Connection blocked: " + reason, Severity.WARN);
    }

    // ==================== Security Events ====================

    public Mono<Void> logRateLimitExceeded(String username, String sessionId, String ipAddress, String action) {
        return logEvent(EventType.RATE_LIMIT_EXCEEDED, username, sessionId, null, ipAddress, 
                "Rate limit exceeded for action: " + action, Severity.WARN);
    }

    public Mono<Void> logIpBlocked(String ipAddress, String reason, int durationMinutes) {
        return logEvent(EventType.IP_BLOCKED, null, null, null, ipAddress, 
                "IP blocked for " + durationMinutes + " minutes: " + reason, Severity.WARN);
    }

    public Mono<Void> logIpUnblocked(String ipAddress, String adminUsername) {
        return logEvent(EventType.IP_UNBLOCKED, null, null, null, ipAddress, 
                "IP unblocked by admin: " + adminUsername, Severity.INFO);
    }

    public Mono<Void> logInvalidToken(String sessionId, String ipAddress, String reason) {
        return logEvent(EventType.INVALID_TOKEN, null, sessionId, null, ipAddress, 
                "Invalid token: " + reason, Severity.WARN);
    }

    public Mono<Void> logSuspiciousActivity(String username, String sessionId, String ipAddress, String details) {
        return logEvent(EventType.SUSPICIOUS_ACTIVITY, username, sessionId, null, ipAddress, 
                details, Severity.ERROR);
    }

    // ==================== Admin Events ====================

    public Mono<Void> logAdminAction(String adminUsername, String ipAddress, String action) {
        return logEvent(EventType.ADMIN_ACTION, adminUsername, null, null, ipAddress, 
                action, Severity.INFO);
    }

    // ==================== Core Logging Method ====================

    private Mono<Void> logEvent(EventType eventType, String username, String sessionId, 
            String roomId, String ipAddress, String details, Severity severity) {
        
        // Also log to application log
        String logMessage = String.format("[%s] user=%s, session=%s, room=%s, ip=%s - %s",
                eventType, username, sessionId, roomId, ipAddress, details);
        
        switch (severity) {
            case ERROR, CRITICAL -> log.error(logMessage);
            case WARN -> log.warn(logMessage);
            default -> log.info(logMessage);
        }

        AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .username(maskSensitiveData(username))
                .sessionId(hashSessionId(sessionId))
                .roomId(roomId)
                .ipAddress(ipAddress)
                .details(details)
                .severity(severity)
                .build();

        return auditEventRepository.save(event).then();
    }

    // ==================== Query Methods ====================

    public Flux<AuditEvent> getRecentEvents(int limit, int offset) {
        return auditEventRepository.findRecentEvents(limit, offset);
    }

    public Flux<AuditEvent> getEventsByUsername(String username) {
        return auditEventRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public Flux<AuditEvent> getEventsByType(String eventType) {
        return auditEventRepository.findByEventTypeOrderByCreatedAtDesc(eventType);
    }

    public Flux<AuditEvent> getEventsBySeverity(String severity) {
        return auditEventRepository.findBySeverityOrderByCreatedAtDesc(severity);
    }

    public Flux<AuditEvent> getEventsAfter(LocalDateTime after) {
        return auditEventRepository.findByCreatedAtAfterOrderByCreatedAtDesc(after);
    }

    public Mono<Long> countFailedLoginsByIpSince(String ipAddress, LocalDateTime since) {
        return auditEventRepository.countFailedLoginsByIpSince(ipAddress, since);
    }

    // ==================== Utility Methods ====================

    /**
     * Mask username for privacy in logs (show first 2 and last 2 chars).
     */
    private String maskSensitiveData(String data) {
        if (data == null || data.length() <= 4) {
            return data;
        }
        return data.substring(0, 2) + "***" + data.substring(data.length() - 2);
    }

    /**
     * Hash session ID for privacy in logs.
     */
    private String hashSessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        // Store first 8 chars only for correlation
        return sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId;
    }
}
