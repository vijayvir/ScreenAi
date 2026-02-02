package com.screenai.config;

import com.screenai.model.User;
import com.screenai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Initializes default data on application startup.
 * Creates admin user from environment variables if not exists.
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Initialize default admin user on application startup.
     * Reads credentials from environment variables.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() {
        log.info("Checking for default admin user...");
        
        userRepository.existsByUsername(adminUsername)
            .flatMap(exists -> {
                if (exists) {
                    log.info("Admin user '{}' already exists, skipping creation", adminUsername);
                    return Mono.empty();
                }
                
                log.info("Creating default admin user: {}", adminUsername);
                
                User adminUser = new User();
                adminUser.setUsername(adminUsername);
                adminUser.setPasswordHash(passwordEncoder.encode(adminPassword));
                adminUser.setRole("ADMIN");
                adminUser.setEnabled(true);
                
                return userRepository.save(adminUser)
                    .doOnSuccess(user -> log.info("Admin user '{}' created successfully with ID: {}", 
                        adminUsername, user.getId()))
                    .doOnError(error -> log.error("Failed to create admin user: {}", error.getMessage()));
            })
            .subscribe(
                result -> {},
                error -> log.error("Error during admin user initialization: {}", error.getMessage())
            );
    }
}
