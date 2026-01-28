package com.screenai.controller;

import com.screenai.dto.ErrorResponse;
import com.screenai.dto.ErrorResponse.ErrorCode;
import com.screenai.model.AuditEvent;
import com.screenai.model.BlockedIp;
import com.screenai.repository.BlockedIpRepository;
import com.screenai.service.ConnectionThrottleService;
import com.screenai.service.SecurityAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin REST controller for security management.
 * Provides endpoints for audit logs, IP blocking, and system statistics.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final SecurityAuditService auditService;
    private final ConnectionThrottleService throttleService;
    private final BlockedIpRepository blockedIpRepository;

    public AdminController(SecurityAuditService auditService,
                           ConnectionThrottleService throttleService,
                           BlockedIpRepository blockedIpRepository) {
        this.auditService = auditService;
        this.throttleService = throttleService;
        this.blockedIpRepository = blockedIpRepository;
    }

    // ==================== Audit Logs ====================

    /**
     * Get recent audit events with pagination.
     */
    @GetMapping("/logs")
    public Flux<AuditEvent> getAuditLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        logger.info("Admin: Retrieving audit logs (limit={}, offset={})", limit, offset);
        return auditService.getRecentEvents(Math.min(limit, 1000), offset);
    }

    /**
     * Get audit events by username.
     */
    @GetMapping("/logs/user/{username}")
    public Flux<AuditEvent> getLogsByUser(@PathVariable String username) {
        logger.info("Admin: Retrieving audit logs for user: {}", username);
        return auditService.getEventsByUsername(username);
    }

    /**
     * Get audit events by event type.
     */
    @GetMapping("/logs/type/{eventType}")
    public Flux<AuditEvent> getLogsByType(@PathVariable String eventType) {
        logger.info("Admin: Retrieving audit logs for type: {}", eventType);
        return auditService.getEventsByType(eventType);
    }

    /**
     * Get audit events by severity.
     */
    @GetMapping("/logs/severity/{severity}")
    public Flux<AuditEvent> getLogsBySeverity(@PathVariable String severity) {
        logger.info("Admin: Retrieving audit logs for severity: {}", severity);
        return auditService.getEventsBySeverity(severity);
    }

    /**
     * Get audit events after a specific timestamp.
     */
    @GetMapping("/logs/since")
    public Flux<AuditEvent> getLogsSince(@RequestParam String after) {
        LocalDateTime timestamp = LocalDateTime.parse(after);
        logger.info("Admin: Retrieving audit logs since: {}", timestamp);
        return auditService.getEventsAfter(timestamp);
    }

    // ==================== IP Blocking ====================

    /**
     * Get all blocked IPs.
     */
    @GetMapping("/blocked-ips")
    public Flux<BlockedIp> getBlockedIps() {
        logger.info("Admin: Retrieving blocked IPs");
        return blockedIpRepository.findActiveBlocks();
    }

    /**
     * Block an IP address.
     */
    @PostMapping("/blocked-ips")
    public Mono<ResponseEntity<Map<String, Object>>> blockIp(@RequestBody BlockIpRequest request) {
        logger.info("Admin: Blocking IP: {} for {} minutes, reason: {}", 
                request.ipAddress(), request.durationMinutes(), request.reason());

        return throttleService.blockIp(request.ipAddress(), request.durationMinutes(), request.reason())
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("ipAddress", request.ipAddress());
                    response.put("blockedUntil", LocalDateTime.now().plusMinutes(request.durationMinutes()));
                    return ResponseEntity.ok(response);
                }));
    }

    /**
     * Unblock an IP address.
     */
    @DeleteMapping("/blocked-ips/{ipAddress}")
    public Mono<ResponseEntity<Map<String, Object>>> unblockIp(@PathVariable String ipAddress) {
        logger.info("Admin: Unblocking IP: {}", ipAddress);

        return throttleService.unblockIp(ipAddress)
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("ipAddress", ipAddress);
                    response.put("message", "IP unblocked successfully");
                    return ResponseEntity.ok(response);
                }));
    }

    /**
     * Check if an IP is blocked.
     */
    @GetMapping("/blocked-ips/{ipAddress}/status")
    public Mono<ResponseEntity<Map<String, Object>>> checkIpStatus(@PathVariable String ipAddress) {
        return throttleService.isIpBlocked(ipAddress)
                .map(blocked -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("ipAddress", ipAddress);
                    response.put("blocked", blocked);
                    return ResponseEntity.ok(response);
                });
    }

    // ==================== Statistics ====================

    /**
     * Get security statistics.
     */
    @GetMapping("/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getStats() {
        logger.info("Admin: Retrieving security statistics");

        return Mono.zip(
                blockedIpRepository.findActiveBlocks().count(),
                auditService.getRecentEvents(1000, 0).count()
        ).map(tuple -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeBlockedIps", tuple.getT1());
            stats.put("recentAuditEvents", tuple.getT2());
            stats.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(stats);
        });
    }

    /**
     * Get failed login count for an IP in the last hour.
     */
    @GetMapping("/stats/failed-logins/{ipAddress}")
    public Mono<ResponseEntity<Map<String, Object>>> getFailedLogins(@PathVariable String ipAddress) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        return auditService.countFailedLoginsByIpSince(ipAddress, oneHourAgo)
                .map(count -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("ipAddress", ipAddress);
                    response.put("failedLogins", count);
                    response.put("since", oneHourAgo);
                    return ResponseEntity.ok(response);
                });
    }

    // ==================== Request DTOs ====================

    public record BlockIpRequest(
            String ipAddress,
            int durationMinutes,
            String reason
    ) {}
}
