package com.screenai.repository;

import com.screenai.model.AuditEvent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * Reactive repository for AuditEvent entity.
 */
@Repository
public interface AuditEventRepository extends ReactiveCrudRepository<AuditEvent, Long> {

    /**
     * Find events by type
     */
    Flux<AuditEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);

    /**
     * Find events by username
     */
    Flux<AuditEvent> findByUsernameOrderByCreatedAtDesc(String username);

    /**
     * Find events by room ID
     */
    Flux<AuditEvent> findByRoomIdOrderByCreatedAtDesc(String roomId);

    /**
     * Find events by IP address
     */
    Flux<AuditEvent> findByIpAddressOrderByCreatedAtDesc(String ipAddress);

    /**
     * Find events by severity
     */
    Flux<AuditEvent> findBySeverityOrderByCreatedAtDesc(String severity);

    /**
     * Find recent events (paginated)
     */
    @Query("SELECT * FROM audit_events ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<AuditEvent> findRecentEvents(int limit, int offset);

    /**
     * Find events after a certain time
     */
    Flux<AuditEvent> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);

    /**
     * Find events by type and time range
     */
    @Query("SELECT * FROM audit_events WHERE event_type = :eventType AND created_at >= :start AND created_at <= :end ORDER BY created_at DESC")
    Flux<AuditEvent> findByEventTypeAndTimeRange(String eventType, LocalDateTime start, LocalDateTime end);

    /**
     * Count failed login attempts from IP in time window
     */
    @Query("SELECT COUNT(*) FROM audit_events WHERE event_type = 'LOGIN_FAILURE' AND ip_address = :ipAddress AND created_at >= :since")
    reactor.core.publisher.Mono<Long> countFailedLoginsByIpSince(String ipAddress, LocalDateTime since);

    /**
     * Count rate limit events from session in time window
     */
    @Query("SELECT COUNT(*) FROM audit_events WHERE event_type = 'RATE_LIMIT_EXCEEDED' AND session_id = :sessionId AND created_at >= :since")
    reactor.core.publisher.Mono<Long> countRateLimitEventsForSessionSince(String sessionId, LocalDateTime since);

    /**
     * Delete events older than specified date (cleanup)
     */
    @Query("DELETE FROM audit_events WHERE created_at < :before")
    reactor.core.publisher.Mono<Void> deleteEventsOlderThan(LocalDateTime before);
}
