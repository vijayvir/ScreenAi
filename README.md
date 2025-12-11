# ScreenAI-Server - Lightweight WebSocket Relay Server

A **lightweight, scalable relay server** for real-time screen sharing and video streaming. Built with **Spring Boot** and **WebSocket**, this server efficiently forwards H.264 video streams from presenters to multiple viewers.

## 🎯 What is ScreenAI-Server?

ScreenAI-Server is a **relay-only server** that acts as a central hub for video streaming:

```
Presenter Client          ScreenAI-Server         Viewer Clients
(Captures & Encodes)      (Relays Only)          (Decode & Display)
      │                         │                        │
      │ H.264 video chunks     │                        │
      │═══════════════════════►│                        │
      │    (Binary WebSocket)  │  Relay to all viewers │
      │                        │═══════════════════════►│
      │                        │═══════════════════════►│
      │                        │═══════════════════════►│
```

### **🔑 Key Principle**
- ✅ **Clients** handle CPU-intensive tasks (screen capture, H.264 encoding)
- ✅ **Server** handles only network relay (minimal CPU usage)
- ✅ **Result**: Highly scalable architecture supporting 100+ concurrent sessions

---

## ✨ Features

### **Core Capabilities**
- ✅ **WebSocket Binary Relay** - Forward H.264 video chunks from presenters to viewers
- ✅ **Room-Based Architecture** - Isolated streaming rooms with one presenter per room
- ✅ **Multi-Viewer Broadcasting** - One presenter can stream to unlimited viewers
- ✅ **Init Segment Caching** - Late joiners receive cached H.264 init segment instantly
- ✅ **Session Management** - Automatic room cleanup when presenter disconnects
- ✅ **Performance Monitoring** - Real-time CPU, memory, and throughput metrics
- ✅ **Connection Limits** - Configurable max connections and session timeouts
- ✅ **Graceful Shutdown** - Notify clients and cleanup before server restart

---

## 🛠️ Technology Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Framework** | Spring Boot | 3.1.5 | Web application framework |
| **WebSocket** | Spring WebSocket | 6.0.x | Real-time binary communication |
| **Java** | OpenJDK | 17+ | Programming language |
| **Build Tool** | Maven | 3.9.x | Dependency management |
| **Server** | Embedded Tomcat | 10.1.x | HTTP/WebSocket server |

---

## 📋 System Requirements

### **Server Requirements**
- **Java:** JDK 17 or higher
- **Memory:** 2GB RAM minimum, 4GB recommended
- **CPU:** 2+ cores (very low CPU usage ~5-10% per session)
- **Network:** 1 Gbps network (main bottleneck)
- **OS:** Windows, macOS, Linux (platform independent)

### **Estimated Capacity**
| Metric | Typical Load | High Load |
|--------|--------------|-----------|
| **Concurrent Rooms** | 10-20 rooms | 50-100 rooms |
| **Total Viewers** | 100-200 viewers | 500-1000 viewers |
| **Bandwidth** | 50-100 Mbps | 500 Mbps - 1 Gbps |
| **CPU Usage** | 10-20% | 30-50% |
| **Memory** | 200-400 MB | 500-800 MB |

---

## 🚀 Quick Start

### **1. Prerequisites**
Ensure you have Java 17+ installed:
```bash
java -version
# Should show: openjdk version "17" or higher
```

### **2. Clone the Repository**
```bash
git clone https://github.com/vijayvir/ScreenAi.git
cd ScreenAi
```

### **3. Build the Application**
```bash
./mvnw clean package
```

This creates an executable JAR: `target/screenai-server-1.0.0.jar` (~40 MB)

### **4. Run the Server**
```bash
java -jar target/screenai-server-1.0.0.jar
```

Or using Maven:
```bash
./mvnw spring-boot:run
```

### **5. Server Started Successfully!**
You should see:
```
═══════════════════════════════════════════════════════
   ScreenAI-Server (Relay Mode) Started Successfully   
═══════════════════════════════════════════════════════

📍 WebSocket Endpoint:
   Local:   ws://localhost:8080/screenshare
   Network: ws://192.168.1.100:8080/screenshare

🔧 Server Mode: RELAY ONLY
   ✅ Room management enabled
   ✅ Binary data relay enabled
   ✅ Performance monitoring enabled
```

---

## 🔌 WebSocket Protocol

### **Connection URL**
```
ws://localhost:8080/screenshare
```

### **Message Types**

#### **1. Create Room (Presenter)**
**Request:**
```json
{
  "type": "create-room",
  "roomId": "meeting-123"
}
```

**Response:**
```json
{
  "type": "room-created",
  "roomId": "meeting-123",
  "role": "presenter"
}
```

#### **2. Join Room (Viewer)**
**Request:**
```json
{
  "type": "join-room",
  "roomId": "meeting-123"
}
```

**Response:**
```json
{
  "type": "room-joined",
  "roomId": "meeting-123",
  "role": "viewer",
  "viewerCount": 3
}
```

#### **3. Binary Video Data (Presenter Only)**
Presenters send raw H.264 fMP4 binary data via WebSocket.

Server automatically:
- ✅ Detects init segments (ftyp/moov boxes)
- ✅ Caches init segment for late joiners
- ✅ Relays all data to viewers in the same room

---

## 🧪 Testing the Server

### **Using wscat (WebSocket CLI)**

**Install wscat:**
```bash
npm install -g wscat
```

**Create a Room (Presenter):**
```bash
wscat -c ws://localhost:8080/screenshare
> {"type":"create-room","roomId":"test-room"}
< {"type":"room-created","roomId":"test-room","role":"presenter"}
```

**Join Room (Viewer):**
```bash
wscat -c ws://localhost:8080/screenshare
> {"type":"join-room","roomId":"test-room"}
< {"type":"room-joined","roomId":"test-room","role":"viewer","viewerCount":1}
```

See **[TESTING_GUIDE.md](TESTING_GUIDE.md)** for complete testing scenarios.

---

## ⚙️ Configuration

### **application.yml**
```yaml
server:
  port: 8080

spring:
  websocket:
    max-text-message-size: 65536      # 64 KB
    max-binary-message-size: 1048576  # 1 MB

logging:
  level:
    com.screenai: INFO
```

### **Environment Variables**
```bash
# Custom port
SERVER_PORT=9090 java -jar screenai-server-1.0.0.jar
```

---

## 🏗️ Project Structure

```
ScreenAi/
├── src/main/java/com/screenai/
│   ├── ScreenAIApplication.java          # Main entry point
│   ├── config/
│   │   └── WebSocketConfig.java          # WebSocket config
│   ├── handler/
│   │   └── ScreenShareRelayHandler.java  # Message handler
│   ├── service/
│   │   ├── SessionManager.java           # Room management
│   │   ├── StreamRelayService.java       # Video relay
│   │   └── PerformanceMonitorService.java
│   └── model/
│       ├── Room.java
│       └── PerformanceMetrics.java
├── pom.xml
└── target/
    └── screenai-server-1.0.0.jar        # ~40 MB
```

---

## 📈 Performance

**Benchmarks** (4-core, 8GB RAM):

| Scenario | Rooms | Viewers | CPU | Memory | Bandwidth |
|----------|-------|---------|-----|--------|-----------|
| Light | 5 | 25 | 8% | 180 MB | 50 Mbps |
| Medium | 20 | 100 | 15% | 300 MB | 200 Mbps |
| Heavy | 50 | 250 | 30% | 500 MB | 500 Mbps |
| Max | 100 | 500 | 50% | 800 MB | 1 Gbps |

---


## 🐛 Troubleshooting

**Port already in use:**
```bash
lsof -i :8080
java -jar -Dserver.port=9090 screenai-server-1.0.0.jar
```

**WebSocket connection fails:**
```bash
wscat -c ws://localhost:8080/screenshare
tail -f logs/screenai-server.log
```

---

