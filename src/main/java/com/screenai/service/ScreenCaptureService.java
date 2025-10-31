package com.screenai.service;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.screenai.encoder.VideoEncoderFactory;
import com.screenai.encoder.VideoEncoderStrategy;
import com.screenai.handler.ScreenShareWebSocketHandler;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.util.Iterator;

/**
 * Service for capturing the screen using JavaCV and streaming via WebSockets
 */
@Service
public class ScreenCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(ScreenCaptureService.class);

    private VideoEncoderStrategy currentEncoder;
    private int consecutiveFrameSkips = 0;
    private long lastFrameTime = 0;
    private static final int FRAME_RATE = 15; // 15 FPS for optimal balance

    @Autowired
    private ScreenShareWebSocketHandler webSocketHandler;

    @Autowired
    private PerformanceMonitorService performanceMonitor;

    // Configuration parameters
    @Value("${screen.capture.frame-rate:15}")
    private int frameRate = 15;

    @Value("${screen.capture.jpeg-quality:80}")
    private int jpegQuality = 80;

    @Value("${screen.capture.ultra-fast:true}")
    private boolean ultraFastMode = true;

    @Value("${screen.capture.zero-latency:true}")
    private boolean zeroLatencyMode = true;

    private FFmpegFrameGrabber frameGrabber;
    private FFmpegFrameRecorder recorder;
    private ScheduledExecutorService scheduler;
    private boolean isInitialized = false;
    private boolean isCapturing = false;
    private Rectangle screenRect;

    // Recorder state
    private volatile boolean recorderStarted = false;

    // ✅ Init segment caching for new viewers
    private byte[] initSegment = null;

    // ✅ Stream management
    private ByteArrayOutputStream videoStream;
    private volatile int lastSentPosition = 0;

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

            // Configure grabber only (do not start recorder yet)
            initializePlatformScreenCapture();

            if (frameGrabber != null) {
                frameGrabber.start();
                isInitialized = true;
                logger.info("JavaCV FFmpegFrameGrabber initialized successfully for screen capture");
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
                // macOS: Try modern labeled device first, then numeric fallbacks
                Exception firstError = null;
                try {
                    frameGrabber = new FFmpegFrameGrabber("Capture screen 0");
                    frameGrabber.setFormat("avfoundation");

                    frameGrabber.setImageWidth(screenRect.width);
                    frameGrabber.setImageHeight(screenRect.height);
                    frameGrabber.setFrameRate(FRAME_RATE);
                    logger.info("macOS avfoundation screen capture configured with 'Capture screen 0'");
                } catch (Exception e0) {
                    firstError = e0;
                    logger.debug("Primary macOS 'Capture screen 0' failed: {}", e0.getMessage());

                    try {
                        frameGrabber = new FFmpegFrameGrabber("0:none");
                        frameGrabber.setFormat("avfoundation");

                        frameGrabber.setImageWidth(screenRect.width);
                        frameGrabber.setImageHeight(screenRect.height);
                        frameGrabber.setFrameRate(FRAME_RATE);
                        logger.info("macOS avfoundation screen capture configured with '0:none'");
                    } catch (Exception e1) {
                        logger.debug("Secondary macOS '0:none' failed: {}", e1.getMessage());
                        try {
                            frameGrabber = new FFmpegFrameGrabber("1:none");
                            frameGrabber.setFormat("avfoundation");
                            frameGrabber.setImageWidth(screenRect.width);
                            frameGrabber.setImageHeight(screenRect.height);
                            frameGrabber.setFrameRate(FRAME_RATE);
                            logger.info("macOS avfoundation screen capture configured with '1:none'");
                        } catch (Exception e2) {
                            logger.warn("All macOS avfoundation configs failed: primary={}, secondary={}, tertiary={}",
                                    firstError != null ? firstError.getMessage() : "none", e1.getMessage(),
                                    e2.getMessage());
                            frameGrabber = null;
                        }

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
     * ✅ Initialize H.264 encoder with init segment extraction
     */
    private boolean initializeVideoRecorder() {
        try {
            // Use ByteArrayOutputStream instead of pipes
            videoStream = new ByteArrayOutputStream();
            lastSentPosition = 0;
            initSegment = null;
            recorderStarted = false;

            int targetWidth = screenRect.width;
            int targetHeight = screenRect.height;

            recorder = new FFmpegFrameRecorder(videoStream, targetWidth, targetHeight);

            // ✅ Use encoder factory to get best available encoder (GPU-accelerated when
            // possible)
            currentEncoder = VideoEncoderFactory.getBestEncoder();
            currentEncoder.configure(recorder);
            String codecName = currentEncoder.getCodecName();

            logger.info("✅ Selected encoder: {} (Hardware: {}, CPU reduction: {}%)",
                    currentEncoder.getEncoderType(),
                    currentEncoder.isHardwareAccelerated(),
                    (int) (currentEncoder.getCpuReduction() * 100));

            recorder.setFormat("mp4");
            recorder.setFrameRate(FRAME_RATE);
            recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
            recorder.setVideoBitrate(2000000);
            recorder.setGopSize(FRAME_RATE);

            // ✅ fMP4 container options for MediaSource
            recorder.setOption("movflags", "frag_keyframe+empty_moov+default_base_moof");
            recorder.setOption("flush_packets", "1");
            recorder.setOption("min_frag_duration", "66666"); // ~1 frame at 15fps

            recorder.start();
            recorderStarted = true;

            logger.info("✅ H.264 fMP4 encoder started: {}x{} @ {}fps ({})",
                    targetWidth, targetHeight, FRAME_RATE, codecName);

            // ✅ Wait for init segment to be written
            Thread.sleep(200);

            // ✅ Extract and cache init segment (ftyp + moov boxes)
            extractInitSegment();

            return true;

        } catch (Exception e) {
            logger.error("❌ Failed to initialize H.264 recorder: {}", e.getMessage(), e);
            recorderStarted = false;
            return false;
        }
    }

    /**
     * ✅ SIMPLIFIED: Start capture with incremental ByteArrayOutputStream sending
     */
    public void startCapture() {
        if (!isInitialized || isCapturing) {
            logger.warn("Cannot start capture - not initialized or already capturing");
            return;
        }

        if (!initializeVideoRecorder()) {
            logger.error("Recorder could not be started");
            return;
        }

        isCapturing = true;

        // ✅ Start performance monitoring with encoder type
        String encoderType = currentEncoder != null ? currentEncoder.getEncoderType() : "Unknown";
        performanceMonitor.startMonitoring(encoderType);
        logger.info("📊 Performance monitoring started");

        scheduler = Executors.newScheduledThreadPool(1);

        final long frameIntervalMs = 1000 / FRAME_RATE;

        scheduler.scheduleAtFixedRate(() -> {
            try {
                long captureStartTime = System.currentTimeMillis();

                // Grab frame
                Frame frame = frameGrabber.grabImage();
                if (frame != null && frame.image != null) {
                    // ✅ Record successful frame capture
                    performanceMonitor.recordFrameCapture();

                    // Set timestamp for smooth playback
                    if (frame.timestamp > 0) {
                        try {
                            recorder.setTimestamp(frame.timestamp);
                        } catch (Exception ignore) {
                        }
                    }

                    // ✅ Record frame to ByteArrayOutputStream
                    recorder.record(frame);

                    // ✅ Send only NEW bytes written since last send
                    sendIncrementalData();

                    // ✅ Calculate and record latency
                    long latency = System.currentTimeMillis() - captureStartTime;
                    performanceMonitor.recordLatency(latency);

                    // Reset consecutive frame skip counter
                    consecutiveFrameSkips = 0;
                    lastFrameTime = System.currentTimeMillis();
                } else {
                    // ✅ Track dropped frames
                    consecutiveFrameSkips++;
                    performanceMonitor.recordDroppedFrame();

                    if (consecutiveFrameSkips > 5) {
                        logger.warn("⚠️ {} consecutive frames dropped", consecutiveFrameSkips);
                    }
                }
            } catch (Exception e) {
                logger.error("Error during screen capture: {}", e.getMessage());
                performanceMonitor.recordDroppedFrame();
            }

        }, 0, frameIntervalMs, TimeUnit.MILLISECONDS);

        logger.info("🎬 H.264 streaming started at {} FPS", FRAME_RATE);

    }

    /**
     * ✅ Send only NEW media fragments (moof + mdat boxes)
     */
    private void sendIncrementalData() {
        try {
            byte[] fullBuffer = videoStream.toByteArray();
            int currentSize = fullBuffer.length;

            // Only send if there's new data
            if (currentSize > lastSentPosition) {
                byte[] newData = Arrays.copyOfRange(fullBuffer, lastSentPosition, currentSize);

                if (newData.length > 0 && containsMediaFragment(newData)) {
                    String boxInfo = detectBoxType(newData);
                    logger.debug("📤 Sending {} bytes to {} viewers ({})",
                            newData.length, webSocketHandler.getSessionCount(), boxInfo);

                    webSocketHandler.broadcastVideoBinary(newData);
                    lastSentPosition = currentSize;
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error sending incremental data: {}", e.getMessage());
        }
    }

    /**
     * ✅ SIMPLIFIED: Stop capture without pipe cleanup
     */
    public void stopCapture() {
        logger.info("🛑 Stopping screen capture...");

        // ✅ Stop performance monitoring
        performanceMonitor.stopMonitoring();
        logger.info("📊 Performance monitoring stopped");

        // Stop scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Stop and release recorder
        if (recorder != null && recorderStarted) {
            try {
                logger.info("🔄 Flushing and stopping recorder...");
                recorder.stop();
                recorder.release();
                recorderStarted = false;
            } catch (Exception e) {
                logger.warn("⚠️ Error stopping recorder: {}", e.getMessage());
            }
            recorder = null;
        }

        // ✅ Clean up ByteArrayOutputStream
        if (videoStream != null) {
            try {
                videoStream.close();
            } catch (IOException e) {
                logger.debug("Error closing video stream: {}", e.getMessage());
            }
            videoStream = null;
        }

        lastSentPosition = 0;
        isCapturing = false;

        logger.info("✅ Screen capture stopped cleanly");
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
     * Get cached init segment for late-joining viewers
     */
    public byte[] getInitSegment() {
        return initSegment;
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

    /**
     * Detects MP4 box types for debugging
     */
    private String detectBoxType(byte[] data) {
        if (data.length < 8)
            return "unknown";

        StringBuilder boxes = new StringBuilder();
        int offset = 0;

        while (offset + 8 <= data.length) {
            int size = ((data[offset] & 0xFF) << 24) |
                    ((data[offset + 1] & 0xFF) << 16) |
                    ((data[offset + 2] & 0xFF) << 8) |
                    (data[offset + 3] & 0xFF);

            if (size < 8 || size > data.length - offset)
                break;

            String type = new String(new byte[] { data[offset + 4], data[offset + 5],
                    data[offset + 6], data[offset + 7] });

            if (boxes.length() > 0)
                boxes.append(", ");
            boxes.append(type).append("(").append(size).append(")");

            offset += size;
        }

        return boxes.length() > 0 ? boxes.toString() : "unknown";
    }

    /**
     * ✅ Extract and cache init segment (ftyp + moov)
     */
    private void extractInitSegment() {
        try {
            byte[] data = videoStream.toByteArray();
            if (data.length > 0) {
                int moovEnd = findBoxEnd(data, "moov");
                if (moovEnd > 0) {
                    initSegment = Arrays.copyOfRange(data, 0, moovEnd);
                    lastSentPosition = moovEnd;
                    logger.info("✅ Init segment cached: {} bytes (boxes: {})",
                            initSegment.length, detectBoxType(initSegment));
                } else {
                    logger.warn("⚠️ moov box not found in init data");
                }
            }
        } catch (Exception e) {
            logger.error("❌ Failed to extract init segment: {}", e.getMessage());
        }
    }

    /**
     * Find end position of MP4 box
     */
    private int findBoxEnd(byte[] data, String boxType) {
        int offset = 0;
        while (offset + 8 <= data.length) {
            int size = ((data[offset] & 0xFF) << 24) |
                    ((data[offset + 1] & 0xFF) << 16) |
                    ((data[offset + 2] & 0xFF) << 8) |
                    (data[offset + 3] & 0xFF);

            if (size < 8 || offset + size > data.length)
                break;

            String type = new String(new byte[] { data[offset + 4], data[offset + 5],
                    data[offset + 6], data[offset + 7] });

            if (type.equals(boxType)) {
                return offset + size;
            }

            offset += size;
        }
        return -1;
    }

    /**
     * Find box position
     */
    private int findBox(byte[] data, String boxType) {
        int offset = 0;
        while (offset + 8 <= data.length) {
            int size = ((data[offset] & 0xFF) << 24) |
                    ((data[offset + 1] & 0xFF) << 16) |
                    ((data[offset + 2] & 0xFF) << 8) |
                    (data[offset + 3] & 0xFF);

            if (size < 8 || offset + size > data.length)
                break;

            String type = new String(new byte[] { data[offset + 4], data[offset + 5],
                    data[offset + 6], data[offset + 7] });

            if (type.equals(boxType))
                return offset;

            offset += size;
        }
        return -1;
    }

    /**
     * Check if data contains media fragment (moof/mdat)
     */
    private boolean containsMediaFragment(byte[] data) {
        return findBox(data, "moof") >= 0 || findBox(data, "mdat") >= 0;
    }

    /**
     * Get screen dimensions
     */
    public Rectangle getScreenBounds() {
        return screenRect;
    }
}
