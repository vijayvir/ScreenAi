package com.screenai.service;

import com.screenai.dto.ErrorResponse.ErrorCode;
import com.screenai.exception.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for rate limiting requests.
 * Implements sliding window algorithm for per-session and per-IP rate limiting.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    // Rate limit data per session
    private final Map<String, RateLimitBucket> sessionBuckets = new ConcurrentHashMap<>();
    
    // Rate limit data per IP (for room creation)
    private final Map<String, RateLimitBucket> ipRoomCreationBuckets = new ConcurrentHashMap<>();
    
    // Failed auth attempts per IP
    private final Map<String, AtomicInteger> failedAuthAttempts = new ConcurrentHashMap<>();

    private final int messagesPerSecond;
    private final int roomCreationsPerHour;
    private final int failedAuthBeforeBlock;
    private final int ipBlockDurationMinutes;

    private final SecurityAuditService auditService;

    public RateLimitService(
            SecurityAuditService auditService,
            @Value("${security.rate-limit.messages-per-second:100}") int messagesPerSecond,
            @Value("${security.rate-limit.room-creations-per-hour:10}") int roomCreationsPerHour,
            @Value("${security.rate-limit.failed-auth-before-block:5}") int failedAuthBeforeBlock,
            @Value("${security.rate-limit.ip-block-duration-minutes:15}") int ipBlockDurationMinutes) {

        this.auditService = auditService;
        this.messagesPerSecond = messagesPerSecond;
        this.roomCreationsPerHour = roomCreationsPerHour;
        this.failedAuthBeforeBlock = failedAuthBeforeBlock;
        this.ipBlockDurationMinutes = ipBlockDurationMinutes;

        log.info("RateLimitService initialized: {} msg/sec, {} rooms/hour", messagesPerSecond, roomCreationsPerHour);

        // Start cleanup thread
        startCleanupThread();
    }

    /**
     * Check if a message from session is allowed.
     * Throws RateLimitException if rate limit exceeded.
     */
    public void checkMessageRateLimit(String sessionId, String ipAddress) {
        RateLimitBucket bucket = sessionBuckets.computeIfAbsent(sessionId, 
                k -> new RateLimitBucket(messagesPerSecond, 1000)); // 1 second window

        if (!bucket.tryAcquire()) {
            log.warn("Rate limit exceeded for session: {}", sessionId);
            auditService.logRateLimitExceeded(null, sessionId, ipAddress, "message").subscribe();
            throw new RateLimitException(ErrorCode.RATE_001, "Message rate limit exceeded");
        }
    }

    /**
     * Check if room creation from IP is allowed.
     * Throws RateLimitException if rate limit exceeded.
     */
    public void checkRoomCreationRateLimit(String ipAddress, String username) {
        RateLimitBucket bucket = ipRoomCreationBuckets.computeIfAbsent(ipAddress, 
                k -> new RateLimitBucket(roomCreationsPerHour, 3600000)); // 1 hour window

        if (!bucket.tryAcquire()) {
            log.warn("Room creation rate limit exceeded for IP: {}", ipAddress);
            auditService.logRateLimitExceeded(username, null, ipAddress, "room-creation").subscribe();
            throw new RateLimitException(ErrorCode.ROOM_009, "Room creation rate limit exceeded");
        }
    }

    /**
     * Record a failed authentication attempt for an IP.
     * Returns true if IP should be blocked.
     */
    public boolean recordFailedAuth(String ipAddress) {
        AtomicInteger attempts = failedAuthAttempts.computeIfAbsent(ipAddress, k -> new AtomicInteger(0));
        int currentAttempts = attempts.incrementAndGet();
        
        if (currentAttempts >= failedAuthBeforeBlock) {
            log.warn("IP {} has {} failed auth attempts, should be blocked", ipAddress, currentAttempts);
            return true;
        }
        
        return false;
    }

    /**
     * Reset failed auth attempts for an IP (on successful auth).
     */
    public void resetFailedAuth(String ipAddress) {
        failedAuthAttempts.remove(ipAddress);
    }

    /**
     * Get current failed auth count for IP.
     */
    public int getFailedAuthCount(String ipAddress) {
        AtomicInteger attempts = failedAuthAttempts.get(ipAddress);
        return attempts != null ? attempts.get() : 0;
    }

    /**
     * Remove rate limit data for a session (on disconnect).
     */
    public void removeSession(String sessionId) {
        sessionBuckets.remove(sessionId);
    }

    /**
     * Get IP block duration in minutes.
     */
    public int getIpBlockDurationMinutes() {
        return ipBlockDurationMinutes;
    }

    /**
     * Sliding window rate limit bucket.
     */
    private static class RateLimitBucket {
        private final int maxRequests;
        private final long windowMillis;
        private final AtomicLong windowStart = new AtomicLong(0);
        private final AtomicInteger requestCount = new AtomicInteger(0);

        RateLimitBucket(int maxRequests, long windowMillis) {
            this.maxRequests = maxRequests;
            this.windowMillis = windowMillis;
        }

        synchronized boolean tryAcquire() {
            long now = Instant.now().toEpochMilli();
            long currentWindowStart = windowStart.get();

            // Reset window if expired
            if (now - currentWindowStart >= windowMillis) {
                windowStart.set(now);
                requestCount.set(1);
                return true;
            }

            // Check if under limit
            if (requestCount.get() < maxRequests) {
                requestCount.incrementAndGet();
                return true;
            }

            return false;
        }

        int getCurrentCount() {
            return requestCount.get();
        }
    }

    /**
     * Start background thread to clean up old rate limit data.
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // Run every minute
                    cleanupOldData();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "rate-limit-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /**
     * Clean up old rate limit data to prevent memory leaks.
     */
    private void cleanupOldData() {
        long now = Instant.now().toEpochMilli();
        
        // Clean session buckets older than 5 minutes
        sessionBuckets.entrySet().removeIf(entry -> {
            long windowStart = entry.getValue().windowStart.get();
            return now - windowStart > 300000;
        });

        // Clean IP buckets older than 2 hours
        ipRoomCreationBuckets.entrySet().removeIf(entry -> {
            long windowStart = entry.getValue().windowStart.get();
            return now - windowStart > 7200000;
        });

        log.debug("Cleaned up rate limit data. Sessions: {}, IPs: {}", 
                sessionBuckets.size(), ipRoomCreationBuckets.size());
    }

    // Stats for monitoring
    public int getActiveSessionCount() {
        return sessionBuckets.size();
    }

    public int getTrackedIpCount() {
        return ipRoomCreationBuckets.size();
    }
}
