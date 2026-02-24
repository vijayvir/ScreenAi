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
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Screen Capture Service using JavaCV for real screen capture.
 * Implements FFmpegFrameGrabber for platform-specific screen capture.
 * Broadcasts captured frames via a reactive Flux for WebFlux consumers.
 *
 * Adapted from the WebRTC branch to use reactive Sinks instead of a
 * blocking WebSocket handler, making it compatible with Spring WebFlux.
 */
@Service
public class ScreenCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(ScreenCaptureService.class);
    private static final int FRAME_RATE = 30;
    /** Number of frames buffered in the reactive sink before back-pressure drops start. */
    private static final int FRAME_BUFFER_SIZE = 32;

    // Reactive sink for broadcasting Base64-encoded JPEG frames to all viewers
    private final Sinks.Many<String> frameSink =
            Sinks.many().multicast().onBackpressureBuffer(FRAME_BUFFER_SIZE, false);

    private FFmpegFrameGrabber frameGrabber;
    private Java2DFrameConverter converter;
    private ScheduledExecutorService scheduler;
    private volatile boolean isInitialized = false;
    private volatile boolean isCapturing = false;
    private Rectangle screenRect;

    /**
     * Returns a Flux of Base64-encoded JPEG frame data strings.
     * Subscribe to receive a continuous stream of screen frames.
     */
    public Flux<String> getFrameFlux() {
        return frameSink.asFlux();
    }

    /**
     * Initializes JavaCV screen capture with platform-specific FFmpeg formats.
     * Guards against headless environments (e.g., CI servers without a display).
     */
    public void initialize() {
        if (GraphicsEnvironment.isHeadless()) {
            logger.warn("Running in headless mode — screen capture not available");
            return;
        }

        try {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            screenRect = gd.getDefaultConfiguration().getBounds();
            logger.info("Detected screen resolution: {}x{}", screenRect.width, screenRect.height);

            converter = new Java2DFrameConverter();
            initializePlatformScreenCapture();

            if (frameGrabber != null) {
                try {
                    frameGrabber.start();
                    isInitialized = true;
                    logger.info("JavaCV FFmpegFrameGrabber initialized for screen capture");
                } catch (Exception e) {
                    logger.error("JavaCV screen capture failed to start: {}", e.getMessage());
                    throw new RuntimeException("Failed to initialize JavaCV screen capture", e);
                }
            } else {
                throw new RuntimeException("JavaCV screen capture not available for this platform");
            }

        } catch (Exception e) {
            logger.error("Failed to initialize screen capture: {}", e.getMessage());
            throw new RuntimeException("Screen capture initialization failed", e);
        }
    }

    /**
     * Configures platform-specific FFmpeg screen capture format.
     */
    private void initializePlatformScreenCapture() {
        String osName = System.getProperty("os.name").toLowerCase();
        logger.info("Initializing JavaCV screen capture for OS: {}", osName);

        try {
            if (osName.contains("windows")) {
                frameGrabber = new FFmpegFrameGrabber("desktop");
                frameGrabber.setFormat("gdigrab");
                frameGrabber.setImageWidth(screenRect.width);
                frameGrabber.setImageHeight(screenRect.height);
                frameGrabber.setFrameRate(FRAME_RATE);
                logger.info("Windows gdigrab screen capture configured");

            } else if (osName.contains("mac")) {
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
                String display = System.getenv("DISPLAY");
                if (display == null) display = ":0.0";
                frameGrabber = new FFmpegFrameGrabber(display);
                frameGrabber.setFormat("x11grab");
                frameGrabber.setImageWidth(screenRect.width);
                frameGrabber.setImageHeight(screenRect.height);
                frameGrabber.setFrameRate(FRAME_RATE);
                logger.info("Linux x11grab configured for display: {}", display);

            } else {
                logger.warn("Unsupported OS for JavaCV screen capture: {}", osName);
                frameGrabber = null;
            }

        } catch (Exception e) {
            logger.error("Error configuring platform screen capture: {}", e.getMessage());
            frameGrabber = null;
        }
    }

    /**
     * Starts the screen capture loop, emitting frames to the reactive sink.
     */
    public void startCapture() {
        if (!isInitialized || isCapturing) {
            logger.warn("Cannot start capture: initialized={}, capturing={}", isInitialized, isCapturing);
            return;
        }

        isCapturing = true;
        long captureInterval = 1000L / FRAME_RATE;

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String frameData = captureScreenAsBase64();
                if (frameData != null) {
                    frameSink.tryEmitNext(frameData);
                }
            } catch (Exception e) {
                logger.error("Error during frame capture: {}", e.getMessage());
            }
        }, 0, captureInterval, TimeUnit.MILLISECONDS);

        logger.info("Screen capture started at {} FPS (interval: {}ms)", FRAME_RATE, captureInterval);
    }

    /**
     * Stops the screen capture loop.
     */
    public void stopCapture() {
        isCapturing = false;

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        logger.info("Screen capture stopped");
    }

    /**
     * Captures the current screen as a Base64-encoded JPEG string.
     *
     * @return Base64-encoded JPEG string, or null if capture failed
     */
    public String captureScreenAsBase64() {
        BufferedImage screenCapture = captureScreenWithJavaCV();
        if (screenCapture == null) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(screenCapture, "jpg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            logger.error("Failed to encode screen capture: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Captures the current screen as a BufferedImage.
     *
     * @return BufferedImage of the current screen, or null if not available
     */
    public BufferedImage captureScreen() {
        if (!isInitialized) {
            return null;
        }
        return captureScreenWithJavaCV();
    }

    /**
     * Captures a screen frame using JavaCV FFmpegFrameGrabber.
     */
    private BufferedImage captureScreenWithJavaCV() {
        if (frameGrabber == null) {
            return null;
        }

        try {
            Frame frame = frameGrabber.grab();
            if (frame != null && frame.image != null) {
                return converter.convert(frame);
            }
        } catch (Exception e) {
            logger.error("JavaCV screen capture failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Captures the current screen as a JavaCV Frame.
     */
    public Frame captureScreenAsFrame() {
        if (!isInitialized || frameGrabber == null) {
            return null;
        }

        try {
            return frameGrabber.grab();
        } catch (Exception e) {
            logger.error("JavaCV frame capture failed: {}", e.getMessage());
            return null;
        }
    }

    /** @return Screen bounds, or null if not initialized */
    public Rectangle getScreenBounds() {
        return screenRect;
    }

    /** @return The Java2DFrameConverter instance */
    public Java2DFrameConverter getConverter() {
        return converter;
    }

    /** @return true if screen capture has been successfully initialized */
    public boolean isInitialized() {
        return isInitialized;
    }

    /** @return true if the capture loop is currently running */
    public boolean isCapturing() {
        return isCapturing;
    }

    /** @return Description of the active capture method */
    public String getCaptureMethod() {
        return isInitialized ? "JavaCV FFmpeg" : "Not initialized";
    }

    /**
     * Releases all JavaCV resources.  Call on application shutdown.
     */
    public void cleanup() {
        stopCapture();

        if (frameGrabber != null) {
            try {
                frameGrabber.stop();
                frameGrabber.release();
                logger.info("JavaCV FFmpegFrameGrabber resources released");
            } catch (Exception e) {
                logger.warn("Error during JavaCV cleanup: {}", e.getMessage());
            }
        }

        logger.info("Screen capture service cleaned up");
    }
}
