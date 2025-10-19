package com.screenai.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.screenai.handler.ScreenShareWebSocketHandler;
import com.screenai.handler.ScreenShareWebSocketHandler.LatencyData;

/**
 * Network Quality Detection Service
 * 
 * Analyzes network conditions based on latency measurements, connection
 * stability,
 * and packet loss patterns to provide adaptive streaming recommendations.
 */
@Service
public class NetworkQualityService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkQualityService.class);

    @Autowired
    private ScreenShareWebSocketHandler webSocketHandler;

    // Quality assessment configuration
    private static final long EXCELLENT_LATENCY_THRESHOLD = 50; // < 50ms
    private static final long GOOD_LATENCY_THRESHOLD = 150; // 50-150ms
    private static final long FAIR_LATENCY_THRESHOLD = 300; // 150-300ms
    // > 300ms is considered Poor

    private static final double LATENCY_JITTER_THRESHOLD = 30.0; // High jitter threshold (ms)
    private static final int MIN_MEASUREMENTS_FOR_ASSESSMENT = 5; // Minimum pings needed
    private static final long QUALITY_ASSESSMENT_INTERVAL = 5000; // 5 seconds

    // Network quality tracking
    private final Map<String, NetworkQuality> sessionQualities = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> latencyHistory = new ConcurrentHashMap<>();
    private final ScheduledExecutorService qualityAnalyzer = Executors.newScheduledThreadPool(1);

    // Network quality enumeration
    public enum QualityLevel {
        EXCELLENT("Excellent", 4, 15, 100), // targetFPS, targetQuality%
        GOOD("Good", 3, 12, 80),
        FAIR("Fair", 2, 8, 60),
        POOR("Poor", 1, 5, 40);

        private final String displayName;
        private final int priority;
        private final int recommendedFPS;
        private final int recommendedQuality;

        QualityLevel(String displayName, int priority, int recommendedFPS, int recommendedQuality) {
            this.displayName = displayName;
            this.priority = priority;
            this.recommendedFPS = recommendedFPS;
            this.recommendedQuality = recommendedQuality;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getPriority() {
            return priority;
        }

        public int getRecommendedFPS() {
            return recommendedFPS;
        }

        public int getRecommendedQuality() {
            return recommendedQuality;
        }
    }

    // Network quality data structure
    public static class NetworkQuality {
        private QualityLevel level;
        private double averageLatency;
        private double latencyJitter;
        private int connectionStability;
        private LocalDateTime lastAssessment;
        private long assessmentCount;
        private String assessmentReason;

        public NetworkQuality(QualityLevel level, double averageLatency, double latencyJitter,
                int connectionStability, String reason) {
            this.level = level;
            this.averageLatency = averageLatency;
            this.latencyJitter = latencyJitter;
            this.connectionStability = connectionStability;
            this.lastAssessment = LocalDateTime.now();
            this.assessmentCount = 1;
            this.assessmentReason = reason;
        }

        // Getters and setters
        public QualityLevel getLevel() {
            return level;
        }

        public void setLevel(QualityLevel level) {
            this.level = level;
        }

        public double getAverageLatency() {
            return averageLatency;
        }

        public void setAverageLatency(double averageLatency) {
            this.averageLatency = averageLatency;
        }

        public double getLatencyJitter() {
            return latencyJitter;
        }

        public void setLatencyJitter(double latencyJitter) {
            this.latencyJitter = latencyJitter;
        }

        public int getConnectionStability() {
            return connectionStability;
        }

        public void setConnectionStability(int connectionStability) {
            this.connectionStability = connectionStability;
        }

        public LocalDateTime getLastAssessment() {
            return lastAssessment;
        }

        public void setLastAssessment(LocalDateTime lastAssessment) {
            this.lastAssessment = lastAssessment;
        }

        public long getAssessmentCount() {
            return assessmentCount;
        }

        public void incrementAssessmentCount() {
            this.assessmentCount++;
        }

        public String getAssessmentReason() {
            return assessmentReason;
        }

        public void setAssessmentReason(String assessmentReason) {
            this.assessmentReason = assessmentReason;
        }

        // Utility methods
        public int getRecommendedFPS() {
            return level.getRecommendedFPS();
        }

        public int getRecommendedQuality() {
            return level.getRecommendedQuality();
        }

        public boolean isConnectionGood() {
            return connectionStability >= 70;
        }

        public boolean requiresAdaptation() {
            return level == QualityLevel.FAIR || level == QualityLevel.POOR;
        }
    }

    /**
     * Initialize the network quality service
     */
    public void initialize() {
        logger.info("Starting Network Quality Detection Service");

        // Start periodic quality assessment
        qualityAnalyzer.scheduleAtFixedRate(
                this::assessAllSessionQualities,
                QUALITY_ASSESSMENT_INTERVAL,
                QUALITY_ASSESSMENT_INTERVAL,
                TimeUnit.MILLISECONDS);

        logger.info("Network Quality Service initialized with {}ms assessment interval",
                QUALITY_ASSESSMENT_INTERVAL);
    }

    /**
     * Assess network quality for all active sessions
     */
    private void assessAllSessionQualities() {
        try {
            Map<String, LatencyData> allLatencies = webSocketHandler.getAllSessionLatencies();

            for (Map.Entry<String, LatencyData> entry : allLatencies.entrySet()) {
                String sessionId = entry.getKey();
                LatencyData latencyData = entry.getValue();

                if (latencyData.isConnected() &&
                        latencyData.getMeasurementCount() >= MIN_MEASUREMENTS_FOR_ASSESSMENT) {

                    NetworkQuality quality = assessSessionQuality(sessionId, latencyData);
                    sessionQualities.put(sessionId, quality);

                    // Log quality changes
                    if (quality.getAssessmentCount() == 1 ||
                            quality.getAssessmentCount() % 10 == 0) {
                        logger.debug(
                                "Session {} network quality: {} (latency: {:.1f}ms, jitter: {:.1f}ms, stability: {}%)",
                                sessionId, quality.getLevel().getDisplayName(),
                                quality.getAverageLatency(), quality.getLatencyJitter(),
                                quality.getConnectionStability());
                    }
                }
            }

            // Clean up disconnected sessions
            sessionQualities.entrySet().removeIf(entry -> {
                LatencyData latencyData = allLatencies.get(entry.getKey());
                return latencyData == null || !latencyData.isConnected();
            });

        } catch (Exception e) {
            logger.error("Error during quality assessment: {}", e.getMessage());
        }
    }

    /**
     * Assess network quality for a specific session
     */
    public NetworkQuality assessSessionQuality(String sessionId, LatencyData latencyData) {
        double avgLatency = latencyData.getAverageLatency();
        double jitter = calculateLatencyJitter(sessionId, latencyData);
        int stability = calculateConnectionStability(latencyData);

        // Determine quality level
        QualityLevel level = determineQualityLevel(avgLatency, jitter, stability);
        String reason = buildAssessmentReason(avgLatency, jitter, stability, level);

        // Update or create quality record
        NetworkQuality existingQuality = sessionQualities.get(sessionId);
        if (existingQuality != null) {
            existingQuality.setLevel(level);
            existingQuality.setAverageLatency(avgLatency);
            existingQuality.setLatencyJitter(jitter);
            existingQuality.setConnectionStability(stability);
            existingQuality.setLastAssessment(LocalDateTime.now());
            existingQuality.incrementAssessmentCount();
            existingQuality.setAssessmentReason(reason);
            return existingQuality;
        } else {
            return new NetworkQuality(level, avgLatency, jitter, stability, reason);
        }
    }

    /**
     * Calculate latency jitter (variation) for a session
     */
    private double calculateLatencyJitter(String sessionId, LatencyData latencyData) {
        List<Long> history = latencyHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // Add current latency to history (keep last 20 measurements)
        history.add(latencyData.getLastLatency());
        if (history.size() > 20) {
            history.remove(0);
        }

        if (history.size() < 3) {
            return 0.0; // Not enough data for jitter calculation
        }

        // Calculate standard deviation as jitter measure
        double mean = history.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = history.stream()
                .mapToDouble(latency -> Math.pow(latency - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * Calculate connection stability percentage
     */
    private int calculateConnectionStability(LatencyData latencyData) {
        // Base stability on measurement consistency and latency range
        long latencyRange = latencyData.getMaxLatency() - latencyData.getMinLatency();
        double avgLatency = latencyData.getAverageLatency();

        // High stability if latency is consistent (low range relative to average)
        double rangeToAvgRatio = avgLatency > 0 ? latencyRange / avgLatency : 0;

        int stability;
        if (rangeToAvgRatio < 0.5) {
            stability = 95; // Very stable
        } else if (rangeToAvgRatio < 1.0) {
            stability = 80; // Good stability
        } else if (rangeToAvgRatio < 2.0) {
            stability = 60; // Fair stability
        } else {
            stability = 30; // Poor stability
        }

        // Adjust based on measurement count (more measurements = more reliable)
        int measurementCount = latencyData.getMeasurementCount();
        if (measurementCount < 10) {
            stability = Math.max(50, stability - 20); // Less reliable with few measurements
        }

        return Math.min(100, Math.max(0, stability));
    }

    /**
     * Determine quality level based on network metrics
     */
    private QualityLevel determineQualityLevel(double avgLatency, double jitter, int stability) {
        // Start with latency-based assessment
        QualityLevel baseLevel;
        if (avgLatency < EXCELLENT_LATENCY_THRESHOLD) {
            baseLevel = QualityLevel.EXCELLENT;
        } else if (avgLatency < GOOD_LATENCY_THRESHOLD) {
            baseLevel = QualityLevel.GOOD;
        } else if (avgLatency < FAIR_LATENCY_THRESHOLD) {
            baseLevel = QualityLevel.FAIR;
        } else {
            baseLevel = QualityLevel.POOR;
        }

        // Degrade quality based on jitter and stability
        if (jitter > LATENCY_JITTER_THRESHOLD && baseLevel.getPriority() > 1) {
            baseLevel = QualityLevel.values()[baseLevel.ordinal() + 1]; // Downgrade
        }

        if (stability < 50 && baseLevel.getPriority() > 1) {
            baseLevel = QualityLevel.values()[baseLevel.ordinal() + 1]; // Downgrade
        }

        return baseLevel;
    }

    /**
     * Build assessment reason string
     */
    private String buildAssessmentReason(double avgLatency, double jitter, int stability, QualityLevel level) {
        StringBuilder reason = new StringBuilder();
        reason.append(String.format("Latency: %.1fms", avgLatency));

        if (jitter > LATENCY_JITTER_THRESHOLD) {
            reason.append(String.format(", High jitter: %.1fms", jitter));
        }

        if (stability < 70) {
            reason.append(String.format(", Low stability: %d%%", stability));
        }

        reason.append(String.format(" → %s", level.getDisplayName()));
        return reason.toString();
    }

    /**
     * Get network quality for a specific session
     */
    public NetworkQuality getSessionQuality(String sessionId) {
        return sessionQualities.get(sessionId);
    }

    /**
     * Get network quality for all sessions
     */
    public Map<String, NetworkQuality> getAllSessionQualities() {
        return new ConcurrentHashMap<>(sessionQualities);
    }

    /**
     * Get overall network quality summary
     */
    public NetworkQualitySummary getNetworkSummary() {
        Map<QualityLevel, Integer> qualityDistribution = new ConcurrentHashMap<>();
        double totalAvgLatency = 0.0;
        int activeConnections = 0;

        for (NetworkQuality quality : sessionQualities.values()) {
            qualityDistribution.merge(quality.getLevel(), 1, Integer::sum);
            totalAvgLatency += quality.getAverageLatency();
            activeConnections++;
        }

        double overallAvgLatency = activeConnections > 0 ? totalAvgLatency / activeConnections : 0.0;

        // Determine dominant quality level
        QualityLevel dominantQuality = qualityDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(QualityLevel.GOOD);

        return new NetworkQualitySummary(dominantQuality, overallAvgLatency,
                activeConnections, qualityDistribution);
    }

    /**
     * Network quality summary data structure
     */
    public static class NetworkQualitySummary {
        private final QualityLevel dominantQuality;
        private final double overallAverageLatency;
        private final int activeConnections;
        private final Map<QualityLevel, Integer> qualityDistribution;

        public NetworkQualitySummary(QualityLevel dominantQuality, double overallAverageLatency,
                int activeConnections, Map<QualityLevel, Integer> qualityDistribution) {
            this.dominantQuality = dominantQuality;
            this.overallAverageLatency = overallAverageLatency;
            this.activeConnections = activeConnections;
            this.qualityDistribution = qualityDistribution;
        }

        public QualityLevel getDominantQuality() {
            return dominantQuality;
        }

        public double getOverallAverageLatency() {
            return overallAverageLatency;
        }

        public int getActiveConnections() {
            return activeConnections;
        }

        public Map<QualityLevel, Integer> getQualityDistribution() {
            return qualityDistribution;
        }

        public int getRecommendedFPS() {
            return dominantQuality.getRecommendedFPS();
        }

        public int getRecommendedQuality() {
            return dominantQuality.getRecommendedQuality();
        }

        public boolean shouldAdaptStreaming() {
            return dominantQuality == QualityLevel.FAIR || dominantQuality == QualityLevel.POOR;
        }
    }

    /**
     * Shutdown the service
     */
    public void shutdown() {
        if (qualityAnalyzer != null && !qualityAnalyzer.isShutdown()) {
            qualityAnalyzer.shutdown();
            try {
                if (!qualityAnalyzer.awaitTermination(5, TimeUnit.SECONDS)) {
                    qualityAnalyzer.shutdownNow();
                }
            } catch (InterruptedException e) {
                qualityAnalyzer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Network Quality Detection Service shut down");
    }
}