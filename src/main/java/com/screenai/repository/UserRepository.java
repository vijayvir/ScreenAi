package com.screenai.repository;

import com.screenai.model.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for User entity.
 */
@Repository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    /**
     * Find user by username (case-insensitive)
     */
    Mono<User> findByUsername(String username);

    /**
     * Check if username exists
     */
    Mono<Boolean> existsByUsername(String username);

    /**
     * Find user by refresh token
     */
    Mono<User> findByRefreshToken(String refreshToken);

    /**
     * Update failed login attempts
     */
    @Query("UPDATE users SET failed_login_attempts = :attempts, updated_at = CURRENT_TIMESTAMP WHERE id = :userId")
    Mono<Void> updateFailedAttempts(Long userId, Integer attempts);

    /**
     * Lock user account
     */
    @Query("UPDATE users SET locked_until = :lockedUntil, updated_at = CURRENT_TIMESTAMP WHERE id = :userId")
    Mono<Void> lockAccount(Long userId, java.time.LocalDateTime lockedUntil);

    /**
     * Unlock user account and reset failed attempts
     */
    @Query("UPDATE users SET locked_until = NULL, failed_login_attempts = 0, updated_at = CURRENT_TIMESTAMP WHERE id = :userId")
    Mono<Void> unlockAccount(Long userId);

    /**
     * Update refresh token
     */
    @Query("UPDATE users SET refresh_token = :token, refresh_token_expires_at = :expiresAt, updated_at = CURRENT_TIMESTAMP WHERE id = :userId")
    Mono<Void> updateRefreshToken(Long userId, String token, java.time.LocalDateTime expiresAt);

    /**
     * Invalidate refresh token (logout)
     */
    @Query("UPDATE users SET refresh_token = NULL, refresh_token_expires_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = :userId")
    Mono<Void> invalidateRefreshToken(Long userId);
}
