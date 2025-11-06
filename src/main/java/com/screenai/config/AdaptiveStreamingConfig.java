package com.screenai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import com.screenai.service.AdaptiveStreamingService;

import jakarta.annotation.PreDestroy;

/**
 * Configuration for Adaptive Streaming Service
 * 
 * Handles initialization and shutdown of the AdaptiveStreamingService
 */
@Configuration
public class AdaptiveStreamingConfig {

    @Autowired
    private AdaptiveStreamingService adaptiveStreamingService;

    /**
     * Initialize the adaptive streaming service when the application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeAdaptiveStreamingService() {
        adaptiveStreamingService.initialize();
    }

    /**
     * Shutdown the adaptive streaming service when the application is stopping
     */
    @PreDestroy
    public void shutdownAdaptiveStreamingService() {
        if (adaptiveStreamingService != null) {
            adaptiveStreamingService.shutdown();
        }
    }
}