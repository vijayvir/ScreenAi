# ScreenAI-Server

A **lightweight WebSocket relay server** for real-time screen sharing. Built with **Spring Boot WebFlux + Netty** for high-performance, non-blocking video streaming.

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
         │      (H.264 fMP4)         │      (H.264 fMP4)         │
```

## ✨ Features

- ✅ **Reactive WebSocket Relay** - Non-blocking I/O with Spring WebFlux + Netty
- ✅ **Room-Based Architecture** - Isolated streaming rooms (1 presenter, multiple viewers)
- ✅ **Binary Video Streaming** - H.264/fMP4 video relay support
- ✅ **Init Segment Caching** - Late joiners receive cached init segment instantly
- ✅ **Auto Backpressure** - Handles slow consumers gracefully
- ✅ **Low Resource Usage** - Minimal CPU (~5-15%), server only relays data

---

## 🚀 Quick Start

### Prerequisites

- **Java 17+** (Java 21 recommended)

```bash
java -version
# Should show: openjdk version "17" or higher
```

### Run the Server

**Option 1: Using pre-built JAR**
```bash
java -jar target/screenai-server-1.0.0.jar
```

**Option 2: Using Maven**
```bash
./mvnw spring-boot:run
```

### Server Started!

```
═══════════════════════════════════════════════════════
   ScreenAI-Server (Relay Mode) Started Successfully   
═══════════════════════════════════════════════════════

📍 WebSocket Endpoint:
   Local:   ws://localhost:8080/screenshare
   Network: ws://<your-ip>:8080/screenshare

🔧 Server Mode: RELAY ONLY
   ✅ Room management enabled
   ✅ Binary data relay enabled
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

- **Presenter** sends H.264 fMP4 video frames as binary WebSocket messages
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

See [TESTING_GUIDE.md](TESTING_GUIDE.md) for complete test scenarios.

---

## 🏗️ Project Structure

```
src/main/java/com/screenai/
├── ScreenAIApplication.java              # Main entry point
├── config/
│   └── WebFluxWebSocketConfig.java       # WebSocket configuration
├── handler/
│   └── ReactiveScreenShareHandler.java   # WebSocket message handler
├── model/
│   ├── ReactiveRoom.java                 # Room state
│   └── PerformanceMetrics.java           # Metrics model
└── service/
    └── PerformanceMonitorService.java    # Performance tracking
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
```

---

## 📈 Performance

| Scenario | Rooms | Viewers | CPU | Memory |
|----------|-------|---------|-----|--------|
| Light    | 5     | 25      | ~8% | 180 MB |
| Medium   | 20    | 100     | ~15%| 300 MB |
| Heavy    | 50    | 250     | ~30%| 500 MB |

*Benchmarks on 4-core, 8GB RAM machine*

---

## 📋 Client Integration

For detailed client integration instructions, see:

📖 **[CLIENT_INTEGRATION_GUIDE.md](CLIENT_INTEGRATION_GUIDE.md)**

Includes:
- Java WebSocket client examples
- Presenter and Viewer code samples
- Video format requirements (H.264 fMP4)
- Error handling best practices

---

## 🐛 Troubleshooting

### Port already in use
```bash
lsof -i :8080
# Kill the process or use different port
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
| Java | OpenJDK | 17+ |
| Build | Maven | 3.9.x |

---

## 📄 License

MIT License - See [LICENSE](LICENSE) for details.

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

Made with ❤️ for real-time screen sharing
