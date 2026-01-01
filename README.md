
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

### api guide

to generate code:
http://localhost:8081/api/start

to join (pass code in raw)
http://localhost:8080/api/join