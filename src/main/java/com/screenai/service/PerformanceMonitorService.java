package com.screenai.service;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.screenai.model.PerformanceMetrics;
import com.sun.management.OperatingSystemMXBean;

/**
 * Tracks FPS, latency, dropped frames, CPU, memory
 */
@Service
public class PerformanceMonitorService {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitorService.class);
    
    // Performance tracking
    private final ConcurrentLinkedQueue<Long> frameTimestamps = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> latencyMeasurements = new ConcurrentLinkedQueue<>();
    
    private volatile boolean monitoring = false;
    private int totalFrames = 0;
    private int droppedFrames = 0;
    private long sessionStartTime = 0;
    private String currentEncoderType = "Unknown";
    
    private final List<MetricsListener> listeners = new CopyOnWriteArrayList<>();
    
    // Scheduled metrics collection
    private ScheduledExecutorService metricsScheduler;
    private static final int METRICS_INTERVAL_MS = 1000; // 1 second
    
    // System monitoring
    private final OperatingSystemMXBean osBean;
    private final Runtime runtime = Runtime.getRuntime();
    
    public PerformanceMonitorService() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }
    
   
    public void addMetricsListener(MetricsListener listener) {
        listeners.add(listener);
        logger.debug("Metrics listener added: {}", listener.getClass().getSimpleName());
    }
    
    public void removeMetricsListener(MetricsListener listener) {
        listeners.remove(listener);
        logger.debug("Metrics listener removed: {}", listener.getClass().getSimpleName());
    }
    
    /**
     * Start monitoring
     */
    public void startMonitoring(String encoderType) {
        this.monitoring = true;
        this.currentEncoderType = encoderType;
        this.sessionStartTime = System.currentTimeMillis();
        this.totalFrames = 0;
        this.droppedFrames = 0;
        
        // Clear queues
        frameTimestamps.clear();
        latencyMeasurements.clear();
        
        // Start periodic metrics collection
        metricsScheduler = Executors.newScheduledThreadPool(1);
        metricsScheduler.scheduleAtFixedRate(
            this::collectAndBroadcastMetrics,
            0,
            METRICS_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        logger.info("📊 Performance monitoring started with encoder: {}", encoderType);
    }
    
    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        this.monitoring = false;
        
        if (metricsScheduler != null && !metricsScheduler.isShutdown()) {
            metricsScheduler.shutdown();
            try {
                metricsScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                metricsScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("📊 Performance monitoring stopped. Total frames: {}, Dropped: {} ({:.2f}%)",
                   totalFrames, droppedFrames, 
                   totalFrames > 0 ? (droppedFrames * 100.0 / totalFrames) : 0);
    }
    
    /**
     * Check if monitoring is active
     */
    public boolean isMonitoring() {
        return monitoring;
    }
    
    /**
     * Record frame capture timestamp
     */
    public void recordFrameCapture() {
        long now = System.currentTimeMillis();
        frameTimestamps.offer(now);
        totalFrames++;
        
        // Keep only last 2 seconds of timestamps
        cleanOldTimestamps();
    }
    
    /**
     * Record dropped frame
     */
    public void recordDroppedFrame() {
        droppedFrames++;
        logger.debug("⚠️ Frame dropped. Total dropped: {}/{} ({:.2f}%)", 
                    droppedFrames, totalFrames,
                    totalFrames > 0 ? (droppedFrames * 100.0 / totalFrames) : 0);
    }
    
    /**
     * Record latency measurement
     */
    public void recordLatency(long latencyMs) {
        latencyMeasurements.offer(latencyMs);
        
        // Keep only last 10 measurements
        while (latencyMeasurements.size() > 10) {
            latencyMeasurements.poll();
        }
    }
    
    /**
     * Calculate current FPS
     */
    public double getCurrentFps() {
        cleanOldTimestamps();
        
        if (frameTimestamps.size() < 2) {
            return 0.0;
        }
        
        Long oldest = frameTimestamps.peek();
        Long[] timestamps = frameTimestamps.toArray(new Long[0]);
        Long newest = timestamps[timestamps.length - 1];
        
        if (oldest == null || newest == null) {
            return 0.0;
        }
        
        long timeSpan = newest - oldest;
        if (timeSpan <= 0) {
            return 0.0;
        }
        
        return (frameTimestamps.size() - 1) * 1000.0 / timeSpan;
    }
    
    /**
     * Get average latency
     */
    public long getAverageLatency() {
        if (latencyMeasurements.isEmpty()) {
            return 0;
        }
        
        long sum = 0;
        for (Long latency : latencyMeasurements) {
            sum += latency;
        }
        
        return sum / latencyMeasurements.size();
    }
    
    /**
     * Get current CPU usage
     */
    public double getCpuUsage() {
        double cpu = osBean.getProcessCpuLoad() * 100.0;
        return cpu >= 0 ? cpu : 0.0;
    }
    
    /**
     * Get current memory usage in MB
     */
    public long getMemoryUsageMb() {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    /**
     * Collect metrics and notify listeners
     */
    private void collectAndBroadcastMetrics() {
        try {
            PerformanceMetrics metrics = new PerformanceMetrics.Builder()
                .fps(getCurrentFps())
                .latency(getAverageLatency())
                .droppedFrames(droppedFrames)
                .totalFrames(totalFrames)
                .cpuUsage(getCpuUsage())
                .memoryUsage(getMemoryUsageMb())
                .encoderType(currentEncoderType)
                .build();
            
            // Notify all listeners
            for (MetricsListener listener : listeners) {
                try {
                    listener.onMetricsUpdate(metrics);
                } catch (Exception e) {
                    logger.error("Error notifying metrics listener", e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error collecting metrics", e);
        }
    }
    
    /**
     * Remove timestamps older than 2 seconds
     */
    private void cleanOldTimestamps() {
        long cutoff = System.currentTimeMillis() - 2000;
        while (!frameTimestamps.isEmpty() && frameTimestamps.peek() < cutoff) {
            frameTimestamps.poll();
        }
    }
    
    /**
     * Get current metrics snapshot
     */
    public PerformanceMetrics getCurrentMetrics() {
        return new PerformanceMetrics.Builder()
            .fps(getCurrentFps())
            .latency(getAverageLatency())
            .droppedFrames(droppedFrames)
            .totalFrames(totalFrames)
            .cpuUsage(getCpuUsage())
            .memoryUsage(getMemoryUsageMb())
            .encoderType(currentEncoderType)
            .build();
    }
    
   
    public interface MetricsListener {
        void onMetricsUpdate(PerformanceMetrics metrics);
    }
}
