package com.screenai.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.screenai.service.ScreenCaptureService;

import reactor.core.publisher.Mono;

/**
 * REST API controller for screen-capture status and control.
 *
 * Adapted from the WebRTC branch for Spring WebFlux (reactive return types).
 */
@RestController
public class ScreenSharingApiController {

    private final ScreenCaptureService screenCaptureService;

    public ScreenSharingApiController(ScreenCaptureService screenCaptureService) {
        this.screenCaptureService = screenCaptureService;
    }

    /**
     * Returns current screen-capture status and statistics.
     */
    @GetMapping("/api/status")
    public Mono<ResponseEntity<Map<String, Object>>> getStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("initialized", screenCaptureService.isInitialized());
        status.put("capturing", screenCaptureService.isCapturing());
        status.put("captureMethod", screenCaptureService.getCaptureMethod());

        if (screenCaptureService.getScreenBounds() != null) {
            Map<String, Object> screen = new HashMap<>();
            screen.put("width", screenCaptureService.getScreenBounds().width);
            screen.put("height", screenCaptureService.getScreenBounds().height);
            status.put("screenResolution", screen);
        }

        status.put("serverTime", System.currentTimeMillis());
        status.put("osName", System.getProperty("os.name"));
        status.put("javaVersion", System.getProperty("java.version"));

        return Mono.just(ResponseEntity.ok(status));
    }

    /**
     * Starts screen capture (initializes first if needed).
     */
    @GetMapping("/api/start-capture")
    public Mono<ResponseEntity<Map<String, Object>>> startCapture() {
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

        return Mono.just(ResponseEntity.ok(response));
    }

    /**
     * Stops the running screen capture.
     */
    @GetMapping("/api/stop-capture")
    public Mono<ResponseEntity<Map<String, Object>>> stopCapture() {
        Map<String, Object> response = new HashMap<>();

        try {
            screenCaptureService.stopCapture();
            response.put("success", true);
            response.put("message", "Screen capture stopped");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error stopping capture: " + e.getMessage());
        }

        return Mono.just(ResponseEntity.ok(response));
    }
}
