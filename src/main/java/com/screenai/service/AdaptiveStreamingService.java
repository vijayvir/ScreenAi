package com.screenai.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.screenai.service.NetworkQualityService.NetworkQualitySummary;
import com.screenai.service.NetworkQualityService.QualityLevel;

/**
 * Adaptive Streaming Service
 * 
 * Automatically adjusts streaming parameters based on network quality
 * to provide optimal viewing experience for all connected clients.
 */
@Service
public class AdaptiveStreamingService {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveStreamingService.class);

    @Autowired
    private NetworkQualityService networkQualityService;

    @Autowired
    private ScreenCaptureService screenCaptureService;

    @Value("${screenai.adaptive.enabled:true}")
    private boolean adaptiveStreamingEnabled;

    @Value("${screenai.adaptive.assessment-interval:10000}")
    private long assessmentInterval; // 10 seconds default

    @Value("${screenai.adaptive.stability-threshold:3}")
    private int stabilityThreshold; // Number of consistent assessments before adapting

    @Value("${screenai.adaptive.fps.min:5}")
    private int minFPS;

    @Value("${screenai.adaptive.fps.max:15}")
    private int maxFPS;

    @Value("${screenai.adaptive.quality.min:40}")
    private int minQuality;

    @Value("${screenai.adaptive.quality.max:90}")
    private int maxQuality;

    private final ScheduledExecutorService adaptationScheduler = Executors.newScheduledThreadPool(1);

    // State tracking for stability
    private QualityLevel lastQualityLevel = QualityLevel.GOOD;
    private int consecutiveAssessments = 0;
    private boolean isInitialized = false;

    // Performance tracking
    private int adaptationCount = 0;
    private long lastAdaptationTime = 0;

    /**
     * Initialize the adaptive streaming service
     */
    public void initialize() {
        if (!adaptiveStreamingEnabled) {
            logger.info("Adaptive streaming is disabled");
            return;
        }

        logger.info("Starting Adaptive Streaming Service");

        // Start periodic adaptation assessment
        adaptationScheduler.scheduleAtFixedRate(
                this::assessAndAdapt,
                assessmentInterval,
                assessmentInterval,
                TimeUnit.MILLISECONDS);

        isInitialized = true;
        logger.info("Adaptive Streaming Service initialized with {}ms assessment interval", assessmentInterval);
    }

    /**
     * Main adaptation logic - assess network quality and adjust streaming
     * parameters
     */
    private void assessAndAdapt() {
        try {
            if (!isInitialized || !adaptiveStreamingEnabled) {
                return;
            }

            NetworkQualitySummary summary = networkQualityService.getNetworkSummary();

            // Skip if no active connections
            if (summary.getActiveConnections() == 0) {
                logger.debug("No active connections - skipping adaptation");
                return;
            }

            QualityLevel currentQuality = summary.getDominantQuality();

            // Check for quality stability
            if (currentQuality == lastQualityLevel) {
                consecutiveAssessments++;
            } else {
                consecutiveAssessments = 1;
                lastQualityLevel = currentQuality;
            }

            // Only adapt after stable readings
            if (consecutiveAssessments >= stabilityThreshold) {
                performAdaptation(summary, currentQuality);
                consecutiveAssessments = 0; // Reset after adaptation
            } else {
                logger.debug("Quality level: {} (stability: {}/{})",
                        currentQuality.getDisplayName(), consecutiveAssessments, stabilityThreshold);
            }

        } catch (Exception e) {
            logger.error("Error during adaptive streaming assessment: {}", e.getMessage());
        }
    }

    /**
     * Perform streaming parameter adaptation based on network quality
     */
    private void performAdaptation(NetworkQualitySummary summary, QualityLevel qualityLevel) {
        try {
            boolean adapted = false;
            int currentFPS = screenCaptureService.getFrameRate();
            int currentQuality = screenCaptureService.getJpegQuality();

            int targetFPS = Math.max(minFPS, Math.min(maxFPS, summary.getRecommendedFPS()));
            int targetQuality = Math.max(minQuality, Math.min(maxQuality, summary.getRecommendedQuality()));

            StringBuilder adaptationLog = new StringBuilder();
            adaptationLog.append(String.format("Adaptation for %s network (%.1fms avg latency, %d connections): ",
                    qualityLevel.getDisplayName(),
                    summary.getOverallAverageLatency(),
                    summary.getActiveConnections()));

            // Adapt FPS if needed
            if (currentFPS != targetFPS) {
                // Note: In a real implementation, you would need methods to dynamically change
                // FPS
                adaptationLog.append(String.format("FPS %d→%d ", currentFPS, targetFPS));
                adapted = true;

                // Log the recommendation for now (actual implementation would require dynamic
                // FPS change)
                logger.info("FPS adaptation recommended: {} → {} (current implementation uses fixed FPS)",
                        currentFPS, targetFPS);
            }

            // Adapt quality if needed
            if (currentQuality != targetQuality) {
                // Note: In a real implementation, you would need methods to dynamically change
                // quality
                adaptationLog.append(String.format("Quality %d%%→%d%% ", currentQuality, targetQuality));
                adapted = true;

                // Log the recommendation for now (actual implementation would require dynamic
                // quality change)
                logger.info("Quality adaptation recommended: {}% → {}% (current implementation uses fixed quality)",
                        currentQuality, targetQuality);
            }

            // Special adaptations for poor quality
            if (qualityLevel == QualityLevel.POOR) {
                if (!screenCaptureService.isUltraFastMode()) {
                    adaptationLog.append("Enable ultra-fast mode ");
                    adapted = true;
                    logger.info("Ultra-fast mode recommended for poor network quality");
                }
            }

            if (adapted) {
                adaptationCount++;
                lastAdaptationTime = System.currentTimeMillis();
                logger.info("{}", adaptationLog.toString());

                // In future implementation, trigger actual parameter changes here
                triggerParameterUpdate(targetFPS, targetQuality, qualityLevel == QualityLevel.POOR);
            } else {
                logger.debug("No adaptation needed for {} quality (FPS: {}, Quality: {}%)",
                        qualityLevel.getDisplayName(), currentFPS, currentQuality);
            }

        } catch (Exception e) {
            logger.error("Error performing streaming adaptation: {}", e.getMessage());
        }
    }

    /**
     * Trigger parameter updates (placeholder for future dynamic parameter change
     * implementation)
     */
    private void triggerParameterUpdate(int targetFPS, int targetQuality, boolean enableUltraFast) {
        // Placeholder for future implementation
        // This would integrate with ScreenCaptureService to dynamically change
        // parameters

        logger.debug("Parameter update triggered: FPS={}, Quality={}%, UltraFast={}",
                targetFPS, targetQuality, enableUltraFast);

        // TODO: Implement dynamic parameter change methods in ScreenCaptureService
        // screenCaptureService.updateFPS(targetFPS);
        // screenCaptureService.updateQuality(targetQuality);
        // screenCaptureService.setUltraFastMode(enableUltraFast);
    }

    /**
     * Get adaptation statistics
     */
    public AdaptationStats getAdaptationStats() {
        return new AdaptationStats(
                adaptiveStreamingEnabled,
                isInitialized,
                adaptationCount,
                lastAdaptationTime,
                lastQualityLevel,
                consecutiveAssessments,
                stabilityThreshold);
    }

    /**
     * Enable or disable adaptive streaming
     */
    public void setAdaptiveStreamingEnabled(boolean enabled) {
        this.adaptiveStreamingEnabled = enabled;
        logger.info("Adaptive streaming {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Force an immediate adaptation assessment
     */
    public void forceAdaptation() {
        if (isInitialized && adaptiveStreamingEnabled) {
            logger.info("Forcing immediate adaptation assessment");
            assessAndAdapt();
        } else {
            logger.warn("Cannot force adaptation - service not initialized or disabled");
        }
    }

    /**
     * Shutdown the adaptive streaming service
     */
    public void shutdown() {
        if (adaptationScheduler != null && !adaptationScheduler.isShutdown()) {
            adaptationScheduler.shutdown();
            try {
                if (!adaptationScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    adaptationScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                adaptationScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Adaptive Streaming Service shut down");
    }

    /**
     * Adaptation statistics data structure
     */
    public static class AdaptationStats {
        private final boolean enabled;
        private final boolean initialized;
        private final int adaptationCount;
        private final long lastAdaptationTime;
        private final QualityLevel lastQualityLevel;
        private final int consecutiveAssessments;
        private final int stabilityThreshold;

        public AdaptationStats(boolean enabled, boolean initialized, int adaptationCount,
                long lastAdaptationTime, QualityLevel lastQualityLevel,
                int consecutiveAssessments, int stabilityThreshold) {
            this.enabled = enabled;
            this.initialized = initialized;
            this.adaptationCount = adaptationCount;
            this.lastAdaptationTime = lastAdaptationTime;
            this.lastQualityLevel = lastQualityLevel;
            this.consecutiveAssessments = consecutiveAssessments;
            this.stabilityThreshold = stabilityThreshold;
        }

        // Getters
        public boolean isEnabled() {
            return enabled;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public int getAdaptationCount() {
            return adaptationCount;
        }

        public long getLastAdaptationTime() {
            return lastAdaptationTime;
        }

        public QualityLevel getLastQualityLevel() {
            return lastQualityLevel;
        }

        public int getConsecutiveAssessments() {
            return consecutiveAssessments;
        }

        public int getStabilityThreshold() {
            return stabilityThreshold;
        }

        public long getTimeSinceLastAdaptation() {
            return lastAdaptationTime > 0 ? System.currentTimeMillis() - lastAdaptationTime : -1;
        }
    }
}