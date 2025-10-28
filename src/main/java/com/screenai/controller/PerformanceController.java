package com.screenai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.screenai.model.PerformanceMetrics;
import com.screenai.service.PerformanceMonitorService;

/**
 * REST API Controller for performance metrics
 * Provides real-time streaming performance data via HTTP endpoints
 */
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceController.class);

    @Autowired
    private PerformanceMonitorService performanceMonitor;

    /**
     * Get current performance metrics
     * 
     * @return Current FPS, latency, dropped frames, CPU usage, memory usage
     */
    @GetMapping("/metrics")
    public ResponseEntity<PerformanceMetrics> getCurrentMetrics() {
        try {
            PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
            
            logger.debug("📊 Performance metrics requested: FPS={}, Latency={}ms, Drops={}", 
                        metrics.getFps(), metrics.getLatencyMs(), metrics.getDroppedFrames());
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("❌ Error retrieving performance metrics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get performance statistics summary
     * 
     * @return Aggregated performance statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<PerformanceStats> getPerformanceStats() {
        try {
            PerformanceMetrics current = performanceMonitor.getCurrentMetrics();
            
            PerformanceStats stats = new PerformanceStats(
                current.getFps(),
                current.getLatencyMs(),
                current.getDroppedFrames(),
                current.getTotalFrames(),
                current.getDropRate(),
                current.getCpuUsage(),
                current.getMemoryUsageMb(),
                current.getEncoderType(),
                performanceMonitor.isMonitoring()
            );
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("❌ Error retrieving performance stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check if performance monitoring is active
     * 
     * @return Monitoring status
     */
    @GetMapping("/status")
    public ResponseEntity<MonitoringStatus> getMonitoringStatus() {
        boolean isActive = performanceMonitor.isMonitoring();
        PerformanceMetrics metrics = isActive ? performanceMonitor.getCurrentMetrics() : null;
        
        MonitoringStatus status = new MonitoringStatus(
            isActive,
            isActive ? metrics.getEncoderType() : "none",
            isActive ? metrics.getFps() : 0.0,
            isActive ? metrics.getTotalFrames() : 0
        );
        
        return ResponseEntity.ok(status);
    }

    /**
     * Performance statistics DTO
     */
    public static class PerformanceStats {
        private final double currentFps;
        private final long currentLatencyMs;
        private final int droppedFrames;
        private final long totalFrames;
        private final double dropRate;
        private final double cpuUsage;
        private final double memoryUsageMb;
        private final String encoderType;
        private final boolean isMonitoring;

        public PerformanceStats(double currentFps, long currentLatencyMs, int droppedFrames,
                              long totalFrames, double dropRate, double cpuUsage,
                              double memoryUsageMb, String encoderType, boolean isMonitoring) {
            this.currentFps = currentFps;
            this.currentLatencyMs = currentLatencyMs;
            this.droppedFrames = droppedFrames;
            this.totalFrames = totalFrames;
            this.dropRate = dropRate;
            this.cpuUsage = cpuUsage;
            this.memoryUsageMb = memoryUsageMb;
            this.encoderType = encoderType;
            this.isMonitoring = isMonitoring;
        }

        // Getters
        public double getCurrentFps() { return currentFps; }
        public long getCurrentLatencyMs() { return currentLatencyMs; }
        public int getDroppedFrames() { return droppedFrames; }
        public long getTotalFrames() { return totalFrames; }
        public double getDropRate() { return dropRate; }
        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsageMb() { return memoryUsageMb; }
        public String getEncoderType() { return encoderType; }
        public boolean isMonitoring() { return isMonitoring; }
    }

    /**
     * Monitoring status DTO
     */
    public static class MonitoringStatus {
        private final boolean active;
        private final String encoderType;
        private final double currentFps;
        private final long totalFrames;

        public MonitoringStatus(boolean active, String encoderType, double currentFps, long totalFrames) {
            this.active = active;
            this.encoderType = encoderType;
            this.currentFps = currentFps;
            this.totalFrames = totalFrames;
        }

        // Getters
        public boolean isActive() { return active; }
        public String getEncoderType() { return encoderType; }
        public double getCurrentFps() { return currentFps; }
        public long getTotalFrames() { return totalFrames; }
    }
}
