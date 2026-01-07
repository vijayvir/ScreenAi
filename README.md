# ScreenAI-Server

A **lightweight WebSocket relay server** for real-time screen sharing. Built with **Spring Boot WebFlux + Netty** for high-performance, non-blocking video streaming.

![Spring Boot 3.5.5](https://img.shields.io/badge/Spring_Boot-3.5.5-green) ![Java 21](https://img.shields.io/badge/Java-21-red) ![WebFlux](https://img.shields.io/badge/Reactive-WebFlux-blue) ![Netty](https://img.shields.io/badge/Server-Netty-orange)

## 🎯 Overview

ScreenAI-Server acts as a relay hub between presenters (screen sharers) and viewers:

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│    Presenter    │         │  ScreenAI Server│         │     Viewers     │
│  (Screen Share) │         │  (WebFlux+Netty)│         │   (Watch Feed)  │
└────────┬────────┘         └────────┬────────┘         └────────┬────────┘
         │                           │                           │
         │  1. create-room           │                           │
         │──────────────────────────►│                           │
         │                           │                           │
         │                           │  2. join-room             │
         │                           │◄──────────────────────────│
         │                           │                           │
         │  3. Binary video frames   │  4. Relay to viewers      │
         │══════════════════════════►│══════════════════════════►│
         │      (H.264 MPEG-TS)      │      (H.264 MPEG-TS)      │
```

## ✨ Features

- ✅ **Reactive WebSocket Relay** - Non-blocking I/O with Spring WebFlux + Netty
- ✅ **Room-Based Architecture** - Isolated streaming rooms (1 presenter, multiple viewers)
- ✅ **Binary Video Streaming** - H.264/MPEG-TS video relay support
- ✅ **Init Segment Caching** - Late joiners receive cached init segment instantly
- ✅ **Auto Backpressure** - Handles slow consumers gracefully
- ✅ **Low Resource Usage** - Minimal CPU (~5-15%), server only relays data
- ✅ **Cross-Platform Clients** - Supports macOS, Windows, and Linux clients

---

## 🚀 Quick Start

### Prerequisites

- **Java 17+** (Java 21 recommended)

```bash
java -version
# Should show: openjdk version "17" or higher
```

### Run the Server

**Option 1: Using Maven Wrapper (Recommended)**
```bash
./mvnw spring-boot:run
```

**Option 2: Using pre-built JAR**
```bash
java -jar target/screenai-server-1.0.0.jar
```

### Server Started!

```
═══════════════════════════════════════════════════════════
   ScreenAI-Server (WebFlux + Netty) Started Successfully   
═══════════════════════════════════════════════════════════

📍 WebSocket Endpoint:
   Local:   ws://localhost:8080/screenshare
   Network: ws://<your-ip>:8080/screenshare

🔧 Server Mode: WebFlux + Netty (Non-Blocking)
   ✅ Reactive WebSocket handling
   ✅ Non-blocking I/O via Netty
   ✅ Automatic backpressure handling
   ✅ Binary data relay (no size limits)
   ✅ Room management enabled
```

---

## 🔌 WebSocket Protocol

### Endpoint
```
ws://localhost:8080/screenshare
```

### Message Types

#### 1. Create Room (Presenter)

**Request:**
```json
{"type": "create-room", "roomId": "my-room-123"}
```

**Response:**
```json
{
  "type": "room-created",
  "roomId": "my-room-123",
  "role": "presenter",
  "message": "Room created successfully"
}
```

#### 2. Join Room (Viewer)

**Request:**
```json
{"type": "join-room", "roomId": "my-room-123"}
```

**Response:**
```json
{
  "type": "room-joined",
  "roomId": "my-room-123",
  "role": "viewer",
  "hasPresenter": true
}
```

#### 3. Leave Room

**Request:**
```json
{"type": "leave-room"}
```

#### 4. Get Viewer Count (Presenter only)

**Request:**
```json
{"type": "get-viewer-count"}
```

**Response:**
```json
{"type": "viewer-count", "count": 5}
```

#### 5. Binary Video Data

- **Presenter** sends H.264 MPEG-TS video frames as binary WebSocket messages
- **Server** relays binary data to all viewers in the room
- **Viewers** receive binary frames for decoding/display

---

## 📨 Server Events

| Event | Description |
|-------|-------------|
| `connected` | Connection established |
| `room-created` | Room created successfully |
| `room-joined` | Joined room as viewer |
| `waiting` | Room exists but no presenter yet |
| `presenter-joined` | Presenter started streaming |
| `presenter-left` | Presenter disconnected |
| `viewer-joined` | New viewer joined |
| `viewer-left` | Viewer disconnected |
| `room-closed` | Room was closed |
| `error` | Error occurred |

---

## 🏗️ Project Structure

```
src/main/java/com/screenai/
├── ScreenAIApplication.java              # Main entry point with startup banner
├── config/
│   ├── WebFluxWebSocketConfig.java       # WebSocket endpoint configuration
│   └── JacksonConfig.java                # JSON serialization config
├── handler/
│   └── ReactiveScreenShareHandler.java   # WebSocket message handler (rooms, relay)
├── model/
│   ├── ReactiveRoom.java                 # Room state management
│   └── PerformanceMetrics.java           # Metrics model
├── service/
│   └── PerformanceMonitorService.java    # Performance tracking
└── controller/
    └── PerformanceController.java        # REST API for metrics
```

---

## ⚙️ Configuration

### application.yml

```yaml
server:
  port: 8080

spring:
  main:
    web-application-type: reactive

logging:
  level:
    com.screenai: INFO
```

### Environment Variables

```bash
# Custom port
SERVER_PORT=9090 java -jar screenai-server-1.0.0.jar

# Or with Maven
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=9090
```

---

## 🧪 Testing

### Using wscat

```bash
# Install wscat
npm install -g wscat

# Create a room (Presenter)
wscat -c ws://localhost:8080/screenshare
> {"type":"create-room","roomId":"test"}

# Join the room (Viewer - new terminal)
wscat -c ws://localhost:8080/screenshare
> {"type":"join-room","roomId":"test"}
```

### Using the ScreenAI Client

1. Start the server: `./mvnw spring-boot:run`
2. Run the client application (see [ScreenAI-Client](../ScreenAI-Client/README.md))
3. Connect to `localhost:8080`
4. Create a room and start sharing

---

## 🐛 Troubleshooting

### Port already in use
```bash
# Find process using port 8080
lsof -i :8080

# Kill it and restart
lsof -ti:8080 | xargs kill -9

# Or use different port
java -jar -Dserver.port=9090 screenai-server-1.0.0.jar
```

### WebSocket connection fails
```bash
# Test if server is running
curl http://localhost:8080

# Check server logs
tail -f logs/screenai.log
```

### No video data received
- Ensure presenter has created the room first
- Verify room ID matches between presenter and viewer
- Check that presenter is sending binary data

---

## 🔧 Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Spring Boot | 3.5.5 |
| Reactive | Spring WebFlux | 6.x |
| Server | Netty | 4.x |
| Java | OpenJDK | 17-21 |
| Build | Maven | 3.9.x |

---

## 📄 Related Projects

- **[ScreenAI-Client](../ScreenAI-Client)** - JavaFX desktop client for screen sharing


