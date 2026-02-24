package com.screenai.service;

import com.screenai.model.User;
import com.screenai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Bootstrap service to create default admin user on application startup.
 * This ensures the admin user exists even if data.sql fails to execute.
 */
@Service
public class AdminBootstrapService {
    
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    // Default admin credentials (should be changed after first login in production)
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";
    
    public AdminBootstrapService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }
    
    @EventListener(ApplicationReadyEvent.class)
    @Order(100)  // Run after other initialization
    public void createDefaultAdminIfNotExists() {
        log.info("Checking for default admin user...");
        
        userRepository.findByUsername(DEFAULT_ADMIN_USERNAME)
            .flatMap(existingUser -> {
                // Update existing admin password to ensure it matches (dev mode fix)
                log.info("Updating admin user password...");
                existingUser.setPasswordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
                existingUser.setRole("ADMIN");
                existingUser.setEnabled(true);
                existingUser.resetFailedAttempts(); // Unlock if locked
                return userRepository.save(existingUser)
                    .doOnSuccess(user -> log.info("Admin user password reset successfully"));
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Creating default admin user...");
                User admin = new User();
                admin.setUsername(DEFAULT_ADMIN_USERNAME);
                admin.setPasswordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
                admin.setRole("ADMIN");
                admin.setEnabled(true);
                
                return userRepository.save(admin)
                    .doOnSuccess(user -> log.info("Default admin user created with ID: {}", user.getId()));
            }))
            .subscribe(
                result -> {},
                error -> log.error("Admin bootstrap error: {}", error.getMessage())
            );
    }
}
