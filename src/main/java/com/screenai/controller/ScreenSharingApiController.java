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
import org.springframework.web.bind.annotation.RestController;

import com.screenai.handler.ScreenShareWebSocketHandler;
import com.screenai.service.ScreenCaptureService;

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

    /**
     * Get current screen sharing status and statistics
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
