package com.screenai.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.screenai.config.AdaptiveStreamingProperties;
import com.screenai.model.StreamingParameters;
import com.screenai.service.NetworkQualityService.NetworkQuality;

import jakarta.annotation.PreDestroy;

/**
 * Adaptive streaming service that automatically adjusts video quality
 * based on real-time network quality assessment
 * 
 * Features:
 * - Periodic network quality assessment
 * - Smooth parameter transitions (avoids jarring quality jumps)
 * - Configurable bitrate, framerate, and resolution limits
 * - Automatic quality adaptation based on latency and jitter
 */
@Service
public class AdaptiveStreamingService {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveStreamingService.class);

    @Autowired
    private NetworkQualityService networkQualityService;
    
    @Autowired
    private ScreenCaptureService screenCaptureService;
    
    @Autowired
    private AdaptiveStreamingProperties config;
    
    private ScheduledExecutorService assessmentScheduler;
    private volatile StreamingParameters currentParams;
    private volatile NetworkQuality lastQuality;
    private volatile int stabilityCounter = 0;
    private volatile boolean isRunning = false;
    
    // Require 3 consecutive quality assessments before adapting (30 seconds at 10s interval)
    private static final int STABILITY_THRESHOLD = 3;
    
    /**
     * Initialize and start the adaptive streaming service
     */
    public void initialize() {
        if (!config.isEnabled()) {
            logger.info("⚠️ Adaptive streaming is disabled in configuration");
            return;
        }
        
        if (isRunning) {
            logger.warn("Adaptive streaming service already running");
            return;
        }
        
        // Initialize with default parameters (GOOD quality baseline)
        currentParams = StreamingParameters.forQuality(NetworkQuality.GOOD);
        lastQuality = NetworkQuality.GOOD;
        
        logger.info("🎯 Adaptive Streaming Service initialized with baseline: {}", currentParams);
        logger.info("📊 Assessment interval: {}ms, Stability threshold: {} cycles", 
                   config.getAssessmentInterval(), STABILITY_THRESHOLD);
        
        startAssessment();
    }
    
    /**
     * Start periodic network quality assessment and adaptation
     */
    private void startAssessment() {
        if (assessmentScheduler != null && !assessmentScheduler.isShutdown()) {
            return;
        }
        
        assessmentScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "AdaptiveStreaming-Assessor");
            thread.setDaemon(true);
            return thread;
        });
        
        long interval = config.getAssessmentInterval();
        
        assessmentScheduler.scheduleAtFixedRate(() -> {
            try {
                assessAndAdapt();
            } catch (Exception e) {
                logger.error("Error during adaptive assessment: {}", e.getMessage(), e);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        
        isRunning = true;
        logger.info("✅ Adaptive streaming assessment started (every {}s)", interval / 1000);
    }
    
    /**
     * Core adaptation logic:
     * 1. Assess current network quality
     * 2. Check for stability (same quality for STABILITY_THRESHOLD cycles)
     * 3. Calculate optimal parameters
     * 4. Apply changes if quality is stable and parameters changed
     */
    private void assessAndAdapt() {
        // Only adapt if capture is active
        if (!screenCaptureService.isCapturing()) {
            logger.debug("📊 Skipping adaptation - capture not active");
            return;
        }
        
        // Get overall network quality summary
        NetworkQualityService.NetworkQualitySummary summary = networkQualityService.getOverallSummary();
        NetworkQuality newQuality = summary.getOverallQuality();
        
        double avgLatency = summary.getAverageLatency();
        double avgJitter = summary.getAverageJitter();
        
        logger.debug("📊 Network Assessment: Quality={}, Latency={:.1f}ms, Jitter={:.1f}ms", 
                    newQuality, avgLatency, avgJitter);
        
        // Check quality stability
        if (newQuality == lastQuality) {
            stabilityCounter++;
            logger.debug("🔄 Quality stable: {} (counter: {}/{})", 
                        newQuality, stabilityCounter, STABILITY_THRESHOLD);
        } else {
            logger.info("🔄 Quality changed: {} → {} (resetting stability counter)", 
                       lastQuality, newQuality);
            stabilityCounter = 0;
            lastQuality = newQuality;
        }
        
        // Only adapt if quality has been stable for enough cycles
        if (stabilityCounter >= STABILITY_THRESHOLD) {
            adaptToQuality(newQuality);
            // Reset counter after adaptation
            stabilityCounter = 0;
        } else if (stabilityCounter > 0) {
            logger.debug("⏳ Waiting for quality stability ({}/{} cycles)", 
                        stabilityCounter, STABILITY_THRESHOLD);
        }
    }
    
    /**
     * Adapt streaming parameters to the assessed network quality
     */
    private void adaptToQuality(NetworkQuality quality) {
        // Calculate optimal parameters with configured limits
        StreamingParameters newParams = StreamingParameters.withLimits(
            quality,
            config.getBitrate().getMin(),
            config.getBitrate().getMax(),
            config.getFramerate().getMin(),
            config.getFramerate().getMax()
        );
        
        // Only update if parameters actually changed
        if (!newParams.equals(currentParams)) {
            logger.info("🎚️ Adapting to {} quality: {} → {}", 
                       quality, currentParams, newParams);
            
            // Apply the new parameters
            boolean success = screenCaptureService.updateStreamingParameters(newParams);
            
            if (success) {
                currentParams = newParams;
                logger.info("✅ Streaming parameters updated successfully");
            } else {
                logger.warn("⚠️ Failed to update streaming parameters");
            }
        } else {
            logger.debug("✅ Parameters already optimal for {} quality: {}", quality, currentParams);
        }
    }
    
    /**
     * Get current streaming parameters
     */
    public StreamingParameters getCurrentParameters() {
        return currentParams;
    }
    
    /**
     * Manually trigger quality assessment (for testing/debugging)
     */
    public void triggerAssessment() {
        if (isRunning) {
            assessAndAdapt();
        } else {
            logger.warn("Cannot trigger assessment - service not running");
        }
    }
    
    /**
     * Shutdown the adaptive streaming service
     */
    @PreDestroy
    public void shutdown() {
        if (assessmentScheduler != null && !assessmentScheduler.isShutdown()) {
            logger.info("🛑 Shutting down adaptive streaming service...");
            assessmentScheduler.shutdown();
            try {
                if (!assessmentScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    assessmentScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                assessmentScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            isRunning = false;
            logger.info("✅ Adaptive streaming service stopped");
        }
    }
    
    /**
     * Check if adaptive streaming is running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Get basic streaming recommendations (for backward compatibility)
     */
    public String getRecommendations() {
        if (!isRunning) {
            return "Adaptive streaming is not active";
        }
        return String.format("Current quality: %s | Parameters: %s", lastQuality, currentParams);
    }
}
