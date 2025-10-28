# ScreenAI - Real-Time Screen Sharing Platform
A real-time screen sharing application built with **Spring Boot** and **JavaCV**, featuring **H.264 video streaming** with **fMP4** container.

## 🚀 Features

### **Current Features (v2.0)**
- ✅ **H.264 Streaming** - Real-time screen capture with native resolution at 15fps
- ✅ **fMP4 Fragmented Streaming** - Optimized container format for MediaSource API
- ✅ **Cross-Platform Hardware Acceleration** - GPU encoding on macOS (VideoToolbox), Windows/Linux (NVENC), software fallback (libx264)
- ✅ **Real-Time Performance Monitoring** - Live FPS, latency, dropped frames, CPU, and memory tracking
- ✅ **Performance Dashboard** - WebSocket-powered live metrics display
- ✅ **REST API for Metrics** - HTTP endpoints for performance data access
- ✅ **Real-Time Viewer Count** - Live WebSocket updates showing connected viewers
- ✅ **Cross-Platform Support** - Works on Windows, macOS, and Linux
- ✅ **Multi-Viewer Broadcasting** - Stream to multiple viewers simultaneously
- ✅ **WebSocket Binary Streaming** - Efficient real-time data transmission
- ✅ **MediaSource API Integration** - Native browser video decoding
- ✅ **Platform-Specific Optimization** - Tailored capture methods for each OS
- ✅ **Init Segment Caching** - Instant playback for late-joining viewers
- ✅ **Incremental Data Streaming** - Send only new video fragments for efficiency

## 🛠️ Technology Stack

- **Backend:** Spring Boot 3.5.5, JavaCV 1.5.x, FFmpeg
- **Frontend:** HTML5, JavaScript, WebSocket API, MediaSource Extensions (MSE)
- **Video Processing:** H.264/AVC codec with fMP4 fragmented container
- **Real-Time Communication:** WebSocket binary + JSON messaging
- **Build Tool:** Maven
- **JDK:** Java 17+

## 📋 System Requirements

### **Minimum Requirements**
- **Java:** JDK 17 or higher
- **Memory:** 4GB RAM minimum, 8GB recommended
- **CPU:** Multi-core processor (Intel i5/AMD Ryzen 5 or better)
- **Network:** Stable internet connection for multi-device streaming
- **Screen Recording Permissions:** Required on macOS and some Linux distributions

### **Platform Support**
- **Windows 10+** - Uses `gdigrab` for screen capture
- **macOS 10.14+** - Uses `avfoundation` for screen capture  
- **Linux (Ubuntu/Debian)** - Uses `x11grab` for screen capture

### **Browser Compatibility**
- **Chrome 85+** (Recommended - best fMP4 and H.264 support)
- **Firefox 78+** (Full MSE and H.264 support)
- **Safari 14+** (Native H.264 support on macOS/iOS)
- **Edge 85+** (Chromium-based, full compatibility)

**Note:** All modern browsers support MediaSource Extensions and H.264 codec required for streaming.

## 🚀 Quick Start

### **1. Clone the Repository**
```bash
git clone https://github.com/vijayvir/ScreenAi.git
cd ScreenAi
```

### **2. Build the Application**
```bash
./mvnw clean package
```

### **3. Run the Application**
```bash
./mvnw spring-boot:run
```

### **4. Access the Application**
- **Local Access:** http://localhost:8080
- **Network Access:** http://[your-ip]:8080 (for other devices)

### **5. Start Streaming**
1. Click **"Start Screen Sharing"** button
2. Grant screen recording permissions if prompted (macOS/Linux)
3. Open additional browser tabs/devices to view the stream
4. Click **"Stop Screen Sharing"** when finished

## 🎯 H.264 Streaming Configuration

### **Video Encoding Settings**
```java 
- Resolution: Native screen resolution (dynamic)
- Frame Rate: 15 FPS (optimal for screen sharing)
- Bitrate: 2 Mbps (high quality)
- Codec: H.264 (h264_videotoolbox on macOS, libx264 fallback)
- Container: fMP4 (Fragmented MP4)
- GOP Size: 15 frames (1 second at 15 FPS)
- Pixel Format: YUV420P
- Encoder Preset: ultrafast
- Tune: zerolatency (minimal buffering)
- Latency: ~66ms per frame (1/15th second)
```

### **fMP4 Container Configuration**
```java
// Fragmented MP4 settings for MediaSource API
- movflags: frag_keyframe+empty_moov+default_base_moof
- flush_packets: 1 (immediate streaming)
- min_frag_duration: 66666 microseconds (~1 frame)
```

### **Browser MediaSource Configuration**
```javascript
// MediaSource API setup
- Codec: 'video/mp4; codecs="avc1.42E01E"' (H.264 Baseline)
- Init Segment: Cached ftyp+moov boxes for instant playback
- Media Fragments: Incremental moof+mdat boxes
- Buffer Management: Automatic cleanup of old segments
```

## 🔧 Platform-Specific Setup

### **macOS Setup**
1. **Grant Screen Recording Permission:**
   - Go to `System Preferences > Security & Privacy > Privacy > Screen Recording`
   - Add your Java application or IDE to the allowed list
   - Restart the application after granting permission

### **Linux Setup**
1. **Install Required Dependencies:**
   ```bash
   sudo apt-get update
   sudo apt-get install x11-utils
   ```
2. **Ensure X11 Display Access:**
   ```bash
   echo $DISPLAY  # Should show :0.0 or similar
   ```

### **Windows Setup**
- No additional setup required
- Windows Defender may prompt for network access (allow it)

## 📊 Performance Optimization

### **Server-Side Optimizations**
- **Init Segment Caching** - Pre-extracted ftyp+moov boxes for instant viewer onboarding
- **Incremental Streaming** - Send only new moof+mdat fragments, not full buffer
- **ByteArrayOutputStream** - In-memory streaming without file I/O overhead
- **Hardware Acceleration** - h264_videotoolbox on macOS for GPU encoding
- **Ultra-Fast Preset** - Minimal encoding latency (~66ms per frame)
- **Frame-Level Fragmentation** - Each frame becomes immediately available

### **Client-Side Optimizations**
- **MediaSource Buffer Management** - Automatic cleanup of old video segments
- **Direct Binary Processing** - No Base64 encoding/decoding overhead
- **Immediate Playback** - Init segment enables instant video start
- **WebSocket Binary Mode** - Efficient ArrayBuffer transmission
- **Real-Time Stats** - Live viewer count via WebSocket JSON messages

## 🌐 API Endpoints

### **REST API**
```http
GET  /                            # Main viewer interface
GET  /api/status                 # Get streaming status and viewer count
GET  /api/performance/metrics    # Get current performance metrics
GET  /api/performance/stats      # Get aggregated performance statistics
GET  /api/performance/status     # Get monitoring status
```

### **Performance Metrics API**

#### **GET /api/performance/metrics**
Returns real-time performance snapshot:
```json
{
  "fps": 15.0,
  "latencyMs": 45,
  "droppedFrames": 2,
  "totalFrames": 450,
  "dropRate": 0.44,
  "cpuUsage": 23.5,
  "memoryUsageMb": 512.3,
  "encoderType": "GPU (VideoToolbox)",
  "timestamp": "2025-10-28T10:30:45.123Z"
}
```

#### **GET /api/performance/stats**
Returns aggregated statistics:
```json
{
  "currentFps": 15.0,
  "currentLatencyMs": 45,
  "droppedFrames": 2,
  "totalFrames": 450,
  "dropRate": 0.44,
  "cpuUsage": 23.5,
  "memoryUsageMb": 512.3,
  "encoderType": "GPU (VideoToolbox)",
  "isMonitoring": true
}
```

#### **GET /api/performance/status**
Returns monitoring status:
```json
{
  "active": true,
  "encoderType": "GPU (VideoToolbox)",
  "currentFps": 15.0,
  "totalFrames": 450
}
```

### **WebSocket Endpoints**
```javascript
// Binary video data (H.264 fMP4 fragments)
ws://localhost:8080/screenshare

// Message Types:
// 1. Binary: H.264 video data (init segment + media fragments)
// 2. JSON: {"type":"viewerCount","count":N} - Real-time viewer updates
// 3. JSON: {"type":"performance","metrics":{...}} - Real-time performance metrics
```

### **Example WebSocket Client**
```javascript
const ws = new WebSocket('ws://localhost:8080/screenshare');
ws.binaryType = 'arraybuffer';

ws.onmessage = (event) => {
  if (event.data instanceof ArrayBuffer) {
    // Handle binary video data
    handleVideoData(event.data);
  } else {
    // Handle JSON messages
    const msg = JSON.parse(event.data);
    
    if (msg.type === 'viewerCount') {
      updateViewerCount(msg.count);
    } else if (msg.type === 'performance') {
      updatePerformanceMetrics(msg.metrics);
    }
  }
};
```

## 🔍 Troubleshooting

### **Common Issues**

**1. No Video Stream Visible**
- Check browser console for codec support: Should show `video/mp4; codecs="avc1.42E01E"` as supported
- Verify screen recording permissions granted (macOS: System Preferences > Security & Privacy > Screen Recording)
- Ensure WebSocket connection established (check Network tab in DevTools)
- Confirm MediaSource API supported in browser

**2. High Latency/Choppy Video**
- Check CPU usage (should be <30% per core with hardware acceleration)
- Verify network bandwidth (minimum 2.5 Mbps)
- Review browser performance in DevTools Performance tab
- Check for other applications consuming bandwidth
- Try closing other browser tabs

**3. WebSocket Connection Fails**
- Check firewall settings (allow port 8080)
- Verify application is running: `curl http://localhost:8080`
- Check browser console for WebSocket errors
- Try different browser (Chrome recommended)
- Disable browser extensions that might block WebSocket

**4. Permission Denied (macOS)**
- Grant screen recording permission: System Preferences > Security & Privacy > Privacy > Screen Recording
- Add Java or your IDE to the allowed list
- Restart the application after granting permission
- May need to restart the terminal/IDE

**5. Black Screen or Frozen Video**
- Check server logs for encoder errors
- Verify FFmpeg screen capture device detected
- Try restarting the application
- Check if another application is blocking screen capture

### **Debug Logs**
Enable detailed debug logging:
```bash
./mvnw spring-boot:run -Dlogging.level.com.screenai=DEBUG
```

Check specific component logs:
```bash
# WebSocket handler logs
./mvnw spring-boot:run -Dlogging.level.com.screenai.handler=DEBUG

# Video encoding logs
./mvnw spring-boot:run -Dlogging.level.com.screenai.service=DEBUG
```

## 🏗️ Architecture Overview

### **Design Patterns Used**

| Pattern | Component | Purpose |
|---------|-----------|---------|
| **Strategy** | VideoEncoderStrategy | Runtime encoder selection (GPU/CPU) |
| **Factory** | VideoEncoderFactory | Platform-aware encoder creation |
| **Builder** | PerformanceMetrics | Flexible metrics object construction |
| **Observer** | MetricsListener | Real-time performance broadcasting |
| **Singleton** | Spring @Service | Service lifecycle management |

### **System Architecture**

```
┌─────────────────────────────────────────────────────────────────────┐
│                         SERVER SIDE                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    ┌───────────────┐    ┌──────────────────┐   │
│  │   Screen     │    │   JavaCV      │    │  Encoder Factory │   │
│  │   Capture    │───▶│   Frame       │───▶│  - VideoToolbox  │   │
│  │   (FFmpeg)   │    │   Grabber     │    │  - NVENC         │   │
│  └──────────────┘    └───────────────┘    │  - libx264       │   │
│                                            └──────────────────┘   │
│                                                      │              │
│                                                      ▼              │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │              Performance Monitor Service                     │  │
│  │  - FPS Tracking (2-second rolling window)                   │  │
│  │  - Latency Measurement (10-sample average)                  │  │
│  │  - Dropped Frame Detection                                  │  │
│  │  - CPU & Memory Monitoring                                  │  │
│  │  - Observer Pattern Broadcasting (every 1 second)           │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                      │              │
│                                                      ▼              │
│                                            ┌──────────────────┐   │
│                                            │  ByteArrayOS     │   │
│                                            │  - Init Segment  │   │
│                                            │  - Media Frags   │   │
│                                            └──────────────────┘   │
│                                                      │              │
│                                                      ▼              │
│                                            ┌──────────────────┐   │
│                                            │   WebSocket      │   │
│                                            │   Handler        │   │
│                                            │   - Binary Data  │   │
│                                            │   - Viewer Count │   │
│                                            │   - Performance  │   │
│                                            └──────────────────┘   │
└────────────────────────────────┬────────────────────────────────────┘
                                 │ WebSocket (Binary + JSON)
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT SIDE                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    ┌───────────────┐    ┌──────────────────┐   │
│  │  WebSocket   │    │  MediaSource  │    │   <video>        │   │
│  │  Client      │───▶│  API          │───▶│   Element        │   │
│  │  (Binary)    │    │  - SourceBuf  │    │   (Playback)     │   │
│  └──────────────┘    │  - H.264 Dec  │    └──────────────────┘   │
│         │            └───────────────┘                             │
│         │ (JSON)                                                   │
│         ▼                                                           │
│  ┌──────────────┐    ┌──────────────────────────────────┐         │
│  │  Viewer      │    │  Performance Dashboard           │         │
│  │  Count UI    │    │  - FPS Display                   │         │
│  └──────────────┘    │  - Latency Gauge                 │         │
│                      │  - Dropped Frames Counter        │         │
│                      │  - CPU/Memory Usage              │         │
│                      │  - Encoder Type Info             │         │
│                      └──────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────────────┘
```

### **Data Flow**
1. **Screen Capture**: FFmpeg grabs screen frames via platform-specific API
2. **Encoder Selection**: Factory pattern selects best encoder (GPU preferred)
3. **Encoding**: JavaCV encodes frames to H.264 in fMP4 container
4. **Performance Tracking**: Monitor records FPS, latency, dropped frames
5. **Streaming**: New video fragments sent via WebSocket (binary)
6. **Metrics Broadcasting**: Performance data sent via WebSocket (JSON, every 1s)
7. **Decoding**: Browser MediaSource API decodes H.264 natively
8. **Playback**: Video element displays decoded frames in real-time
9. **Dashboard Updates**: Frontend displays live performance metrics

### **Component Details**

#### **Encoder Package** (Strategy + Factory Pattern)
```
com.screenai.encoder/
├── VideoEncoderStrategy.java      (Interface)
├── VideoEncoderFactory.java       (Factory with platform detection)
├── H264VideoToolboxEncoder.java   (macOS GPU - 70% CPU reduction)
├── NvencEncoder.java              (NVIDIA GPU - 80% CPU reduction)
└── LibX264Encoder.java            (Software fallback)
```

#### **Performance Monitoring** (Observer + Builder Pattern)
```
com.screenai.service/
└── PerformanceMonitorService.java
    ├── MetricsListener interface  (Observer Pattern)
    ├── FPS calculation (2-second rolling window)
    ├── Latency tracking (10-sample average)
    ├── CPU/Memory monitoring (OperatingSystemMXBean)
    └── Scheduled broadcasting (every 1 second)

com.screenai.model/
└── PerformanceMetrics.java
    └── Builder pattern for flexible construction
```

#### **REST API**
```
com.screenai.controller/
└── PerformanceController.java
    ├── GET /api/performance/metrics
    ├── GET /api/performance/stats
    └── GET /api/performance/status
```

## 📈 Performance Metrics

### **Real-Time Monitoring**
The application now includes comprehensive performance monitoring:

- **FPS (Frames Per Second)**: Calculated from 2-second rolling window
- **Latency**: Average capture-to-broadcast time (10-sample average)
- **Dropped Frames**: Count and percentage of skipped frames
- **CPU Usage**: System CPU utilization via OperatingSystemMXBean
- **Memory Usage**: JVM heap memory consumption
- **Encoder Type**: Active encoder (GPU/CPU) with acceleration status

### **Performance Metrics Thresholds**

| Metric | Good | Warning | Critical |
|--------|------|---------|----------|
| **FPS** | 14-15 | 10-13 | < 10 |
| **Latency** | < 100ms | 100-200ms | > 200ms |
| **Dropped Frames** | < 5% | 5-10% | > 10% |
| **CPU Usage** | < 50% | 50-80% | > 80% |
| **Memory** | < 1GB | 1-2GB | > 2GB |

### **Typical Performance**
- **Latency:** ~50-100ms (encoding + network + decoding)
- **End-to-End Delay:** 200-400ms total
- **CPU Usage (with GPU):** 10-25% per core during streaming
- **CPU Usage (software):** 30-60% per core during streaming
- **Memory Usage:** ~200MB base + ~50MB per viewer
- **Bandwidth:** ~2 Mbps outbound per viewer
- **Frame Rate:** Consistent 15 FPS
- **Resolution:** Native screen resolution (dynamic)

### **Hardware Acceleration Impact**
- **macOS (VideoToolbox):** 70% CPU reduction vs software
- **Windows/Linux (NVENC):** 80% CPU reduction vs software
- **Fallback (libx264):** Baseline performance (software encoding)

### **Scalability**
- **Concurrent Viewers:** 10-20 viewers with GPU acceleration (5-10 with software)
- **Network Bandwidth:** ~2 Mbps per viewer
- **CPU Scaling:** Linear with viewer count (significantly reduced with hardware encoding)
- **Memory Scaling:** ~50MB per additional viewer


**New Components:**
- `PerformanceMonitorService` - Real-time metrics tracking
- `PerformanceMetrics` - Metrics DTO with Builder pattern
- `PerformanceController` - REST API for metrics
- `VideoEncoderStrategy` - Strategy interface for encoders
- `VideoEncoderFactory` - Encoder selection and creation
- `H264VideoToolboxEncoder` - macOS GPU encoder
- `NvencEncoder` - NVIDIA GPU encoder
- `LibX264Encoder` - Software fallback encoder

**Performance Impact:**
- 70-80% CPU reduction with GPU acceleration
- Sub-100ms latency monitoring
- Real-time performance visibility
- Platform-aware encoder selection

### **v1.0** (Initial Release)
- ✅ H.264 streaming with fMP4 container
- ✅ Real-time viewer count
- ✅ Cross-platform support (Windows, macOS, Linux)
- ✅ WebSocket binary streaming
- ✅ MediaSource API integration
- ✅ Init segment caching
- ✅ Incremental data streaming
