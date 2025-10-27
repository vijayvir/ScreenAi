package com.screenai.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.screenai.handler.ScreenShareWebSocketHandler;
import com.screenai.service.ScreenCaptureService;
import com.screenai.service.NetworkQualityService;
import com.screenai.service.AdaptiveStreamingService;

/**
 * REST API Controller for screen sharing status and information
 */
@RestController
public class ScreenSharingApiController {

    private static final Logger logger = LoggerFactory.getLogger(ScreenSharingApiController.class);

    @Autowired
    private ScreenCaptureService screenCaptureService;

    @Autowired
    private ScreenShareWebSocketHandler webSocketHandler;

    @Autowired
    private NetworkQualityService networkQualityService;

    @Autowired
    private AdaptiveStreamingService adaptiveStreamingService;

    /**
     * Get current screen sharing status and statistics
     * 
     * @return Status information including capture method, viewer count, etc.
     */
    @GetMapping("/api/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

            // Safely get service status
            try {
                status.put("initialized", screenCaptureService.isInitialized());
                status.put("capturing", screenCaptureService.isCapturing());
                status.put("captureMethod", screenCaptureService.getCaptureMethod());
            } catch (Exception e) {
                logger.warn("Error getting screen capture status: {}", e.getMessage());
                status.put("initialized", false);
                status.put("capturing", false);
                status.put("captureMethod", "Error");
                status.put("error", "Screen capture service unavailable");
            }

            // Safely get screen bounds
            try {
                if (screenCaptureService.getScreenBounds() != null) {
                    Map<String, Object> screen = new HashMap<>();
                    screen.put("width", screenCaptureService.getScreenBounds().width);
                    screen.put("height", screenCaptureService.getScreenBounds().height);
                    status.put("screenResolution", screen);
                } else {
                    status.put("screenResolution", null);
                }
            } catch (Exception e) {
                logger.warn("Error getting screen bounds: {}", e.getMessage());
                status.put("screenResolution", null);
            }

            // Safely get viewer count
            try {
                status.put("viewerCount", webSocketHandler.getViewerCount());
            } catch (Exception e) {
                logger.warn("Error getting viewer count: {}", e.getMessage());
                status.put("viewerCount", 0);
            }

            // Performance configuration information
            try {
                Map<String, Object> performance = new HashMap<>();
                performance.put("frameRate", screenCaptureService.getFrameRate());
                performance.put("jpegQuality", screenCaptureService.getJpegQuality());
                performance.put("ultraFastMode", screenCaptureService.isUltraFastMode());
                performance.put("zeroLatencyMode", screenCaptureService.isZeroLatencyMode());
                status.put("performance", performance);
            } catch (Exception e) {
                logger.warn("Error getting performance configuration: {}", e.getMessage());
                status.put("performance", null);
            }

            // System information (should not fail, but just in case)
            status.put("serverTime", System.currentTimeMillis());
            status.put("osName", System.getProperty("os.name", "Unknown"));
            status.put("javaVersion", System.getProperty("java.version", "Unknown"));

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Unexpected error in getStatus", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error");
            errorResponse.put("message", "Unable to retrieve status");
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Start screen capture manually
     */
    @GetMapping("/api/start-capture")
    public ResponseEntity<Map<String, Object>> startCapture() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Validate service availability
            if (screenCaptureService == null) {
                response.put("success", false);
                response.put("message", "Screen capture service not available");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }

            // Check initialization
            if (!screenCaptureService.isInitialized()) {
                logger.info("Initializing screen capture service...");
                try {
                    screenCaptureService.initialize();
                } catch (Exception initError) {
                    logger.error("Failed to initialize screen capture", initError);
                    response.put("success", false);
                    response.put("message", "Failed to initialize screen capture: " + initError.getMessage());
                    response.put("errorType", "INITIALIZATION_FAILED");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }

            // Start capture if not already running
            if (screenCaptureService.isInitialized() && !screenCaptureService.isCapturing()) {
                try {
                    screenCaptureService.startCapture();

                    // Verify it actually started
                    Thread.sleep(500); // Brief wait to check if start was successful
                    if (screenCaptureService.isCapturing()) {
                        response.put("success", true);
                        response.put("message", "Screen capture started successfully");
                        response.put("method", screenCaptureService.getCaptureMethod());
                        logger.info("Screen capture started successfully");
                    } else {
                        response.put("success", false);
                        response.put("message", "Screen capture failed to start (verification failed)");
                        response.put("errorType", "START_VERIFICATION_FAILED");
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    response.put("success", false);
                    response.put("message", "Operation interrupted");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                } catch (Exception startError) {
                    logger.error("Error starting screen capture", startError);
                    response.put("success", false);
                    response.put("message", "Failed to start capture: " + startError.getMessage());
                    response.put("errorType", "START_FAILED");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            } else if (screenCaptureService.isCapturing()) {
                response.put("success", true);
                response.put("message", "Screen capture already running");
                response.put("method", screenCaptureService.getCaptureMethod());
                logger.debug("Screen capture start requested but already running");
            } else {
                response.put("success", false);
                response.put("message", "Screen capture service not properly initialized");
                response.put("errorType", "SERVICE_NOT_READY");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Unexpected error in startCapture", e);
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            response.put("errorType", "UNEXPECTED_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Stop screen capture manually
     */
    @GetMapping("/api/stop-capture")
    public ResponseEntity<Map<String, Object>> stopCapture() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Validate service availability
            if (screenCaptureService == null) {
                response.put("success", false);
                response.put("message", "Screen capture service not available");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }

            if (screenCaptureService.isCapturing()) {
                try {
                    screenCaptureService.stopCapture();

                    // Verify it actually stopped
                    Thread.sleep(500); // Brief wait to check if stop was successful
                    if (!screenCaptureService.isCapturing()) {
                        response.put("success", true);
                        response.put("message", "Screen capture stopped successfully");
                        logger.info("Screen capture stopped successfully");
                    } else {
                        response.put("success", false);
                        response.put("message", "Screen capture failed to stop (verification failed)");
                        response.put("errorType", "STOP_VERIFICATION_FAILED");
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    response.put("success", false);
                    response.put("message", "Operation interrupted");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                } catch (Exception stopError) {
                    logger.error("Error stopping screen capture", stopError);
                    response.put("success", false);
                    response.put("message", "Failed to stop capture: " + stopError.getMessage());
                    response.put("errorType", "STOP_FAILED");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            } else {
                response.put("success", true);
                response.put("message", "Screen capture was not running");
                logger.debug("Screen capture stop requested but not running");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Unexpected error in stopCapture", e);
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            response.put("errorType", "UNEXPECTED_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get network quality assessment for all sessions
     * 
     * @return Network quality information for all connected viewers
     */
    @GetMapping("/api/network-quality")
    public ResponseEntity<Map<String, Object>> getNetworkQuality() {
        try {
            Map<String, Object> response = new HashMap<>();

            // Get network quality summary
            NetworkQualityService.NetworkQualitySummary summary = networkQualityService.getNetworkSummary();

            Map<String, Object> summaryData = new HashMap<>();
            summaryData.put("dominantQuality", summary.getDominantQuality().getDisplayName());
            summaryData.put("overallAverageLatency", Math.round(summary.getOverallAverageLatency() * 10.0) / 10.0);
            summaryData.put("activeConnections", summary.getActiveConnections());
            summaryData.put("recommendedFPS", summary.getRecommendedFPS());
            summaryData.put("recommendedQuality", summary.getRecommendedQuality());
            summaryData.put("shouldAdaptStreaming", summary.shouldAdaptStreaming());

            // Quality distribution
            Map<String, Integer> distribution = new HashMap<>();
            for (Map.Entry<NetworkQualityService.QualityLevel, Integer> entry : summary.getQualityDistribution()
                    .entrySet()) {
                distribution.put(entry.getKey().getDisplayName(), entry.getValue());
            }
            summaryData.put("qualityDistribution", distribution);

            response.put("summary", summaryData);

            // Get individual session qualities
            Map<String, NetworkQualityService.NetworkQuality> sessionQualities = networkQualityService
                    .getAllSessionQualities();
            Map<String, Object> sessions = new HashMap<>();

            for (Map.Entry<String, NetworkQualityService.NetworkQuality> entry : sessionQualities.entrySet()) {
                String sessionId = entry.getKey();
                NetworkQualityService.NetworkQuality quality = entry.getValue();

                Map<String, Object> sessionData = new HashMap<>();
                sessionData.put("quality", quality.getLevel().getDisplayName());
                sessionData.put("averageLatency", Math.round(quality.getAverageLatency() * 10.0) / 10.0);
                sessionData.put("latencyJitter", Math.round(quality.getLatencyJitter() * 10.0) / 10.0);
                sessionData.put("connectionStability", quality.getConnectionStability());
                sessionData.put("recommendedFPS", quality.getRecommendedFPS());
                sessionData.put("recommendedQuality", quality.getRecommendedQuality());
                sessionData.put("lastAssessment", quality.getLastAssessment().toString());
                sessionData.put("assessmentCount", quality.getAssessmentCount());
                sessionData.put("assessmentReason", quality.getAssessmentReason());
                sessionData.put("requiresAdaptation", quality.requiresAdaptation());

                sessions.put(sessionId, sessionData);
            }

            response.put("sessions", sessions);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting network quality", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get network quality");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get latency information for all sessions
     * 
     * @return Detailed latency measurements for all connected viewers
     */
    @GetMapping("/api/latency")
    public ResponseEntity<Map<String, Object>> getLatencyInfo() {
        try {
            Map<String, Object> response = new HashMap<>();

            // Get all session latencies from WebSocket handler
            Map<String, ScreenShareWebSocketHandler.LatencyData> sessionLatencies = webSocketHandler
                    .getAllSessionLatencies();
            Map<String, Object> sessions = new HashMap<>();

            double totalLatency = 0.0;
            int connectedSessions = 0;

            for (Map.Entry<String, ScreenShareWebSocketHandler.LatencyData> entry : sessionLatencies.entrySet()) {
                String sessionId = entry.getKey();
                ScreenShareWebSocketHandler.LatencyData latencyData = entry.getValue();

                if (latencyData.isConnected() && latencyData.getMeasurementCount() > 0) {
                    Map<String, Object> sessionData = new HashMap<>();
                    sessionData.put("connected", latencyData.isConnected());
                    sessionData.put("lastLatency", latencyData.getLastLatency());
                    sessionData.put("averageLatency", Math.round(latencyData.getAverageLatency() * 10.0) / 10.0);
                    sessionData.put("minLatency", latencyData.getMinLatency());
                    sessionData.put("maxLatency", latencyData.getMaxLatency());
                    sessionData.put("measurementCount", latencyData.getMeasurementCount());
                    sessionData.put("timeSinceLastPing", System.currentTimeMillis() - latencyData.getLastPingTime());

                    sessions.put(sessionId, sessionData);

                    totalLatency += latencyData.getAverageLatency();
                    connectedSessions++;
                }
            }

            response.put("sessions", sessions);
            response.put("totalSessions", connectedSessions);
            response.put("overallAverageLatency",
                    connectedSessions > 0 ? Math.round((totalLatency / connectedSessions) * 10.0) / 10.0 : 0.0);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting latency information", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get latency information");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Update streaming settings based on network quality recommendations
     * 
     * @return Updated streaming configuration
     */
    @GetMapping("/api/adaptive-settings")
    public ResponseEntity<Map<String, Object>> getAdaptiveSettings() {
        try {
            Map<String, Object> response = new HashMap<>();

            // Get network quality summary for recommendations
            NetworkQualityService.NetworkQualitySummary summary = networkQualityService.getNetworkSummary();

            Map<String, Object> currentSettings = new HashMap<>();
            currentSettings.put("currentFPS", screenCaptureService.getFrameRate());
            currentSettings.put("currentQuality", screenCaptureService.getJpegQuality());
            currentSettings.put("ultraFastMode", screenCaptureService.isUltraFastMode());
            currentSettings.put("zeroLatencyMode", screenCaptureService.isZeroLatencyMode());

            Map<String, Object> recommendedSettings = new HashMap<>();
            recommendedSettings.put("recommendedFPS", summary.getRecommendedFPS());
            recommendedSettings.put("recommendedQuality", summary.getRecommendedQuality());
            recommendedSettings.put("shouldAdapt", summary.shouldAdaptStreaming());
            recommendedSettings.put("qualityLevel", summary.getDominantQuality().getDisplayName());
            recommendedSettings.put("averageLatency", Math.round(summary.getOverallAverageLatency() * 10.0) / 10.0);

            // Calculate adaptation suggestions
            Map<String, Object> adaptationSuggestions = new HashMap<>();
            boolean needsAdaptation = false;

            if (summary.getRecommendedFPS() < screenCaptureService.getFrameRate()) {
                adaptationSuggestions.put("reduceFPS", true);
                adaptationSuggestions.put("suggestedFPS", summary.getRecommendedFPS());
                needsAdaptation = true;
            }

            if (summary.getRecommendedQuality() < screenCaptureService.getJpegQuality()) {
                adaptationSuggestions.put("reduceQuality", true);
                adaptationSuggestions.put("suggestedQuality", summary.getRecommendedQuality());
                needsAdaptation = true;
            }

            if (summary.shouldAdaptStreaming() && !screenCaptureService.isUltraFastMode()) {
                adaptationSuggestions.put("enableUltraFast", true);
                needsAdaptation = true;
            }

            adaptationSuggestions.put("needsAdaptation", needsAdaptation);

            response.put("currentSettings", currentSettings);
            response.put("recommendedSettings", recommendedSettings);
            response.put("adaptationSuggestions", adaptationSuggestions);
            response.put("networkQuality", summary.getDominantQuality().getDisplayName());
            response.put("activeConnections", summary.getActiveConnections());
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting adaptive settings", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get adaptive settings");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get adaptive streaming status and statistics
     * 
     * @return Adaptive streaming configuration and performance data
     */
    @GetMapping("/api/adaptive-streaming")
    public ResponseEntity<Map<String, Object>> getAdaptiveStreamingStatus() {
        try {
            Map<String, Object> response = new HashMap<>();

            // Get adaptation statistics
            AdaptiveStreamingService.AdaptationStats stats = adaptiveStreamingService.getAdaptationStats();

            Map<String, Object> adaptiveData = new HashMap<>();
            adaptiveData.put("enabled", stats.isEnabled());
            adaptiveData.put("initialized", stats.isInitialized());
            adaptiveData.put("adaptationCount", stats.getAdaptationCount());
            adaptiveData.put("lastAdaptationTime", stats.getLastAdaptationTime());
            adaptiveData.put("timeSinceLastAdaptation", stats.getTimeSinceLastAdaptation());
            adaptiveData.put("lastQualityLevel", stats.getLastQualityLevel().getDisplayName());
            adaptiveData.put("consecutiveAssessments", stats.getConsecutiveAssessments());
            adaptiveData.put("stabilityThreshold", stats.getStabilityThreshold());

            response.put("adaptiveStreaming", adaptiveData);

            // Include current network quality for context
            NetworkQualityService.NetworkQualitySummary summary = networkQualityService.getNetworkSummary();
            Map<String, Object> currentQuality = new HashMap<>();
            currentQuality.put("dominantQuality", summary.getDominantQuality().getDisplayName());
            currentQuality.put("averageLatency", Math.round(summary.getOverallAverageLatency() * 10.0) / 10.0);
            currentQuality.put("activeConnections", summary.getActiveConnections());
            currentQuality.put("shouldAdapt", summary.shouldAdaptStreaming());

            response.put("currentNetworkQuality", currentQuality);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting adaptive streaming status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get adaptive streaming status");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Force an immediate adaptive streaming assessment
     * 
     * @return Result of the forced adaptation
     */
    @PostMapping("/api/adaptive-streaming/force-adaptation")
    public ResponseEntity<Map<String, Object>> forceAdaptation() {
        try {
            Map<String, Object> response = new HashMap<>();

            // Get stats before adaptation
            AdaptiveStreamingService.AdaptationStats statsBefore = adaptiveStreamingService.getAdaptationStats();

            if (!statsBefore.isEnabled()) {
                response.put("success", false);
                response.put("message", "Adaptive streaming is disabled");
                return ResponseEntity.ok(response);
            }

            if (!statsBefore.isInitialized()) {
                response.put("success", false);
                response.put("message", "Adaptive streaming service not initialized");
                return ResponseEntity.ok(response);
            }

            // Force adaptation
            adaptiveStreamingService.forceAdaptation();

            // Give it a moment to process
            Thread.sleep(1000);

            // Get stats after adaptation
            AdaptiveStreamingService.AdaptationStats statsAfter = adaptiveStreamingService.getAdaptationStats();

            response.put("success", true);
            response.put("message", "Forced adaptation completed");
            response.put("adaptationCountBefore", statsBefore.getAdaptationCount());
            response.put("adaptationCountAfter", statsAfter.getAdaptationCount());
            response.put("adaptationTriggered", statsAfter.getAdaptationCount() > statsBefore.getAdaptationCount());
            response.put("currentQualityLevel", statsAfter.getLastQualityLevel().getDisplayName());
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error forcing adaptive streaming assessment", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to force adaptation");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Enable or disable adaptive streaming
     * 
     * @param enabled Whether to enable adaptive streaming
     * @return Updated adaptive streaming status
     */
    @PostMapping("/api/adaptive-streaming/toggle")
    public ResponseEntity<Map<String, Object>> toggleAdaptiveStreaming(@RequestParam boolean enabled) {
        try {
            Map<String, Object> response = new HashMap<>();

            adaptiveStreamingService.setAdaptiveStreamingEnabled(enabled);

            // Get updated stats
            AdaptiveStreamingService.AdaptationStats stats = adaptiveStreamingService.getAdaptationStats();

            response.put("success", true);
            response.put("message", String.format("Adaptive streaming %s", enabled ? "enabled" : "disabled"));
            response.put("enabled", stats.isEnabled());
            response.put("initialized", stats.isInitialized());
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error toggling adaptive streaming", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to toggle adaptive streaming");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Global exception handler for this controller
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        logger.error("Unhandled exception in ScreenSharingApiController", e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "An unexpected error occurred");
        response.put("timestamp", System.currentTimeMillis());
        response.put("errorType", "CONTROLLER_EXCEPTION");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
