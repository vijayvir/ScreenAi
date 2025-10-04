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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.screenai.handler.ScreenShareWebSocketHandler;

import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.util.Iterator;

/**
 * Screen Capture Service using JavaCV for real screen capture
 * Implements FFmpegFrameGrabber for platform-specific screen capture
 * No AWT Robot fallback - JavaCV only implementation
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

    @Autowired
    private ScreenShareWebSocketHandler webSocketHandler;

    private FFmpegFrameGrabber frameGrabber;
    private Java2DFrameConverter converter;
    private ScheduledExecutorService scheduler;
    private boolean isInitialized = false;
    private boolean isCapturing = false;
    private Rectangle screenRect;

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
                    logger.debug("Screen frame broadcasted successfully (using JavaCV)");
                } else {
                    logger.warn("No screen frame data to broadcast");
                }
            } catch (Exception e) {
                logger.error("Error during screen capture broadcast", e);
            }
        }, 0, 1000 / frameRate, TimeUnit.MILLISECONDS);

        logger.info("Screen capture started at {} FPS (using JavaCV)", frameRate);
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
     * with configurable quality
     * 
     * @return Base64 encoded JPEG image of the screen
     */
    public String captureScreenAsBase64() {
        try {
            BufferedImage screenCapture = captureScreen();
            if (screenCapture == null) {
                return null;
            }

            // Convert to JPEG with configurable quality and encode as Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeJPEGWithQuality(screenCapture, baos, jpegQuality);
            byte[] imageBytes = baos.toByteArray();

            return Base64.getEncoder().encodeToString(imageBytes);

        } catch (IOException e) {
            logger.error("Failed to capture screen", e);
            return null;
        }
    }

    /**
     * Writes BufferedImage as JPEG with specified quality
     * 
     * @param image   The BufferedImage to write
     * @param output  The output stream to write to
     * @param quality Quality level (0-100, where 100 is highest quality)
     */
    private void writeJPEGWithQuality(BufferedImage image, ByteArrayOutputStream output, int quality)
            throws IOException {
        // Validate quality range
        quality = Math.max(30, Math.min(100, quality)); // Clamp between 30-100

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality / 100.0f); // Convert to 0.0-1.0 range
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        logger.debug("JPEG encoded with quality: {}%", quality);
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
     * Gets the current FPS setting
     * 
     * @return Current frames per second
     */
    public int getCurrentFPS() {
        return frameRate;
    }

    /**
     * Gets the current JPEG quality setting
     * 
     * @return Current JPEG quality (30-100)
     */
    public int getCurrentJPEGQuality() {
        return jpegQuality;
    }

    /**
     * Gets performance optimization status
     * 
     * @return String describing current optimizations
     */
    public String getOptimizationStatus() {
        return String.format("UltraFast: %s, ZeroLatency: %s, FPS: %d, Quality: %d%%",
                ultraFastMode, zeroLatencyMode, frameRate, jpegQuality);
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
