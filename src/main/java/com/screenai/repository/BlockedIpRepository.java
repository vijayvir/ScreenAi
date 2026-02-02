package com.screenai.repository;

import com.screenai.model.BlockedIp;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Reactive repository for BlockedIp entity.
 */
@Repository
public interface BlockedIpRepository extends ReactiveCrudRepository<BlockedIp, Long> {

    /**
     * Find by IP address
     */
    Mono<BlockedIp> findByIpAddress(String ipAddress);

    /**
     * Check if IP is currently blocked
     */
    @Query("SELECT * FROM blocked_ips WHERE ip_address = :ipAddress AND blocked_until > CURRENT_TIMESTAMP")
    Mono<BlockedIp> findActiveBlockByIpAddress(String ipAddress);

    /**
     * Get all currently active blocks
     */
    @Query("SELECT * FROM blocked_ips WHERE blocked_until > CURRENT_TIMESTAMP ORDER BY blocked_at DESC")
    Flux<BlockedIp> findAllActiveBlocks();

    /**
     * Alias for findAllActiveBlocks (used by AdminController)
     */
    @Query("SELECT * FROM blocked_ips WHERE blocked_until > CURRENT_TIMESTAMP ORDER BY blocked_at DESC")
    Flux<BlockedIp> findActiveBlocks();

    /**
     * Delete by IP address (unblock)
     */
    Mono<Void> deleteByIpAddress(String ipAddress);

    /**
     * Delete expired blocks (cleanup)
     */
    @Query("DELETE FROM blocked_ips WHERE blocked_until < :before")
    Mono<Void> deleteExpiredBlocks(LocalDateTime before);

    /**
     * Check if IP exists and is active
     */
    @Query("SELECT COUNT(*) > 0 FROM blocked_ips WHERE ip_address = :ipAddress AND blocked_until > CURRENT_TIMESTAMP")
    Mono<Boolean> isIpBlocked(String ipAddress);
}
