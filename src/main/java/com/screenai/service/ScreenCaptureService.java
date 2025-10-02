package com.screenai.service;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.screenai.handler.ScreenShareWebSocketHandler;

/**
 * Screen Capture Service using JavaCV  for real screen capture
 * Implements FFmpegFrameGrabber for platform-specific screen capture
 */
@Service
public class ScreenCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(ScreenCaptureService.class);
    private static final int FRAME_RATE = 10; // 10 FPS for balance between performance and smoothness

    @Autowired
    private ScreenShareWebSocketHandler webSocketHandler;

    private FFmpegFrameGrabber frameGrabber;
    private Java2DFrameConverter converter;
    private ScheduledExecutorService scheduler;
    private boolean isInitialized = false;
    private boolean isCapturing = false;
    private Rectangle screenRect;
    
    // Circuit breaker pattern for failed captures
    private int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 10;
    private boolean circuitBreakerOpen = false;
    private long circuitBreakerOpenTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 30000; // 30 seconds

    /**
     * Initializes JavaCV screen capture with platform-specific FFmpeg formats
     */
    public void initialize() {
        try {
            // Get screen dimensions
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            screenRect = gd.getDefaultConfiguration().getBounds();
            
            logger.info("Detected screen resolution: {}x{}", screenRect.width, screenRect.height);
            
            // Initialize JavaCV Frame Converter
            converter = new Java2DFrameConverter();
            
            // Initialize JavaCV screen capture
            initializePlatformScreenCapture();
            
            if (frameGrabber != null) {
                try {
                    frameGrabber.start();
                    isInitialized = true;
                    logger.info("JavaCV FFmpegFrameGrabber initialized successfully for screen capture");
                } catch (Exception e) {
                    logger.error("JavaCV screen capture failed to start: {}", e.getMessage());
                    throw new RuntimeException("Failed to initialize JavaCV screen capture", e);
                }
            } else {
                throw new RuntimeException("JavaCV screen capture not available for this platform");
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize JavaCV screen capture", e);
            throw new RuntimeException("Screen capture initialization failed", e);
        }
    }
    
    /**
     * Initializes platform-specific screen capture using FFmpeg
     */
    private void initializePlatformScreenCapture() {
        String osName = System.getProperty("os.name").toLowerCase();
        logger.info("Initializing JavaCV screen capture for OS: {}", osName);
        
        try {
            if (osName.contains("windows")) {
                // Windows: Use gdigrab
                frameGrabber = new FFmpegFrameGrabber("desktop");
                frameGrabber.setFormat("gdigrab");
                frameGrabber.setImageWidth(screenRect.width);
                frameGrabber.setImageHeight(screenRect.height);
                frameGrabber.setFrameRate(FRAME_RATE);
                logger.info("Windows gdigrab screen capture configured");
                
            } else if (osName.contains("mac")) {
                // macOS: Try multiple approaches
                try {
                    // First attempt: Use desktop capture
                    frameGrabber = new FFmpegFrameGrabber("Capture screen 0");
                    frameGrabber.setFormat("avfoundation");
                    frameGrabber.setFrameRate(FRAME_RATE);
                    logger.info("macOS avfoundation desktop capture configured");
                } catch (Exception e1) {
                    logger.debug("Primary macOS capture failed: {}", e1.getMessage());
                    try {
                        // Second attempt: Use desktop index
                        frameGrabber = new FFmpegFrameGrabber("1:none");
                        frameGrabber.setFormat("avfoundation");
                        frameGrabber.setFrameRate(FRAME_RATE);
                        logger.info("macOS avfoundation index capture configured");
                    } catch (Exception e2) {
                        logger.debug("Secondary macOS capture failed: {}", e2.getMessage());
                        frameGrabber = null;
                    }
                }
                
            } else if (osName.contains("linux")) {
                // Linux: Use x11grab
                String display = System.getenv("DISPLAY");
                if (display == null) display = ":0.0";
                
                frameGrabber = new FFmpegFrameGrabber(display);
                frameGrabber.setFormat("x11grab");
                frameGrabber.setImageWidth(screenRect.width);
                frameGrabber.setImageHeight(screenRect.height);
                frameGrabber.setFrameRate(FRAME_RATE);
                logger.info("Linux x11grab screen capture configured for display: {}", display);
                
            } else {
                logger.warn("Unsupported operating system for JavaCV screen capture: {}", osName);
                frameGrabber = null;
                return;
            }
            
            // Set common options if frameGrabber is available
            if (frameGrabber != null) {
                try {
                    frameGrabber.setOption("preset", "ultrafast");
                    frameGrabber.setOption("tune", "zerolatency");
                } catch (Exception optionError) {
                    logger.debug("Could not set FFmpeg options: {}", optionError.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.warn("Failed to configure JavaCV screen capture: {}", e.getMessage());
            frameGrabber = null;
        }
    }
    
    /**
     * Starts continuous screen capture and broadcasting
     */
    public void startCapture() {
        if (!isInitialized || isCapturing) {
            logger.warn("Cannot start capture - not initialized or already capturing");
            return;
        }
        
        isCapturing = true;
        scheduler = Executors.newScheduledThreadPool(1);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String frameData = captureScreenAsBase64();
                if (frameData != null) {
                    webSocketHandler.broadcastScreenFrame(frameData);
                    logger.info("Screen frame broadcasted successfully (frame size: {} chars)", frameData.length());
                } else {
                    logger.warn("No screen frame data to broadcast");
                }
            } catch (Exception e) {
                logger.error("Error during screen capture broadcast", e);
            }
        }, 0, 1000 / FRAME_RATE, TimeUnit.MILLISECONDS);
        
        logger.info("Screen capture started at {} FPS (using JavaCV)", FRAME_RATE);
    }
    
    /**
     * Stops continuous screen capture
     */
    public void stopCapture() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            isCapturing = false;
            logger.info("Screen capture stopped");
        }
    }
    
    /**
     * Captures the current screen and returns it as a Base64 encoded JPEG string with circuit breaker
     * @return Base64 encoded JPEG image of the screen
     */
    public String captureScreenAsBase64() {
        // Check circuit breaker
        if (circuitBreakerOpen) {
            if (System.currentTimeMillis() - circuitBreakerOpenTime > CIRCUIT_BREAKER_TIMEOUT) {
                logger.info("Circuit breaker timeout expired, attempting to close circuit");
                circuitBreakerOpen = false;
                consecutiveFailures = 0;
            } else {
                logger.debug("Circuit breaker is open, skipping capture attempt");
                return null;
            }
        }
        
        BufferedImage screenCapture = null;
        ByteArrayOutputStream baos = null;
        
        try {
            screenCapture = captureScreen();
            if (screenCapture == null) {
                handleCaptureFailure();
                return null;
            }
            
            // Convert to JPEG and encode as Base64
            baos = new ByteArrayOutputStream();
            if (!ImageIO.write(screenCapture, "jpg", baos)) {
                logger.warn("Failed to write screenshot to output stream");
                handleCaptureFailure();
                return null;
            }
            
            byte[] imageBytes = baos.toByteArray();
            String result = Base64.getEncoder().encodeToString(imageBytes);
            
            // Reset on success
            consecutiveFailures = 0;
            circuitBreakerOpen = false;
            
            return result;
            
        } catch (IOException e) {
            logger.error("IO error during screen capture: {}", e.getMessage());
            handleCaptureFailure();
            return null;
        } catch (OutOfMemoryError e) {
            logger.error("Out of memory during screen capture - consider reducing quality");
            handleCaptureFailure(); 
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error during screen capture: {}", e.getMessage(), e);
            handleCaptureFailure();
            return null;
        } finally {
            // Ensure resources are properly closed
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                    logger.debug("Error closing ByteArrayOutputStream: {}", e.getMessage());
                }
            }
            
            // Help GC with BufferedImage
            if (screenCapture != null) {
                screenCapture.flush();
            }
        }
    }
    
    private void handleCaptureFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            logger.error("Too many consecutive failures ({}), opening circuit breaker for {} seconds", 
                        consecutiveFailures, CIRCUIT_BREAKER_TIMEOUT / 1000);
            circuitBreakerOpen = true;
            circuitBreakerOpenTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Captures the current screen as a BufferedImage
     * @return BufferedImage of the current screen
     */
    public BufferedImage captureScreen() {
        if (!isInitialized) {
            logger.debug("Screen capture not initialized");
            return null;
        }
        
        return captureScreenWithJavaCV();
    }
    
    /**
     * Captures screen using JavaCV FFmpegFrameGrabber
     */
    private BufferedImage captureScreenWithJavaCV() {
        if (frameGrabber == null) {
            logger.debug("JavaCV frame grabber not available");
            return null;
        }
        
        try {
            // Capture frame using JavaCV FFmpegFrameGrabber
            Frame frame = frameGrabber.grab();
            if (frame != null && frame.image != null) {
                // Convert JavaCV Frame to BufferedImage
                BufferedImage bufferedImage = converter.convert(frame);
                logger.debug("JavaCV screen captured: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());
                return bufferedImage;
            } else {
                logger.debug("No frame captured from JavaCV");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("JavaCV screen capture failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Captures screen as JavaCV Frame for advanced processing
     * @return org.bytedeco.javacv.Frame object
     */
    public Frame captureScreenAsFrame() {
        if (!isInitialized) {
            return null;
        }
        
        return captureFrameWithJavaCV();
    }
    
    /**
     * Captures frame directly with JavaCV
     */
    private Frame captureFrameWithJavaCV() {
        if (frameGrabber == null) {
            return null;
        }
        
        try {
            Frame frame = frameGrabber.grab();
            return frame;
        } catch (Exception e) {
            logger.error("JavaCV frame capture failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the screen dimensions
     * @return Rectangle representing screen bounds
     */
    public Rectangle getScreenBounds() {
        return screenRect;
    }
    
    /**
     * Gets the JavaCV frame converter
     * @return Java2DFrameConverter instance
     */
    public Java2DFrameConverter getConverter() {
        return converter;
    }
    
    /**
     * Checks if screen capture is properly initialized
     * @return true if screen capture is working
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Checks if screen capture is currently running
     * @return true if actively capturing and broadcasting
     */
    public boolean isCapturing() {
        return isCapturing;
    }
    
    /**
     * Gets the current capture method being used
     * @return String describing the capture method
     */
    public String getCaptureMethod() {
        if (!isInitialized) {
            return "Not initialized";
        }
        return "JavaCV FFmpeg";
    }
    
    /**
     * Cleanup resources when service is destroyed
     */
    public void cleanup() {
        stopCapture();
        
        if (frameGrabber != null) {
            try {
                frameGrabber.stop();
                frameGrabber.release();
                logger.info("JavaCV FFmpegFrameGrabber resources cleaned up");
            } catch (Exception e) {
                logger.warn("Error during JavaCV cleanup: {}", e.getMessage());
            }
        }
        
        logger.info("Screen capture service cleaned up");
    }
}
