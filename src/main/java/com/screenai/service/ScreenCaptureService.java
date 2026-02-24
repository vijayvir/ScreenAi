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

import jakarta.annotation.PreDestroy;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.screenai.handler.ReactiveScreenShareHandler;

/**
 * Screen Capture Service using JavaCV for real screen capture.
 * Implements FFmpegFrameGrabber for platform-specific screen capture and
 * broadcasts frames to all connected WebSocket sessions via ReactiveScreenShareHandler.
 */
@Service
public class ScreenCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(ScreenCaptureService.class);
    private static final int FRAME_RATE = 30; // 30 FPS for smoother streaming

    @Autowired
    private ReactiveScreenShareHandler screenShareHandler;

    private FFmpegFrameGrabber frameGrabber;
    private Java2DFrameConverter converter;
    private ScheduledExecutorService scheduler;
    private boolean isInitialized = false;
    private boolean isCapturing = false;
    private Rectangle screenRect;

    /**
     * Initializes JavaCV screen capture with platform-specific FFmpeg formats.
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
     * Initializes platform-specific screen capture using FFmpeg.
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
                    frameGrabber = new FFmpegFrameGrabber("Capture screen 0");
                    frameGrabber.setFormat("avfoundation");
                    frameGrabber.setFrameRate(FRAME_RATE);
                    logger.info("macOS avfoundation desktop capture configured");
                } catch (Exception e1) {
                    logger.debug("Primary macOS capture failed: {}", e1.getMessage());
                    try {
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

            // Set common low-latency options
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
     * Starts continuous screen capture and broadcasting to connected clients.
     */
    public void startCapture() {
        if (!isInitialized || isCapturing) {
            logger.warn("Cannot start capture - not initialized or already capturing");
            return;
        }

        isCapturing = true;
        scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleWithFixedDelay(() -> {
            long startTime = System.nanoTime();
            try {
                String frameData = captureScreenAsBase64();
                if (frameData != null) {
                    screenShareHandler.broadcastScreenFrame(frameData);
                    logger.debug("Screen frame broadcasted successfully (using JavaCV)");
                } else {
                    logger.warn("No screen frame data to broadcast");
                }
            } catch (Exception e) {
                logger.error("Error during screen capture broadcast", e);
            }
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.debug("Frame latency: {} ms", latencyMs);
        }, 0, 1000 / FRAME_RATE, TimeUnit.MILLISECONDS);

        logger.info("Screen capture started at {} FPS (using JavaCV)", FRAME_RATE);
    }

    /**
     * Stops continuous screen capture.
     */
    public void stopCapture() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException ie) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            isCapturing = false;
            logger.info("Screen capture stopped");
        }
    }

    /**
     * Captures the current screen and returns it as a Base64 encoded JPEG string.
     * @return Base64 encoded JPEG image of the screen, or null on failure
     */
    public String captureScreenAsBase64() {
        try {
            BufferedImage screenCapture = captureScreen();
            if (screenCapture == null) {
                return null;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenCapture, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            return Base64.getEncoder().encodeToString(imageBytes);

        } catch (IOException e) {
            logger.error("Failed to capture screen", e);
            return null;
        }
    }

    /**
     * Captures the current screen as a BufferedImage.
     * @return BufferedImage of the current screen, or null if not initialized
     */
    public BufferedImage captureScreen() {
        if (!isInitialized) {
            logger.debug("Screen capture not initialized");
            return null;
        }
        return captureScreenWithJavaCV();
    }

    private BufferedImage captureScreenWithJavaCV() {
        if (frameGrabber == null) {
            return null;
        }

        try {
            Frame frame = frameGrabber.grab();
            if (frame != null && frame.image != null) {
                BufferedImage bufferedImage = converter.convert(frame);
                logger.debug("JavaCV screen captured: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());
                return bufferedImage;
            }
            return null;
        } catch (Exception e) {
            logger.error("JavaCV screen capture failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Captures screen as a JavaCV Frame for advanced processing.
     * @return org.bytedeco.javacv.Frame object, or null if not initialized
     */
    public Frame captureScreenAsFrame() {
        if (!isInitialized) {
            return null;
        }
        if (frameGrabber == null) {
            return null;
        }
        try {
            return frameGrabber.grab();
        } catch (Exception e) {
            logger.error("JavaCV frame capture failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the screen dimensions.
     * @return Rectangle representing screen bounds
     */
    public Rectangle getScreenBounds() {
        return screenRect;
    }

    /**
     * Gets the JavaCV frame converter.
     * @return Java2DFrameConverter instance
     */
    public Java2DFrameConverter getConverter() {
        return converter;
    }

    /**
     * Checks if screen capture is properly initialized.
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Checks if screen capture is currently running.
     */
    public boolean isCapturing() {
        return isCapturing;
    }

    /**
     * Gets the current capture method being used.
     */
    public String getCaptureMethod() {
        if (!isInitialized) {
            return "Not initialized";
        }
        return "JavaCV FFmpeg";
    }

    /**
     * Releases all JavaCV resources. Called automatically on application shutdown.
     */
    @PreDestroy
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
