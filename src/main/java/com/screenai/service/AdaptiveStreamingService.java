package com.screenai.service;package com.screenai.service;package com.screenai.service;package com.screenai.service;



import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;import org.slf4j.Logger;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.event.EventListener;import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;

import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Autowired;import org.slf4j.Logger;import java.util.concurrent.Executors;



import com.screenai.service.NetworkQualityService.NetworkQuality;import org.springframework.beans.factory.annotation.Value;



import jakarta.annotation.PreDestroy;import org.springframework.context.event.EventListener;import org.slf4j.LoggerFactory;import java.util.concurrent.ScheduledExecutorService;

import java.util.concurrent.Executors;

import java.util.concurrent.ScheduledExecutorService;import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Autowired;import java.util.concurrent.TimeUnit;

/**

 * Adaptive streaming service that integrates with existing PerformanceMonitorService

 * and adjusts streaming parameters based on network quality

 */import com.screenai.service.NetworkQualityService.NetworkQuality;import org.springframework.beans.factory.annotation.Value;

@Service

public class AdaptiveStreamingService {

    

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveStreamingService.class);import jakarta.annotation.PreDestroy;import org.springframework.context.event.EventListener;import org.slf4j.Logger;

    

    @Autowiredimport java.util.concurrent.Executors;

    private NetworkQualityService networkQualityService;

    import java.util.concurrent.ScheduledExecutorService;import org.springframework.boot.context.event.ApplicationReadyEvent;import org.slf4j.LoggerFactory;

    // Configuration properties - using Spring Boot's YAML configuration

    @Value("${screenai.adaptive.assessment-interval:10000}")import java.util.concurrent.TimeUnit;

    private long assessmentInterval = 10000; // 10 seconds

    import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Autowired;

    @Value("${screenai.adaptive.bitrate.max:2000}")

    private int maxBitrate = 2000; // kbps/**

    

    @Value("${screenai.adaptive.bitrate.min:500}") * Adaptive streaming service that integrates with existing PerformanceMonitorServiceimport org.springframework.beans.factory.annotation.Value;

    private int minBitrate = 500; // kbps

     * and adjusts streaming parameters based on network quality

    @Value("${screenai.adaptive.framerate.max:30}")

    private int maxFrameRate = 30; */import com.screenai.service.NetworkQualityService.NetworkQuality;import org.springframework.stereotype.Service;

    

    @Value("${screenai.adaptive.framerate.min:10}")@Service

    private int minFrameRate = 10;

    public class AdaptiveStreamingService {

    private ScheduledExecutorService adaptiveScheduler;

    private volatile boolean isRunning = false;    

    private volatile AdaptiveSettings currentSettings;

        private static final Logger logger = LoggerFactory.getLogger(AdaptiveStreamingService.class);import jakarta.annotation.PreDestroy;import com.screenai.service.NetworkQualityService.NetworkQualitySummary;

    @EventListener(ApplicationReadyEvent.class)

    public void initialize() {    

        // Initialize with default settings

        this.currentSettings = new AdaptiveSettings(    @Autowiredimport java.util.concurrent.Executors;import com.screenai.service.NetworkQualityService.QualityLevel;

            1500, // Default bitrate

            15,   // Default frame rate - matches existing FRAME_RATE    private NetworkQualityService networkQualityService;

            1.0,  // Full resolution

            NetworkQuality.GOOD // Default quality assumption    import java.util.concurrent.ScheduledExecutorService;

        );

            @Autowired

        startAdaptiveAssessment();

        logger.info("AdaptiveStreamingService initialized with {}ms assessment interval", assessmentInterval);    private PerformanceMonitorService performanceMonitorService;import java.util.concurrent.TimeUnit;/**

    }

        

    private void startAdaptiveAssessment() {

        if (isRunning) return;    // Configuration properties - using Spring Boot's YAML configuration * Adaptive Streaming Service

        

        isRunning = true;    @Value("${screenai.adaptive.assessment-interval:10000}")

        adaptiveScheduler = Executors.newSingleThreadScheduledExecutor();

            private long assessmentInterval = 10000; // 10 seconds/** * 

        adaptiveScheduler.scheduleAtFixedRate(

            this::assessAndAdapt,    

            assessmentInterval,

            assessmentInterval,    @Value("${screenai.adaptive.bitrate.max:2000}") * Adaptive streaming service that integrates with existing PerformanceMonitorService * Automatically adjusts streaming parameters based on network quality

            TimeUnit.MILLISECONDS

        );    private int maxBitrate = 2000; // kbps

        

        logger.info("Adaptive assessment started with {}ms interval", assessmentInterval);     * and adjusts streaming parameters based on network quality * to provide optimal viewing experience for all connected clients.

    }

        @Value("${screenai.adaptive.bitrate.min:500}")

    @PreDestroy

    public void cleanup() {    private int minBitrate = 500; // kbps */ */

        isRunning = false;

        if (adaptiveScheduler != null) {    

            adaptiveScheduler.shutdown();

        }    @Value("${screenai.adaptive.framerate.max:30}")@Service@Service

        logger.info("AdaptiveStreamingService stopped");

    }    private int maxFrameRate = 30;

    

    private void assessAndAdapt() {    public class AdaptiveStreamingService {public class AdaptiveStreamingService {

        try {

            NetworkQualityService.NetworkQualitySummary summary = networkQualityService.getOverallSummary();    @Value("${screenai.adaptive.framerate.min:10}")

            

            if (summary.getActiveSessions() == 0) {    private int minFrameRate = 10;    

                return;

            }    

            

            NetworkQuality overallQuality = summary.getOverallQuality();    @Value("${screenai.adaptive.resolution.scale-down:0.75}")    private static final Logger logger = LoggerFactory.getLogger(AdaptiveStreamingService.class);    private static final Logger logger = LoggerFactory.getLogger(AdaptiveStreamingService.class);

            AdaptiveSettings newSettings = calculateOptimalSettings(overallQuality, summary);

                private double resolutionScaleDown = 0.75;

            if (shouldUpdateSettings(newSettings)) {

                AdaptiveSettings oldSettings = currentSettings;        

                currentSettings = newSettings;

                    private ScheduledExecutorService adaptiveScheduler;

                logger.info("Adaptive settings updated: {} -> {} (Network: {}, Sessions: {})",

                    formatSettings(oldSettings),    private volatile boolean isRunning = false;    @Autowired    @Autowired

                    formatSettings(newSettings),

                    overallQuality.getDescription(),    

                    summary.getActiveSessions());

            }    // Current adaptive settings    private NetworkQualityService networkQualityService;    private NetworkQualityService networkQualityService;

            

        } catch (Exception e) {    private volatile AdaptiveSettings currentSettings;

            logger.error("Error during adaptive assessment", e);

        }        

    }

        @EventListener(ApplicationReadyEvent.class)

    private AdaptiveSettings calculateOptimalSettings(NetworkQuality quality, 

                                                    NetworkQualityService.NetworkQualitySummary summary) {    public void initialize() {    @Autowired    @Autowired

        

        int bitrate = calculateBitrate(quality);        // Initialize with default settings

        int frameRate = calculateFrameRate(quality, summary);

        double resolutionScale = calculateResolutionScale(quality);        this.currentSettings = new AdaptiveSettings(    private PerformanceMonitorService performanceMonitorService;    private ScreenCaptureService screenCaptureService;

        

        return new AdaptiveSettings(bitrate, frameRate, resolutionScale, quality);            1500, // Default bitrate

    }

                15,   // Default frame rate - matches existing FRAME_RATE    

    private int calculateBitrate(NetworkQuality quality) {

        float qualityFactor = quality.getQualityFactor();            1.0,  // Full resolution

        int baseBitrate = (int) (minBitrate + (maxBitrate - minBitrate) * qualityFactor);

        return Math.max(minBitrate, Math.min(maxBitrate, baseBitrate));            NetworkQuality.GOOD // Default quality assumption    // Configuration properties - using Spring Boot's YAML configuration    @Value("${screenai.adaptive.enabled:true}")

    }

            );

    private int calculateFrameRate(NetworkQuality quality, NetworkQualityService.NetworkQualitySummary summary) {

        float qualityFactor = quality.getQualityFactor();            @Value("${screenai.adaptive.assessment-interval:10000}")    private boolean adaptiveStreamingEnabled;

        int baseFrameRate = (int) (minFrameRate + (maxFrameRate - minFrameRate) * qualityFactor);

                startAdaptiveAssessment();

        // Adjust based on number of sessions

        if (summary.getActiveSessions() > 10) {        logger.info("AdaptiveStreamingService initialized with {}ms assessment interval", assessmentInterval);    private long assessmentInterval = 10000; // 10 seconds

            baseFrameRate = Math.max(minFrameRate, baseFrameRate - 5);

        } else if (summary.getActiveSessions() > 5) {    }

            baseFrameRate = Math.max(minFrameRate, baseFrameRate - 2);

        }            @Value("${screenai.adaptive.assessment-interval:10000}")

        

        return Math.max(minFrameRate, Math.min(maxFrameRate, baseFrameRate));    private void startAdaptiveAssessment() {

    }

            if (isRunning) return;    @Value("${screenai.adaptive.quality-threshold.excellent:0.95}")    private long assessmentInterval; // 10 seconds default

    private double calculateResolutionScale(NetworkQuality quality) {

        float qualityFactor = quality.getQualityFactor();        

        double scale = 0.5 + (0.5 * qualityFactor); // Scale between 0.5 and 1.0

        return Math.max(0.5, Math.min(1.0, scale));        isRunning = true;    private double excellentThreshold = 0.95;

    }

            adaptiveScheduler = Executors.newSingleThreadScheduledExecutor();

    private boolean shouldUpdateSettings(AdaptiveSettings newSettings) {

        if (currentSettings == null) return true;                @Value("${screenai.adaptive.stability-threshold:3}")

        

        boolean bitrateChange = Math.abs(newSettings.bitrate - currentSettings.bitrate) > 100;        adaptiveScheduler.scheduleAtFixedRate(

        boolean frameRateChange = Math.abs(newSettings.frameRate - currentSettings.frameRate) > 1;

        boolean resolutionChange = Math.abs(newSettings.resolutionScale - currentSettings.resolutionScale) > 0.1;            this::assessAndAdapt,    @Value("${screenai.adaptive.quality-threshold.good:0.8}")    private int stabilityThreshold; // Number of consistent assessments before adapting

        boolean qualityChange = !newSettings.networkQuality.equals(currentSettings.networkQuality);

                    assessmentInterval, // Initial delay

        return bitrateChange || frameRateChange || resolutionChange || qualityChange;

    }            assessmentInterval, // Period    private double goodThreshold = 0.8;

    

    private String formatSettings(AdaptiveSettings settings) {            TimeUnit.MILLISECONDS

        return String.format("bitrate=%dkbps,fps=%d,scale=%.2f", 

            settings.bitrate, settings.frameRate, settings.resolutionScale);        );        @Value("${screenai.adaptive.fps.min:5}")

    }

            

    public AdaptiveSettings getCurrentSettings() {

        return currentSettings;        logger.info("Adaptive assessment started with {}ms interval", assessmentInterval);    @Value("${screenai.adaptive.quality-threshold.fair:0.6}")    private int minFPS;

    }

        }

    public static class AdaptiveSettings {

        public final int bitrate;        private double fairThreshold = 0.6;

        public final int frameRate;

        public final double resolutionScale;    @PreDestroy

        public final NetworkQuality networkQuality;

            public void cleanup() {        @Value("${screenai.adaptive.fps.max:15}")

        public AdaptiveSettings(int bitrate, int frameRate, double resolutionScale, NetworkQuality networkQuality) {

            this.bitrate = bitrate;        isRunning = false;

            this.frameRate = frameRate;

            this.resolutionScale = resolutionScale;        if (adaptiveScheduler != null) {    @Value("${screenai.adaptive.bitrate.max:2000}")    private int maxFPS;

            this.networkQuality = networkQuality;

        }            adaptiveScheduler.shutdown();

    }

}            try {    private int maxBitrate = 2000; // kbps

                if (!adaptiveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {

                    adaptiveScheduler.shutdownNow();        @Value("${screenai.adaptive.quality.min:40}")

                }

            } catch (InterruptedException e) {    @Value("${screenai.adaptive.bitrate.min:500}")    private int minQuality;

                adaptiveScheduler.shutdownNow();

                Thread.currentThread().interrupt();    private int minBitrate = 500; // kbps

            }

        }        @Value("${screenai.adaptive.quality.max:90}")

        logger.info("AdaptiveStreamingService stopped");

    }    @Value("${screenai.adaptive.framerate.max:30}")    private int maxQuality;

    

    /**    private int maxFrameRate = 30;

     * Assess network conditions and adapt streaming parameters

     */        private final ScheduledExecutorService adaptationScheduler = Executors.newScheduledThreadPool(1);

    private void assessAndAdapt() {

        try {    @Value("${screenai.adaptive.framerate.min:10}")

            NetworkQualityService.NetworkQualitySummary summary = networkQualityService.getOverallSummary();

                private int minFrameRate = 10;    // State tracking for stability

            if (summary.getActiveSessions() == 0) {

                logger.debug("No active sessions - skipping adaptive assessment");        private QualityLevel lastQualityLevel = QualityLevel.GOOD;

                return;

            }    @Value("${screenai.adaptive.resolution.scale-down:0.75}")    private int consecutiveAssessments = 0;

            

            NetworkQuality overallQuality = summary.getOverallQuality();    private double resolutionScaleDown = 0.75;    private boolean isInitialized = false;

            AdaptiveSettings newSettings = calculateOptimalSettings(overallQuality, summary);

                

            // Apply settings if they've changed significantly

            if (shouldUpdateSettings(newSettings)) {    private ScheduledExecutorService adaptiveScheduler;    // Performance tracking

                AdaptiveSettings oldSettings = currentSettings;

                currentSettings = newSettings;    private volatile boolean isRunning = false;    private int adaptationCount = 0;

                

                logger.info("Adaptive settings updated: {} -> {} (Network: {}, Sessions: {})",        private long lastAdaptationTime = 0;

                    formatSettings(oldSettings),

                    formatSettings(newSettings),    // Current adaptive settings

                    overallQuality.getDescription(),

                    summary.getActiveSessions());    private volatile AdaptiveSettings currentSettings;    /**

            }

                     * Initialize the adaptive streaming service

        } catch (Exception e) {

            logger.error("Error during adaptive assessment", e);    @EventListener(ApplicationReadyEvent.class)     */

        }

    }    public void initialize() {    public void initialize() {

    

    /**        // Initialize with default settings        if (!adaptiveStreamingEnabled) {

     * Calculate optimal streaming settings based on network quality

     */        this.currentSettings = new AdaptiveSettings(            logger.info("Adaptive streaming is disabled");

    private AdaptiveSettings calculateOptimalSettings(NetworkQuality quality, 

                                                    NetworkQualityService.NetworkQualitySummary summary) {            1500, // Default bitrate            return;

        

        int bitrate = calculateBitrate(quality, summary);            15,   // Default frame rate - matches existing FRAME_RATE        }

        int frameRate = calculateFrameRate(quality, summary);

        double resolutionScale = calculateResolutionScale(quality, summary);            1.0,  // Full resolution

        

        return new AdaptiveSettings(bitrate, frameRate, resolutionScale, quality);            NetworkQuality.GOOD // Default quality assumption        logger.info("Starting Adaptive Streaming Service");

    }

            );

    private int calculateBitrate(NetworkQuality quality, NetworkQualityService.NetworkQualitySummary summary) {

        float qualityFactor = quality.getQualityFactor();                // Start periodic adaptation assessment

        

        // Scale bitrate based on quality factor        startAdaptiveAssessment();        adaptationScheduler.scheduleAtFixedRate(

        int baseBitrate = (int) (minBitrate + (maxBitrate - minBitrate) * qualityFactor);

                logger.info("AdaptiveStreamingService initialized with {}ms assessment interval", assessmentInterval);                this::assessAndAdapt,

        // Further adjust based on jitter - high jitter reduces bitrate

        if (summary.getAverageJitter() > 50) {    }                assessmentInterval,

            baseBitrate = (int) (baseBitrate * 0.8); // Reduce by 20% for high jitter

        } else if (summary.getAverageJitter() > 25) {                    assessmentInterval,

            baseBitrate = (int) (baseBitrate * 0.9); // Reduce by 10% for moderate jitter

        }    private void startAdaptiveAssessment() {                TimeUnit.MILLISECONDS);

        

        return Math.max(minBitrate, Math.min(maxBitrate, baseBitrate));        if (isRunning) return;

    }

                    isInitialized = true;

    private int calculateFrameRate(NetworkQuality quality, NetworkQualityService.NetworkQualitySummary summary) {

        float qualityFactor = quality.getQualityFactor();        isRunning = true;        logger.info("Adaptive Streaming Service initialized with {}ms assessment interval", assessmentInterval);

        

        // Scale frame rate based on quality factor        adaptiveScheduler = Executors.newSingleThreadScheduledExecutor();    }

        int baseFrameRate = (int) (minFrameRate + (maxFrameRate - minFrameRate) * qualityFactor);

                

        // Adjust based on number of sessions - more sessions = lower frame rate per session

        if (summary.getActiveSessions() > 10) {        adaptiveScheduler.scheduleAtFixedRate(    /**

            baseFrameRate = Math.max(minFrameRate, baseFrameRate - 5);

        } else if (summary.getActiveSessions() > 5) {            this::assessAndAdapt,     * Main adaptation logic - assess network quality and adjust streaming

            baseFrameRate = Math.max(minFrameRate, baseFrameRate - 2);

        }            assessmentInterval, // Initial delay     * parameters

        

        return Math.max(minFrameRate, Math.min(maxFrameRate, baseFrameRate));            assessmentInterval, // Period     */

    }

                TimeUnit.MILLISECONDS    private void assessAndAdapt() {

    private double calculateResolutionScale(NetworkQuality quality, NetworkQualityService.NetworkQualitySummary summary) {

        float qualityFactor = quality.getQualityFactor();        );        try {

        

        // Start with full resolution for excellent quality                    if (!isInitialized || !adaptiveStreamingEnabled) {

        double scale = 0.5 + (0.5 * qualityFactor); // Scale between 0.5 and 1.0

                logger.info("Adaptive assessment started with {}ms interval", assessmentInterval);                return;

        // Reduce resolution for high latency

        if (summary.getAverageLatency() > 200) {    }            }

            scale *= resolutionScaleDown;

        }    

        

        return Math.max(0.5, Math.min(1.0, scale));    @PreDestroy            NetworkQualitySummary summary = networkQualityService.getNetworkSummary();

    }

        public void cleanup() {

    /**

     * Check if settings should be updated (avoid minor fluctuations)        isRunning = false;            // Skip if no active connections

     */

    private boolean shouldUpdateSettings(AdaptiveSettings newSettings) {        if (adaptiveScheduler != null) {            if (summary.getActiveConnections() == 0) {

        if (currentSettings == null) return true;

                    adaptiveScheduler.shutdown();                logger.debug("No active connections - skipping adaptation");

        // Update if significant change in any parameter

        boolean bitrateChange = Math.abs(newSettings.bitrate - currentSettings.bitrate) > 100;            try {                return;

        boolean frameRateChange = Math.abs(newSettings.frameRate - currentSettings.frameRate) > 1;

        boolean resolutionChange = Math.abs(newSettings.resolutionScale - currentSettings.resolutionScale) > 0.1;                if (!adaptiveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {            }

        boolean qualityChange = !newSettings.networkQuality.equals(currentSettings.networkQuality);

                            adaptiveScheduler.shutdownNow();

        return bitrateChange || frameRateChange || resolutionChange || qualityChange;

    }                }            QualityLevel currentQuality = summary.getDominantQuality();

    

    private String formatSettings(AdaptiveSettings settings) {            } catch (InterruptedException e) {

        return String.format("bitrate=%dkbps,fps=%d,scale=%.2f", 

            settings.bitrate, settings.frameRate, settings.resolutionScale);                adaptiveScheduler.shutdownNow();            // Check for quality stability

    }

                    Thread.currentThread().interrupt();            if (currentQuality == lastQualityLevel) {

    /**

     * Get current adaptive streaming settings            }                consecutiveAssessments++;

     */

    public AdaptiveSettings getCurrentSettings() {        }            } else {

        return currentSettings;

    }        logger.info("AdaptiveStreamingService stopped");                consecutiveAssessments = 1;

    

    /**    }                lastQualityLevel = currentQuality;

     * Get recommended encoder settings based on current adaptive settings

     */                }

    public EncoderRecommendations getEncoderRecommendations() {

        AdaptiveSettings settings = currentSettings;    /**

        

        // Recommend encoder based on quality requirements     * Assess network conditions and adapt streaming parameters            // Only adapt after stable readings

        String recommendedEncoder = switch (settings.networkQuality) {

            case EXCELLENT -> "nvenc"; // Hardware encoding for best quality     */            if (consecutiveAssessments >= stabilityThreshold) {

            case GOOD -> "libx264";    // Software encoding balanced

            case FAIR -> "libx264";    // Software encoding with lower settings    private void assessAndAdapt() {                performAdaptation(summary, currentQuality);

            case POOR -> "libx264";    // Most compatible encoder

        };        try {                consecutiveAssessments = 0; // Reset after adaptation

        

        return new EncoderRecommendations(            NetworkQualityService.NetworkQualitySummary summary = networkQualityService.getOverallSummary();            } else {

            recommendedEncoder,

            settings.bitrate,                            logger.debug("Quality level: {} (stability: {}/{})",

            settings.frameRate,

            settings.resolutionScale,            if (summary.getActiveSessions() == 0) {                        currentQuality.getDisplayName(), consecutiveAssessments, stabilityThreshold);

            settings.networkQuality.getDescription()

        );                logger.debug("No active sessions - skipping adaptive assessment");            }

    }

                    return;

    /**

     * Adaptive streaming settings            }        } catch (Exception e) {

     */

    public static class AdaptiveSettings {                        logger.error("Error during adaptive streaming assessment: {}", e.getMessage());

        public final int bitrate;       // kbps

        public final int frameRate;     // fps            NetworkQuality overallQuality = summary.getOverallQuality();        }

        public final double resolutionScale; // 0.5 to 1.0

        public final NetworkQuality networkQuality;            AdaptiveSettings newSettings = calculateOptimalSettings(overallQuality, summary);    }

        

        public AdaptiveSettings(int bitrate, int frameRate, double resolutionScale, NetworkQuality networkQuality) {            

            this.bitrate = bitrate;

            this.frameRate = frameRate;            // Apply settings if they've changed significantly    /**

            this.resolutionScale = resolutionScale;

            this.networkQuality = networkQuality;            if (shouldUpdateSettings(newSettings)) {     * Perform streaming parameter adaptation based on network quality

        }

    }                AdaptiveSettings oldSettings = currentSettings;     */

    

    /**                currentSettings = newSettings;    private void performAdaptation(NetworkQualitySummary summary, QualityLevel qualityLevel) {

     * Encoder recommendations based on adaptive assessment

     */                        try {

    public static class EncoderRecommendations {

        public final String encoderType;                logger.info("Adaptive settings updated: {} -> {} (Network: {}, Sessions: {})",            boolean adapted = false;

        public final int bitrate;

        public final int frameRate;                    formatSettings(oldSettings),            int currentFPS = screenCaptureService.getFrameRate();

        public final double resolutionScale;

        public final String reason;                    formatSettings(newSettings),            int currentQuality = screenCaptureService.getJpegQuality();

        

        public EncoderRecommendations(String encoderType, int bitrate, int frameRate,                     overallQuality.getDescription(),

                                    double resolutionScale, String reason) {

            this.encoderType = encoderType;                    summary.getActiveSessions());            int targetFPS = Math.max(minFPS, Math.min(maxFPS, summary.getRecommendedFPS()));

            this.bitrate = bitrate;

            this.frameRate = frameRate;            }            int targetQuality = Math.max(minQuality, Math.min(maxQuality, summary.getRecommendedQuality()));

            this.resolutionScale = resolutionScale;

            this.reason = reason;            

        }

    }        } catch (Exception e) {            StringBuilder adaptationLog = new StringBuilder();

}
            logger.error("Error during adaptive assessment", e);            adaptationLog.append(String.format("Adaptation for %s network (%.1fms avg latency, %d connections): ",

        }                    qualityLevel.getDisplayName(),

    }                    summary.getOverallAverageLatency(),

                        summary.getActiveConnections()));

    /**

     * Calculate optimal streaming settings based on network quality            // Adapt FPS if needed

     */            if (currentFPS != targetFPS) {

    private AdaptiveSettings calculateOptimalSettings(NetworkQuality quality,                 // Note: In a real implementation, you would need methods to dynamically change

                                                    NetworkQualityService.NetworkQualitySummary summary) {                // FPS

                        adaptationLog.append(String.format("FPS %d→%d ", currentFPS, targetFPS));

        int bitrate = calculateBitrate(quality, summary);                adapted = true;

        int frameRate = calculateFrameRate(quality, summary);

        double resolutionScale = calculateResolutionScale(quality, summary);                // Log the recommendation for now (actual implementation would require dynamic

                        // FPS change)

        return new AdaptiveSettings(bitrate, frameRate, resolutionScale, quality);                logger.info("FPS adaptation recommended: {} → {} (current implementation uses fixed FPS)",

    }                        currentFPS, targetFPS);

                }

    private int calculateBitrate(NetworkQuality quality, NetworkQualityService.NetworkQualitySummary summary) {

        float qualityFactor = quality.getQualityFactor();            // Adapt quality if needed

                    if (currentQuality != targetQuality) {

        // Scale bitrate based on quality factor                // Note: In a real implementation, you would need methods to dynamically change

        int baseBitrate = (int) (minBitrate + (maxBitrate - minBitrate) * qualityFactor);                // quality

                        adaptationLog.append(String.format("Quality %d%%→%d%% ", currentQuality, targetQuality));

        // Further adjust based on jitter - high jitter reduces bitrate                adapted = true;

        if (summary.getAverageJitter() > 50) {

            baseBitrate = (int) (baseBitrate * 0.8); // Reduce by 20% for high jitter                // Log the recommendation for now (actual implementation would require dynamic

        } else if (summary.getAverageJitter() > 25) {                // quality change)

            baseBitrate = (int) (baseBitrate * 0.9); // Reduce by 10% for moderate jitter                logger.info("Quality adaptation recommended: {}% → {}% (current implementation uses fixed quality)",

        }                        currentQuality, targetQuality);

                    }

        return Math.max(minBitrate, Math.min(maxBitrate, baseBitrate));

    }            // Special adaptations for poor quality

                if (qualityLevel == QualityLevel.POOR) {

    private int calculateFrameRate(NetworkQuality quality, NetworkQualityService.NetworkQualitySummary summary) {                if (!screenCaptureService.isUltraFastMode()) {

        float qualityFactor = quality.getQualityFactor();                    adaptationLog.append("Enable ultra-fast mode ");

                            adapted = true;

        // Scale frame rate based on quality factor                    logger.info("Ultra-fast mode recommended for poor network quality");

        int baseFrameRate = (int) (minFrameRate + (maxFrameRate - minFrameRate) * qualityFactor);                }

                    }

        // Adjust based on number of sessions - more sessions = lower frame rate per session

        if (summary.getActiveSessions() > 10) {            if (adapted) {

            baseFrameRate = Math.max(minFrameRate, baseFrameRate - 5);                adaptationCount++;

        } else if (summary.getActiveSessions() > 5) {                lastAdaptationTime = System.currentTimeMillis();

            baseFrameRate = Math.max(minFrameRate, baseFrameRate - 2);                logger.info("{}", adaptationLog.toString());

        }

                        // In future implementation, trigger actual parameter changes here

        return Math.max(minFrameRate, Math.min(maxFrameRate, baseFrameRate));                triggerParameterUpdate(targetFPS, targetQuality, qualityLevel == QualityLevel.POOR);

    }            } else {

                    logger.debug("No adaptation needed for {} quality (FPS: {}, Quality: {}%)",

    private double calculateResolutionScale(NetworkQuality quality, NetworkQualityService.NetworkQualitySummary summary) {                        qualityLevel.getDisplayName(), currentFPS, currentQuality);

        float qualityFactor = quality.getQualityFactor();            }

        

        // Start with full resolution for excellent quality        } catch (Exception e) {

        double scale = 0.5 + (0.5 * qualityFactor); // Scale between 0.5 and 1.0            logger.error("Error performing streaming adaptation: {}", e.getMessage());

                }

        // Reduce resolution for high latency    }

        if (summary.getAverageLatency() > 200) {

            scale *= resolutionScaleDown;    /**

        }     * Trigger parameter updates (placeholder for future dynamic parameter change

             * implementation)

        return Math.max(0.5, Math.min(1.0, scale));     */

    }    private void triggerParameterUpdate(int targetFPS, int targetQuality, boolean enableUltraFast) {

            // Placeholder for future implementation

    /**        // This would integrate with ScreenCaptureService to dynamically change

     * Check if settings should be updated (avoid minor fluctuations)        // parameters

     */

    private boolean shouldUpdateSettings(AdaptiveSettings newSettings) {        logger.debug("Parameter update triggered: FPS={}, Quality={}%, UltraFast={}",

        if (currentSettings == null) return true;                targetFPS, targetQuality, enableUltraFast);

        

        // Update if significant change in any parameter        // TODO: Implement dynamic parameter change methods in ScreenCaptureService

        boolean bitrateChange = Math.abs(newSettings.bitrate - currentSettings.bitrate) > 100;        // screenCaptureService.updateFPS(targetFPS);

        boolean frameRateChange = Math.abs(newSettings.frameRate - currentSettings.frameRate) > 1;        // screenCaptureService.updateQuality(targetQuality);

        boolean resolutionChange = Math.abs(newSettings.resolutionScale - currentSettings.resolutionScale) > 0.1;        // screenCaptureService.setUltraFastMode(enableUltraFast);

        boolean qualityChange = !newSettings.networkQuality.equals(currentSettings.networkQuality);    }

        

        return bitrateChange || frameRateChange || resolutionChange || qualityChange;    /**

    }     * Get adaptation statistics

         */

    private String formatSettings(AdaptiveSettings settings) {    public AdaptationStats getAdaptationStats() {

        return String.format("bitrate=%dkbps,fps=%d,scale=%.2f",         return new AdaptationStats(

            settings.bitrate, settings.frameRate, settings.resolutionScale);                adaptiveStreamingEnabled,

    }                isInitialized,

                    adaptationCount,

    /**                lastAdaptationTime,

     * Get current adaptive streaming settings                lastQualityLevel,

     */                consecutiveAssessments,

    public AdaptiveSettings getCurrentSettings() {                stabilityThreshold);

        return currentSettings;    }

    }

        /**

    /**     * Enable or disable adaptive streaming

     * Get recommended encoder settings based on current adaptive settings     */

     */    public void setAdaptiveStreamingEnabled(boolean enabled) {

    public EncoderRecommendations getEncoderRecommendations() {        this.adaptiveStreamingEnabled = enabled;

        AdaptiveSettings settings = currentSettings;        logger.info("Adaptive streaming {}", enabled ? "enabled" : "disabled");

            }

        // Recommend encoder based on quality requirements

        String recommendedEncoder = switch (settings.networkQuality) {    /**

            case EXCELLENT -> "nvenc"; // Hardware encoding for best quality     * Force an immediate adaptation assessment

            case GOOD -> "libx264";    // Software encoding balanced     */

            case FAIR -> "libx264";    // Software encoding with lower settings    public void forceAdaptation() {

            case POOR -> "libx264";    // Most compatible encoder        if (isInitialized && adaptiveStreamingEnabled) {

        };            logger.info("Forcing immediate adaptation assessment");

                    assessAndAdapt();

        return new EncoderRecommendations(        } else {

            recommendedEncoder,            logger.warn("Cannot force adaptation - service not initialized or disabled");

            settings.bitrate,        }

            settings.frameRate,    }

            settings.resolutionScale,

            settings.networkQuality.getDescription()    /**

        );     * Shutdown the adaptive streaming service

    }     */

        public void shutdown() {

    /**        if (adaptationScheduler != null && !adaptationScheduler.isShutdown()) {

     * Adaptive streaming settings            adaptationScheduler.shutdown();

     */            try {

    public static class AdaptiveSettings {                if (!adaptationScheduler.awaitTermination(5, TimeUnit.SECONDS)) {

        public final int bitrate;       // kbps                    adaptationScheduler.shutdownNow();

        public final int frameRate;     // fps                }

        public final double resolutionScale; // 0.5 to 1.0            } catch (InterruptedException e) {

        public final NetworkQuality networkQuality;                adaptationScheduler.shutdownNow();

                        Thread.currentThread().interrupt();

        public AdaptiveSettings(int bitrate, int frameRate, double resolutionScale, NetworkQuality networkQuality) {            }

            this.bitrate = bitrate;        }

            this.frameRate = frameRate;        logger.info("Adaptive Streaming Service shut down");

            this.resolutionScale = resolutionScale;    }

            this.networkQuality = networkQuality;

        }    /**

    }     * Adaptation statistics data structure

         */

    /**    public static class AdaptationStats {

     * Encoder recommendations based on adaptive assessment        private final boolean enabled;

     */        private final boolean initialized;

    public static class EncoderRecommendations {        private final int adaptationCount;

        public final String encoderType;        private final long lastAdaptationTime;

        public final int bitrate;        private final QualityLevel lastQualityLevel;

        public final int frameRate;        private final int consecutiveAssessments;

        public final double resolutionScale;        private final int stabilityThreshold;

        public final String reason;

                public AdaptationStats(boolean enabled, boolean initialized, int adaptationCount,

        public EncoderRecommendations(String encoderType, int bitrate, int frameRate,                 long lastAdaptationTime, QualityLevel lastQualityLevel,

                                    double resolutionScale, String reason) {                int consecutiveAssessments, int stabilityThreshold) {

            this.encoderType = encoderType;            this.enabled = enabled;

            this.bitrate = bitrate;            this.initialized = initialized;

            this.frameRate = frameRate;            this.adaptationCount = adaptationCount;

            this.resolutionScale = resolutionScale;            this.lastAdaptationTime = lastAdaptationTime;

            this.reason = reason;            this.lastQualityLevel = lastQualityLevel;

        }            this.consecutiveAssessments = consecutiveAssessments;

    }            this.stabilityThreshold = stabilityThreshold;

}        }

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