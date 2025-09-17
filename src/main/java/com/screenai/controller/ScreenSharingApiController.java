package com.screenai.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.screenai.handler.ScreenShareWebSocketHandler;
import com.screenai.service.ScreenCaptureService;

/**
 * REST API Controller for screen sharing status and information
 */
@RestController
public class ScreenSharingApiController {

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
        Map<String, Object> status = new HashMap<>();
        
        // Basic status
        status.put("initialized", screenCaptureService.isInitialized());
        status.put("capturing", screenCaptureService.isCapturing());
        status.put("captureMethod", screenCaptureService.getCaptureMethod());
        
        // Screen information
        if (screenCaptureService.getScreenBounds() != null) {
            Map<String, Object> screen = new HashMap<>();
            screen.put("width", screenCaptureService.getScreenBounds().width);
            screen.put("height", screenCaptureService.getScreenBounds().height);
            status.put("screenResolution", screen);
        }
        
        // Viewer information
        status.put("viewerCount", webSocketHandler.getViewerCount());
        
        // System information
        status.put("serverTime", System.currentTimeMillis());
        status.put("osName", System.getProperty("os.name"));
        status.put("javaVersion", System.getProperty("java.version"));
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Start screen capture manually
     */
    @GetMapping("/api/start-capture")
    public ResponseEntity<Map<String, Object>> startCapture() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!screenCaptureService.isInitialized()) {
                screenCaptureService.initialize();
            }
            
            if (screenCaptureService.isInitialized() && !screenCaptureService.isCapturing()) {
                screenCaptureService.startCapture();
                response.put("success", true);
                response.put("message", "Screen capture started successfully");
                response.put("method", screenCaptureService.getCaptureMethod());
            } else if (screenCaptureService.isCapturing()) {
                response.put("success", true);
                response.put("message", "Screen capture already running");
                response.put("method", screenCaptureService.getCaptureMethod());
            } else {
                response.put("success", false);
                response.put("message", "Failed to initialize screen capture");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error starting capture: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop screen capture manually
     */
    @GetMapping("/api/stop-capture")
    public ResponseEntity<Map<String, Object>> stopCapture() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            screenCaptureService.stopCapture();
            response.put("success", true);
            response.put("message", "Screen capture stopped");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error stopping capture: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}
