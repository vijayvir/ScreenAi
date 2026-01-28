package com.screenai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Audit event entity for security logging.
 * Records all security-related events for monitoring and compliance.
 */
@Table("audit_events")
public class AuditEvent {

    @Id
    private Long id;

    @Column("event_type")
    private String eventType;

    @Column("username")
    private String username;

    @Column("session_id")
    private String sessionId;

    @Column("room_id")
    private String roomId;

    @Column("ip_address")
    private String ipAddress;

    @Column("details")
    private String details;

    @Column("severity")
    private String severity = "INFO";

    @Column("created_at")
    private LocalDateTime createdAt;

    // Default constructor
    public AuditEvent() {
        this.createdAt = LocalDateTime.now();
    }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AuditEvent event = new AuditEvent();

        public Builder eventType(EventType type) {
            event.eventType = type.name();
            return this;
        }

        public Builder eventType(String type) {
            event.eventType = type;
            return this;
        }

        public Builder username(String username) {
            event.username = username;
            return this;
        }

        public Builder sessionId(String sessionId) {
            event.sessionId = sessionId;
            return this;
        }

        public Builder roomId(String roomId) {
            event.roomId = roomId;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            event.ipAddress = ipAddress;
            return this;
        }

        public Builder details(String details) {
            event.details = details;
            return this;
        }

        public Builder severity(Severity severity) {
            event.severity = severity.name();
            return this;
        }

        public AuditEvent build() {
            return event;
        }
    }

    // Event types enum
    public enum EventType {
        // Authentication events
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        TOKEN_REFRESH,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        
        // Registration events
        REGISTRATION_SUCCESS,
        REGISTRATION_FAILURE,
        
        // Room events
        ROOM_CREATED,
        ROOM_JOINED,
        ROOM_LEFT,
        ROOM_DELETED,
        ROOM_ACCESS_DENIED,
        
        // Viewer management events
        VIEWER_APPROVED,
        VIEWER_DENIED,
        VIEWER_BANNED,
        VIEWER_KICKED,
        
        // Session events
        SESSION_CONNECTED,
        SESSION_DISCONNECTED,
        CONNECTION_BLOCKED,
        
        // Security events
        RATE_LIMIT_EXCEEDED,
        IP_BLOCKED,
        IP_UNBLOCKED,
        INVALID_TOKEN,
        SUSPICIOUS_ACTIVITY,
        
        // Admin events
        ADMIN_ACTION,
        CONFIG_CHANGED
    }

    // Severity enum
    public enum Severity {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        CRITICAL
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "eventType='" + eventType + '\'' +
                ", username='" + username + '\'' +
                ", roomId='" + roomId + '\'' +
                ", severity='" + severity + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
