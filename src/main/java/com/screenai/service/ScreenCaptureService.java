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
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.util.Iterator;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.screenai.handler.ScreenShareWebSocketHandler;

/**
 * Screen Capture Service using JavaCV for real screen capture
 * Implements FFmpegFrameGrabber for platform-specific screen capture
 */
@Service
public class ScreenCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(ScreenCaptureService.class);

    @Value("${screenai.capture.fps:15}")
    private int frameRate;

    @Value("${screenai.capture.jpeg.quality:70}")
    private int jpegQuality;

    @Value("${screenai.capture.optimize.ultrafast:true}")
    private boolean ultraFastMode;

    @Value("${screenai.capture.optimize.zerolatency:true}")
    private boolean zeroLatencyMode;

    @Value("${screenai.capture.circuit-breaker.failure-threshold:10}")
    private int failureThreshold;

    @Value("${screenai.capture.circuit-breaker.timeout:5000}")
    private int circuitBreakerTimeout;

    @Autowired
    private ScreenShareWebSocketHandler webSocketHandler;

    @Autowired
    private NetworkQualityService networkQualityService;

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

    // Adaptive streaming parameters
    private int currentFPS;
    private int currentQuality;
    private long lastQualityCheck = 0;
    private static final long QUALITY_CHECK_INTERVAL = 10000; // 10 seconds

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

                    // Initialize adaptive streaming parameters
                    currentFPS = frameRate;
                    currentQuality = jpegQuality;

                    logger.info("JavaCV FFmpegFrameGrabber initialized successfully for screen capture");
                    logger.info("Initial adaptive settings: FPS={}, Quality={}%", currentFPS, currentQuality);
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
                frameGrabber.setFrameRate(frameRate);
                logger.info("Windows gdigrab screen capture configured");

            } else if (osName.contains("mac")) {
                // macOS: Try multiple approaches
                try {
                    // First attempt: Use desktop capture
                    frameGrabber = new FFmpegFrameGrabber("Capture screen 0");
                    frameGrabber.setFormat("avfoundation");
                    frameGrabber.setFrameRate(frameRate);
                    logger.info("macOS avfoundation desktop capture configured");
                } catch (Exception e1) {
                    logger.debug("Primary macOS capture failed: {}", e1.getMessage());
                    try {
                        // Second attempt: Use desktop index
                        frameGrabber = new FFmpegFrameGrabber("1:none");
                        frameGrabber.setFormat("avfoundation");
                        frameGrabber.setFrameRate(frameRate);
                        logger.info("macOS avfoundation index capture configured");
                    } catch (Exception e2) {
                        logger.debug("Secondary macOS capture failed: {}", e2.getMessage());
                        frameGrabber = null;
                    }
                }

            } else if (osName.contains("linux")) {
                // Linux: Use x11grab
                String display = System.getenv("DISPLAY");
                if (display == null)
                    display = ":0.0";

                frameGrabber = new FFmpegFrameGrabber(display);
                frameGrabber.setFormat("x11grab");
                frameGrabber.setImageWidth(screenRect.width);
                frameGrabber.setImageHeight(screenRect.height);
                frameGrabber.setFrameRate(frameRate);
                logger.info("Linux x11grab screen capture configured for display: {}", display);

            } else {
                logger.warn("Unsupported operating system for JavaCV screen capture: {}", osName);
                frameGrabber = null;
                return;
            }

            // Set common options if frameGrabber is available
            if (frameGrabber != null) {
                try {
                    if (ultraFastMode) {
                        frameGrabber.setOption("preset", "ultrafast");
                    }
                    if (zeroLatencyMode) {
                        frameGrabber.setOption("tune", "zerolatency");
                    }
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
     * Starts continuous screen capture and broadcasting with adaptive streaming
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
                // Check and adapt streaming quality based on network conditions
                adaptStreamingQuality();

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
        }, 0, 1000 / currentFPS, TimeUnit.MILLISECONDS);

        logger.info("Screen capture started at {} FPS (using JavaCV)", currentFPS);
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
     * Captures the current screen and returns it as a Base64 encoded JPEG string
     * with circuit breaker
     * 
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

            // Convert to JPEG with adaptive quality control and encode as Base64
            baos = new ByteArrayOutputStream();
            float qualityFloat = currentQuality / 100.0f; // Use adaptive quality
            if (!writeJPEGWithQuality(screenCapture, baos, qualityFloat)) {
                logger.warn("Failed to write screenshot to output stream with quality {}", currentQuality);
                handleCaptureFailure();
                return null;
            }

            byte[] imageBytes = baos.toByteArray();
            String result = Base64.getEncoder().encodeToString(imageBytes);

            // Reset on success
            consecutiveFailures = 0;
            circuitBreakerOpen = false;

            return result;

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
     * 
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
     * 
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
     * 
     * @return Rectangle representing screen bounds
     */
    public Rectangle getScreenBounds() {
        return screenRect;
    }

    /**
     * Gets the JavaCV frame converter
     * 
     * @return Java2DFrameConverter instance
     */
    public Java2DFrameConverter getConverter() {
        return converter;
    }

    /**
     * Checks if screen capture is properly initialized
     * 
     * @return true if screen capture is working
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Checks if screen capture is currently running
     * 
     * @return true if actively capturing and broadcasting
     */
    public boolean isCapturing() {
        return isCapturing;
    }

    /**
     * Gets the current capture method being used
     * 
     * @return String describing the capture method
     */
    public String getCaptureMethod() {
        if (!isInitialized) {
            return "Not initialized";
        }
        return "JavaCV FFmpeg";
    }

    /**
     * Gets the current frame rate configuration
     * 
     * @return The frame rate in FPS
     */
    public int getFrameRate() {
        return frameRate;
    }

    /**
     * Gets the current JPEG quality configuration
     * 
     * @return The JPEG quality percentage (30-100)
     */
    public int getJpegQuality() {
        return jpegQuality;
    }

    /**
     * Gets the ultra fast mode configuration
     * 
     * @return true if ultra fast mode is enabled
     */
    public boolean isUltraFastMode() {
        return ultraFastMode;
    }

    /**
     * Gets the zero latency mode configuration
     * 
     * @return true if zero latency mode is enabled
     */
    public boolean isZeroLatencyMode() {
        return zeroLatencyMode;
    }

    /**
     * Writes a BufferedImage as JPEG with specified quality
     * 
     * @param image        The image to write
     * @param outputStream The output stream to write to
     * @param quality      The JPEG quality (0.0 to 1.0)
     * @return true if successful, false otherwise
     */
    private boolean writeJPEGWithQuality(BufferedImage image, ByteArrayOutputStream outputStream, float quality) {
        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                logger.warn("No JPEG writers available");
                return false;
            }

            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
                writer.dispose();
                return true;
            }
        } catch (IOException e) {
            logger.error("Error writing JPEG with quality: {}", e.getMessage());
            return false;
        }
    }

    // ==================== ADAPTIVE STREAMING METHODS ====================

    /**
     * Adapt streaming quality based on network conditions
     */
    private void adaptStreamingQuality() {
        long currentTime = System.currentTimeMillis();

        // Only check quality periodically to avoid excessive overhead
        if (currentTime - lastQualityCheck < QUALITY_CHECK_INTERVAL) {
            return;
        }

        lastQualityCheck = currentTime;

        try {
            // Get network quality summary from the NetworkQualityService
            var qualitySummary = networkQualityService.getNetworkSummary();

            if (qualitySummary.getActiveConnections() == 0) {
                // No active connections, use default settings
                return;
            }

            // Get recommended settings based on network quality
            int recommendedFPS = qualitySummary.getRecommendedFPS();
            int recommendedQuality = qualitySummary.getRecommendedQuality();

            // Apply adaptive changes if needed
            boolean fpsChanged = applyFPSAdaptation(recommendedFPS,
                    qualitySummary.getDominantQuality().getDisplayName());
            boolean qualityChanged = applyQualityAdaptation(recommendedQuality,
                    qualitySummary.getDominantQuality().getDisplayName());

            if (fpsChanged || qualityChanged) {
                logger.info("Adaptive streaming applied: FPS={}, Quality={}% (Network: {}, Latency: {:.1f}ms)",
                        currentFPS, currentQuality,
                        qualitySummary.getDominantQuality().getDisplayName(),
                        qualitySummary.getOverallAverageLatency());

                // Restart capture with new settings if FPS changed
                if (fpsChanged && isCapturing) {
                    restartCaptureWithNewFPS();
                }
            }

        } catch (Exception e) {
            logger.error("Error during adaptive streaming quality check: {}", e.getMessage());
        }
    }

    /**
     * Apply FPS adaptation based on network quality
     */
    private boolean applyFPSAdaptation(int recommendedFPS, String networkQuality) {
        if (currentFPS == recommendedFPS) {
            return false;
        }

        // Apply gradual FPS changes to avoid jarring transitions
        int targetFPS = recommendedFPS;
        int fpsDiff = Math.abs(currentFPS - targetFPS);

        // Limit FPS changes to 2 FPS per adaptation cycle for smooth transitions
        if (fpsDiff > 2) {
            if (currentFPS > targetFPS) {
                targetFPS = currentFPS - 2;
            } else {
                targetFPS = currentFPS + 2;
            }
        }

        // Ensure FPS is within reasonable bounds
        targetFPS = Math.max(3, Math.min(30, targetFPS));

        if (currentFPS != targetFPS) {
            int oldFPS = currentFPS;
            currentFPS = targetFPS;
            logger.debug("FPS adapted: {} → {} (Target: {}, Network: {})",
                    oldFPS, currentFPS, recommendedFPS, networkQuality);
            return true;
        }

        return false;
    }

    /**
     * Apply quality adaptation based on network conditions
     */
    private boolean applyQualityAdaptation(int recommendedQuality, String networkQuality) {
        if (currentQuality == recommendedQuality) {
            return false;
        }

        // Apply gradual quality changes
        int targetQuality = recommendedQuality;
        int qualityDiff = Math.abs(currentQuality - targetQuality);

        // Limit quality changes to 10% per adaptation cycle
        if (qualityDiff > 10) {
            if (currentQuality > targetQuality) {
                targetQuality = currentQuality - 10;
            } else {
                targetQuality = currentQuality + 10;
            }
        }

        // Ensure quality is within bounds
        targetQuality = Math.max(20, Math.min(100, targetQuality));

        if (currentQuality != targetQuality) {
            int oldQuality = currentQuality;
            currentQuality = targetQuality;
            logger.debug("Quality adapted: {}% → {}% (Target: {}%, Network: {})",
                    oldQuality, currentQuality, recommendedQuality, networkQuality);
            return true;
        }

        return false;
    }

    /**
     * Restart capture with new FPS setting
     */
    private void restartCaptureWithNewFPS() {
        if (!isCapturing) {
            return;
        }

        try {
            // Stop current scheduler
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                scheduler.awaitTermination(1, TimeUnit.SECONDS);
            }

            // Restart with new FPS
            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    // Note: adaptStreamingQuality() is called from here, but we prevent
                    // recursive FPS changes by checking the time interval
                    adaptStreamingQuality();

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
            }, 0, 1000 / currentFPS, TimeUnit.MILLISECONDS);

            logger.debug("Capture restarted with new FPS: {}", currentFPS);

        } catch (Exception e) {
            logger.error("Error restarting capture with new FPS: {}", e.getMessage());
        }
    }

    /**
     * Get current adaptive streaming settings
     */
    public AdaptiveSettings getCurrentAdaptiveSettings() {
        return new AdaptiveSettings(currentFPS, currentQuality, frameRate, jpegQuality);
    }

    /**
     * Data structure for adaptive settings
     */
    public static class AdaptiveSettings {
        private final int currentFPS;
        private final int currentQuality;
        private final int baseFPS;
        private final int baseQuality;

        public AdaptiveSettings(int currentFPS, int currentQuality, int baseFPS, int baseQuality) {
            this.currentFPS = currentFPS;
            this.currentQuality = currentQuality;
            this.baseFPS = baseFPS;
            this.baseQuality = baseQuality;
        }

        public int getCurrentFPS() {
            return currentFPS;
        }

        public int getCurrentQuality() {
            return currentQuality;
        }

        public int getBaseFPS() {
            return baseFPS;
        }

        public int getBaseQuality() {
            return baseQuality;
        }

        public boolean isAdapted() {
            return currentFPS != baseFPS || currentQuality != baseQuality;
        }

        public String getAdaptationStatus() {
            if (!isAdapted()) {
                return "No adaptation";
            }
            return String.format("FPS: %d→%d, Quality: %d%%→%d%%",
                    baseFPS, currentFPS, baseQuality, currentQuality);
        }
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
