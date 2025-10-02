

# 🖥️ ScreenAI - Real-time Screen Sharing Application

A lightweight, cross-platform screen sharing application built with Spring Boot and JavaCV. This application provides real-time server desktop streaming using WebSocket technology .

## Features

- ✅ **Real-time Screen Streaming** - Live screen capture via WebSocket
- ✅ **Cross-platform Support** - Works on Windows, macOS, and Linux
- ✅ **Simple Interface** - Clean, intuitive web-based viewer
- ✅ **High Performance** - 10 FPS streaming with optimized frame delivery
- ✅ **Zero Installation** - Browser-based viewer, no client software needed
- ✅ **Cross-platform compatibility** - Works on Windows, macOS, and Linux  
- ✅ **Multiple viewers** - Support for concurrent viewers

## Technologies Used

- **Java 17** - Programming language
- **Spring Boot 3.5.5** - Web framework and application container
- **WebSockets** - Real-time bidirectional communication for frame streaming
- **JavaCV 1.5.9** - Screen capture using FFmpegFrameGrabber
- **FFmpeg** - Platform-specific screen capture (AVFoundation, gdigrab, x11grab)
- **Thymeleaf** - Template engine for web interface
- **Maven** - Dependency management and build tool

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- A computer with a graphical display (screen to share)

## Quick Start

### 1. Clone and Build

```bash
# Clone the repository (or extract if you have the source)
cd ScreenAI

# Build the application
./mvnw clean package
```

### 2. Run the Application

```bash
# Run the Spring Boot application
./mvnw spring-boot:run
```

Or run the JAR file directly:

```bash
java -jar target/screenai-0.0.1-SNAPSHOT.jar
```

### 3. Access the Application

The application will start and display network information:


## How It Works

### Architecture Overview

```
┌─────────────────┐    WebSocket     ┌─────────────────┐
│   Web Browser   │ ◄──────────────► │  Spring Boot    │
│   (Viewer)      │                  │    Server       │
│                 │  Frame Streaming │                 │
│ Display Images  │ ◄──────────────► │ Screen Capture  │
│   (Base64)      │    (Real-time)   │   (JavaCV)      │
└─────────────────┘                  └─────────────────┘
        │                                     │
        │ Multiple Viewers                    │ FFmpeg
        ▼                                     ▼
┌─────────────────┐                  ┌─────────────────┐
│ Concurrent      │                  │ Platform Screen │
│ WiFi Devices    │                  │ Capture:        │
│ (Phones/Tablets)│                  │ • macOS: AVFoundation │
└─────────────────┘                  │ • Windows: gdigrab    │
                                     │ • Linux: x11grab      │
                                     └─────────────────┘
```

### Key Components

1. **ScreenCaptureService** - JavaCV FFmpeg screen capture with platform detection
2. **ScreenShareWebSocketHandler** - WebSocket frame broadcasting to multiple viewers  
3. **ScreenSharingApiController** - REST API endpoints for monitoring and control
4. **WebController** - Serves the main web viewer interface
5. **HTML/JavaScript Client** - WebSocket client that displays real-time frames

### Data Flow

1. **Server Startup**: JavaCV initializes platform-specific screen capture
2. **Screen Capture**: FFmpegFrameGrabber captures server desktop at 10 FPS
3. **Frame Processing**: Captured frames converted to Base64 JPEG images
4. **WebSocket Broadcasting**: Frames sent to all connected viewer sessions
5. **Network Access**: Multiple devices can connect via WiFi to view stream
6. **Real-time Display**: Browser receives frames and updates image element

## Configuration

### Screen Capture Settings
Platform-specific screen capture is automatically configured in `ScreenCaptureService.java`:

```java
// macOS: AVFoundation
frameGrabber = new FFmpegFrameGrabber("Capture screen 0");
frameGrabber.setFormat("avfoundation");

// Windows: GDI screen capture  
frameGrabber = new FFmpegFrameGrabber("desktop");
frameGrabber.setFormat("gdigrab");

// Linux: X11 screen capture
frameGrabber = new FFmpegFrameGrabber(display);
frameGrabber.setFormat("x11grab");
```

### Frame Rate and Quality
Adjust capture settings in `ScreenCaptureService.java`:

```java
private static final int FRAME_RATE = 10; // FPS
private static final float JPEG_QUALITY = 0.8f; // 0.0 to 1.0
```

### Server Configuration
Change server settings in `application.properties`:

```properties
server.port=8080
server.address=0.0.0.0  # Allow network access
```

### WebSocket Configuration
WebSocket endpoint configuration in `WebSocketConfig.java`:

```java
@Override
public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(screenShareWebSocketHandler, "/screenshare")
           .setAllowedOrigins("*"); // Allow all origins for demo
}
```

## API Endpoints

### REST API
- **GET** `/api/status` - Get system status and capture information
- **POST** `/api/start-capture` - Start screen capture manually
- **POST** `/api/stop-capture` - Stop screen capture manually

### WebSocket Endpoint
- **WS** `/screenshare` - WebSocket connection for receiving live frames

### Example API Response
```json
{
  "capturing": true,
  "initialized": true,
  "captureMethod": "JavaCV FFmpeg",
  "viewerCount": 2,
  "screenResolution": {
    "width": 1920,
    "height": 1200
  },
  "osName": "Mac OS X",
  "javaVersion": "21.0.3",
  "serverTime": 1758043426676
}
```

## Troubleshooting

### Common Issues

1. **"Screen capture initialization failed" Error**
   - **Cause**: JavaCV FFmpeg cannot access screen capture device
   - **Solution**: 
     - On macOS: Grant screen recording permissions in System Preferences > Security & Privacy > Privacy > Screen Recording
     - On Windows: Run as administrator if needed
     - On Linux: Ensure X11 display is available (`echo $DISPLAY`)
     - Check that no other application is using screen capture

2. **WebSocket Connection Failed**
   - **Cause**: Network connectivity or firewall issues
   - **Solution**: 
     - Check if port 8080 is accessible: `telnet localhost 8080`
     - Ensure firewall allows connections on port 8080
     - Verify server is running and showing "Server Started" message
     - Try accessing from `http://localhost:8080` first

3. **No frames displayed / Black screen**
   - **Cause**: Screen capture permissions or initialization issues
   - **Solution**: 
     - Check server logs for "Screen capture started successfully" message
     - Verify WebSocket connection in browser developer tools
     - Test API endpoint: `curl http://localhost:8080/api/status`
     - Restart application if capture method shows "None"

4. **Network access not working**
   - **Cause**: Server not binding to network interface or firewall blocking
   - **Solution**: 
     - Ensure `server.address=0.0.0.0` in application.properties
     - Check firewall settings allow port 8080
     - Verify WiFi network allows device-to-device communication
     - Use IP address shown in startup message 

5. **Poor performance or lag**
   - **Cause**: High frame rate or large screen resolution
   - **Solution**: 
     - Reduce `FRAME_RATE` in `ScreenCaptureService.java`
     - Lower `JPEG_QUALITY` setting (try 0.6)
     - Close unnecessary applications on server
     - Use wired network connection if possible

### Performance Tips

- **Optimize Frame Rate**: Start with 5-10 FPS, increase as needed
- **Adjust JPEG Quality**: Lower quality = smaller files = better performance
- **Local Network**: Use local WiFi for best performance
- **Server Resources**: Ensure server has adequate CPU and memory
- **Close Applications**: Minimize server desktop applications during streaming

### Platform-Specific Notes

#### macOS
- Requires screen recording permission for the Java application or terminal
- AVFoundation capture provides best performance
- Terminal may need permission if running from command line

#### Windows  
- GDI capture works on all Windows versions
- May require administrator privileges for some capture scenarios
- Works best on Windows 10/11

#### Linux
- Requires X11 display server (not Wayland)
- Ensure `DISPLAY` environment variable is set
- May need additional permissions for screen access


### Project Structure

```
src/
├── main/
│   ├── java/com/aiscreensharing/
│   │   ├── AiScreenSharingApplication.java    # Main application class
│   │   ├── config/
│   │   │   └── WebSocketConfig.java           # WebSocket configuration
│   │   ├── controller/
│   │   │   ├── WebController.java             # Web page controller  
│   │   │   └── ScreenSharingApiController.java # REST API endpoints
│   │   ├── handler/
│   │   │   └── ScreenShareWebSocketHandler.java # WebSocket frame broadcasting
│   │   └── service/
│   │       └── ScreenCaptureService.java      # JavaCV screen capture service
│   └── resources/
│       ├── application.properties             # Application configuration
│       └── templates/
│           └── index.html                     # WebSocket viewer interface
```

### Key Implementation Details

- **ScreenCaptureService**: Uses JavaCV FFmpegFrameGrabber with platform-specific formats
- **WebSocket Handler**: Broadcasts Base64 JPEG frames to multiple concurrent viewers
- **Cross-Platform**: Automatic detection of macOS/Windows/Linux with appropriate capture methods
- **Fallback System**: AWT Robot backup when JavaCV fails
- **Network Access**: Server binds to 0.0.0.0 for WiFi device access

### Building from Source

```bash
# Clean and compile
./mvnw clean compile

# Run tests
./mvnw test

# Package as JAR
./mvnw package

# Run with profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is open source and available under the MIT License.

## Acknowledgments

- **Spring Boot** - For the excellent web framework and WebSocket support
- **JavaCV** - For cross-platform screen capture capabilities and FFmpeg bindings
- **FFmpeg** - For high-performance multimedia framework and screen capture
- **ByteDeco** - For Java bindings to native multimedia libraries
- **WebSocket Protocol** - For real-time bidirectional communication

---
