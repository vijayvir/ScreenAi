package com.screenai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import com.screenai.service.NetworkQualityService;

import jakarta.annotation.PreDestroy;

/**
 * Configuration for Network Quality Detection Service
 * 
 * Handles initialization and shutdown of the NetworkQualityService
 */
@Configuration
public class NetworkQualityConfig {

    @Autowired
    private NetworkQualityService networkQualityService;

    /**
     * Initialize the network quality service when the application context is ready
     */
    @EventListener(ContextRefreshedEvent.class)
    public void initializeNetworkQualityService() {
        networkQualityService.initialize();
    }

    /**
     * Shutdown the network quality service when the application is stopping
     */
    @PreDestroy
    public void shutdownNetworkQualityService() {
        if (networkQualityService != null) {
            networkQualityService.shutdown();
        }
    }
}