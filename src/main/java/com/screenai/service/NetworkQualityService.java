package com.screenai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service to assess network quality for WebSocket connections
 * Integrates with the existing PerformanceMonitorService architecture
 */
@Service
public class NetworkQualityService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkQualityService.class);

    // Network quality thresholds (in milliseconds)
    private static final double EXCELLENT_THRESHOLD = 50.0;
    private static final double GOOD_THRESHOLD = 100.0;
    private static final double FAIR_THRESHOLD = 200.0;
    // Above FAIR_THRESHOLD is considered POOR

    // Store ping measurements per session
    private final ConcurrentHashMap<String, List<Double>> sessionLatencies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NetworkQuality> sessionQualities = new ConcurrentHashMap<>();

    public enum NetworkQuality {
        EXCELLENT(0.95f, "Excellent connection"),
        GOOD(0.8f, "Good connection"),
        FAIR(0.6f, "Fair connection"),
        POOR(0.3f, "Poor connection");

        private final float qualityFactor;
        private final String description;

        NetworkQuality(float qualityFactor, String description) {
            this.qualityFactor = qualityFactor;
            this.description = description;
        }

        public float getQualityFactor() {
            return qualityFactor;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Record a ping measurement for a session
     */
    public void recordPing(String sessionId, double latencyMs) {
        sessionLatencies.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(latencyMs);

        // Keep only last 10 measurements for rolling average
        List<Double> latencies = sessionLatencies.get(sessionId);
        if (latencies.size() > 10) {
            latencies.remove(0);
        }

        // Update quality assessment
        NetworkQuality quality = assessNetworkQuality(sessionId);
        sessionQualities.put(sessionId, quality);

        logger.debug("Recorded ping for session {}: {}ms, quality: {}",
                sessionId, latencyMs, quality);
    }

    /**
     * Assess network quality based on recent ping measurements
     */
    public NetworkQuality assessNetworkQuality(String sessionId) {
        List<Double> latencies = sessionLatencies.get(sessionId);
        if (latencies == null || latencies.isEmpty()) {
            return NetworkQuality.POOR;
        }

        // Calculate average latency
        double avgLatency = latencies.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.MAX_VALUE);

        // Calculate jitter (standard deviation)
        double jitter = calculateJitter(latencies, avgLatency);

        // Assess quality based on average latency and jitter
        return determineQuality(avgLatency, jitter);
    }

    /**
     * Get current network quality for a session
     */
    public NetworkQuality getCurrentQuality(String sessionId) {
        return sessionQualities.getOrDefault(sessionId, NetworkQuality.POOR);
    }

    /**
     * Get average latency for a session
     */
    public double getAverageLatency(String sessionId) {
        List<Double> latencies = sessionLatencies.get(sessionId);
        if (latencies == null || latencies.isEmpty()) {
            return -1;
        }

        return latencies.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(-1);
    }

    /**
     * Get jitter (latency variation) for a session
     */
    public double getJitter(String sessionId) {
        List<Double> latencies = sessionLatencies.get(sessionId);
        if (latencies == null || latencies.size() < 2) {
            return 0;
        }

        double avg = getAverageLatency(sessionId);
        return calculateJitter(latencies, avg);
    }

    /**
     * Remove session data when connection is closed
     */
    public void removeSession(String sessionId) {
        sessionLatencies.remove(sessionId);
        sessionQualities.remove(sessionId);
        logger.debug("Removed network quality data for session: {}", sessionId);
    }

    /**
     * Get network quality summary for all active sessions
     */
    public NetworkQualitySummary getOverallSummary() {
        int totalSessions = sessionQualities.size();
        if (totalSessions == 0) {
            return new NetworkQualitySummary(0, 0, NetworkQuality.POOR, -1, 0);
        }

        // Calculate overall statistics
        double avgLatency = sessionLatencies.values().stream()
                .flatMap(List::stream)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(-1);

        NetworkQuality worstQuality = sessionQualities.values().stream()
                .min((q1, q2) -> Float.compare(q1.qualityFactor, q2.qualityFactor))
                .orElse(NetworkQuality.POOR);

        double avgJitter = sessionLatencies.entrySet().stream()
                .mapToDouble(entry -> getJitter(entry.getKey()))
                .average()
                .orElse(0);

        return new NetworkQualitySummary(totalSessions, totalSessions, worstQuality, avgLatency, avgJitter);
    }

    private double calculateJitter(List<Double> latencies, double average) {
        if (latencies.size() < 2)
            return 0;

        double variance = latencies.stream()
                .mapToDouble(latency -> Math.pow(latency - average, 2))
                .average()
                .orElse(0);

        return Math.sqrt(variance);
    }

    private NetworkQuality determineQuality(double avgLatency, double jitter) {
        // Factor in both latency and jitter for quality assessment
        double qualityScore = avgLatency + (jitter * 0.5); // Jitter has 50% weight of latency

        if (qualityScore <= EXCELLENT_THRESHOLD) {
            return NetworkQuality.EXCELLENT;
        } else if (qualityScore <= GOOD_THRESHOLD) {
            return NetworkQuality.GOOD;
        } else if (qualityScore <= FAIR_THRESHOLD) {
            return NetworkQuality.FAIR;
        } else {
            return NetworkQuality.POOR;
        }
    }

    /**
     * Summary class for network quality metrics
     */
    public static class NetworkQualitySummary {
        private final int activeSessions;
        private final int totalSessions;
        private final NetworkQuality overallQuality;
        private final double averageLatency;
        private final double averageJitter;

        public NetworkQualitySummary(int activeSessions, int totalSessions,
                NetworkQuality overallQuality, double averageLatency, double averageJitter) {
            this.activeSessions = activeSessions;
            this.totalSessions = totalSessions;
            this.overallQuality = overallQuality;
            this.averageLatency = averageLatency;
            this.averageJitter = averageJitter;
        }

        public int getActiveSessions() {
            return activeSessions;
        }

        public int getTotalSessions() {
            return totalSessions;
        }

        public NetworkQuality getOverallQuality() {
            return overallQuality;
        }

        public double getAverageLatency() {
            return averageLatency;
        }

        public double getAverageJitter() {
            return averageJitter;
        }
    }
}