package com.screenai.service;

import com.screenai.model.BlockedIp;
import com.screenai.repository.BlockedIpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing blocked IP addresses.
 * Combines in-memory cache with database persistence.
 */
@Service
public class ConnectionThrottleService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionThrottleService.class);

    // In-memory cache for fast lookups
    private final Map<String, LocalDateTime> blockedIpCache = new ConcurrentHashMap<>();

    private final BlockedIpRepository blockedIpRepository;
    private final SecurityAuditService auditService;
    private final int defaultBlockDurationMinutes;

    public ConnectionThrottleService(
            BlockedIpRepository blockedIpRepository,
            SecurityAuditService auditService,
            @Value("${security.rate-limit.ip-block-duration-minutes:15}") int defaultBlockDurationMinutes) {

        this.blockedIpRepository = blockedIpRepository;
        this.auditService = auditService;
        this.defaultBlockDurationMinutes = defaultBlockDurationMinutes;

        log.info("ConnectionThrottleService initialized with default block duration: {} minutes", 
                defaultBlockDurationMinutes);
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady() {
        // Load existing blocks from database after context is ready
        loadBlockedIpsFromDatabase();
    }

    /**
     * Check if an IP address is currently blocked.
     */
    public Mono<Boolean> isBlocked(String ipAddress) {
        // Check cache first
        LocalDateTime blockedUntil = blockedIpCache.get(ipAddress);
        if (blockedUntil != null) {
            if (LocalDateTime.now().isBefore(blockedUntil)) {
                return Mono.just(true);
            } else {
                // Block expired, remove from cache
                blockedIpCache.remove(ipAddress);
            }
        }

        // Check database as fallback
        return blockedIpRepository.isIpBlocked(ipAddress)
                .doOnNext(isBlocked -> {
                    if (isBlocked) {
                        // Update cache
                        blockedIpRepository.findActiveBlockByIpAddress(ipAddress)
                                .subscribe(block -> blockedIpCache.put(ipAddress, block.getBlockedUntil()));
                    }
                });
    }

    /**
     * Check if IP is blocked (synchronous, cache-only).
     */
    public boolean isBlockedSync(String ipAddress) {
        LocalDateTime blockedUntil = blockedIpCache.get(ipAddress);
        if (blockedUntil != null && LocalDateTime.now().isBefore(blockedUntil)) {
            return true;
        }
        if (blockedUntil != null) {
            blockedIpCache.remove(ipAddress);
        }
        return false;
    }

    /**
     * Block an IP address for the default duration.
     */
    public Mono<Void> blockIp(String ipAddress, String reason) {
        return blockIp(ipAddress, reason, defaultBlockDurationMinutes);
    }

    /**
     * Block an IP address for a specified duration.
     */
    public Mono<Void> blockIp(String ipAddress, String reason, int durationMinutes) {
        LocalDateTime blockedUntil = LocalDateTime.now().plusMinutes(durationMinutes);

        BlockedIp block = new BlockedIp();
        block.setIpAddress(ipAddress);
        block.setReason(reason);
        block.setBlockedUntil(blockedUntil);

        // Update cache
        blockedIpCache.put(ipAddress, blockedUntil);

        log.warn("Blocking IP {} for {} minutes: {}", ipAddress, durationMinutes, reason);

        return blockedIpRepository.findByIpAddress(ipAddress)
                .flatMap(existing -> {
                    existing.setBlockedUntil(blockedUntil);
                    existing.setReason(reason);
                    existing.setBlockedAt(LocalDateTime.now());
                    return blockedIpRepository.save(existing);
                })
                .switchIfEmpty(blockedIpRepository.save(block))
                .then(auditService.logIpBlocked(ipAddress, reason, durationMinutes));
    }

    /**
     * Unblock an IP address.
     */
    public Mono<Void> unblockIp(String ipAddress, String adminUsername) {
        blockedIpCache.remove(ipAddress);
        log.info("Unblocking IP {} by admin {}", ipAddress, adminUsername);

        return blockedIpRepository.deleteByIpAddress(ipAddress)
                .then(auditService.logIpUnblocked(ipAddress, adminUsername));
    }

    /**
     * Unblock an IP address (admin action from controller).
     */
    public Mono<Void> unblockIp(String ipAddress) {
        return unblockIp(ipAddress, "admin");
    }

    /**
     * Block IP with duration specified before reason (overload for admin controller).
     */
    public Mono<Void> blockIp(String ipAddress, int durationMinutes, String reason) {
        return blockIp(ipAddress, reason, durationMinutes);
    }

    /**
     * Check if IP is blocked (reactive, for admin controller).
     */
    public Mono<Boolean> isIpBlocked(String ipAddress) {
        return isBlocked(ipAddress);
    }

    /**
     * Get all currently blocked IPs.
     */
    public Flux<BlockedIp> getBlockedIps() {
        return blockedIpRepository.findAllActiveBlocks();
    }

    /**
     * Clean up expired blocks.
     */
    public Mono<Void> cleanupExpiredBlocks() {
        LocalDateTime now = LocalDateTime.now();

        // Clean cache
        blockedIpCache.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));

        // Clean database
        return blockedIpRepository.deleteExpiredBlocks(now);
    }

    /**
     * Load blocked IPs from database into cache on startup.
     */
    private void loadBlockedIpsFromDatabase() {
        blockedIpRepository.findAllActiveBlocks()
                .doOnNext(block -> blockedIpCache.put(block.getIpAddress(), block.getBlockedUntil()))
                .doOnComplete(() -> log.info("Loaded {} blocked IPs from database", blockedIpCache.size()))
                .doOnError(e -> log.warn("Could not load blocked IPs from database (table may not exist yet): {}", e.getMessage()))
                .onErrorResume(e -> Flux.empty())
                .subscribe();
    }

    /**
     * Get count of currently blocked IPs.
     */
    public int getBlockedIpCount() {
        return blockedIpCache.size();
    }

    /**
     * Get remaining block time for an IP (in minutes).
     */
    public long getRemainingBlockTime(String ipAddress) {
        LocalDateTime blockedUntil = blockedIpCache.get(ipAddress);
        if (blockedUntil == null || LocalDateTime.now().isAfter(blockedUntil)) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), blockedUntil).toMinutes();
    }
}
