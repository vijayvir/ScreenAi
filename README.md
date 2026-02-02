# ScreenAI-Server

A **secure WebSocket relay server** for real-time screen sharing. Built with **Spring Boot WebFlux + Netty** for high-performance, non-blocking video streaming with comprehensive authentication and security features.

## 🎯 Overview

ScreenAI-Server acts as a secure relay hub between presenters (screen sharers) and viewers:

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│    Presenter    │         │  ScreenAI Server│         │     Viewers     │
│  (Screen Share) │         │  (WebFlux+Netty)│         │   (Watch Feed)  │
└────────┬────────┘         └────────┬────────┘         └────────┬────────┘
         │                           │                           │
         │  1. Login/Register        │                           │
         │──────────────────────────►│                           │
         │  ◄── JWT Token ──────────│                           │
         │                           │                           │
         │  2. create-room (+ auth)  │                           │
         │──────────────────────────►│                           │
         │  ◄── accessCode ─────────│                           │
         │                           │                           │
         │                           │  3. join-room + accessCode│
         │                           │◄──────────────────────────│
         │                           │                           │
         │  4. Binary video frames   │  5. Relay to viewers      │
         │══════════════════════════►│══════════════════════════►│
         │      (H.264 fMP4)         │      (H.264 fMP4)         │
```

## ✨ Features

### Core Features
- ✅ **Reactive WebSocket Relay** - Non-blocking I/O with Spring WebFlux + Netty
- ✅ **Room-Based Architecture** - Isolated streaming rooms (1 presenter, multiple viewers)
- ✅ **Binary Video Streaming** - H.264/fMP4 video relay support
- ✅ **Init Segment Caching** - Late joiners receive cached init segment instantly
- ✅ **Auto Backpressure** - Handles slow consumers gracefully
- ✅ **Low Resource Usage** - Minimal CPU (~5-15%), server only relays data

### 🔐 Security Features
- ✅ **JWT Authentication** - Secure token-based auth (15 min access + 7 day refresh tokens)
- ✅ **User Registration** - Secure account creation with encrypted passwords (BCrypt)
- ✅ **Account Lockout** - 5 failed attempts → 15 min lock
- ✅ **Password Policy** - Min 8 chars, uppercase, lowercase, digit, special char
- ✅ **Rate Limiting** - Message rate limiting per session/IP
- ✅ **IP Blocking** - Automatic blocking of suspicious IPs
- ✅ **Room Password Protection** - Optional password for private rooms
- ✅ **Access Codes** - Auto-generated codes for password-protected rooms
- ✅ **Viewer Approval** - Optional manual approval for viewers joining
- ✅ **Viewer Management** - Kick/ban viewers from rooms
- ✅ **Audit Logging** - All security events recorded with masked usernames
- ✅ **Role-Based Access** - ADMIN/USER roles for API endpoints
- ✅ **Token Refresh** - Automatic token renewal without re-login

---

## 🚀 Quick Start

### Prerequisites

- **Java 17+** (Java 21 recommended)

```bash
java -version
# Should show: openjdk version "17" or higher
```

### Run the Server

**Option 1: Using Maven Wrapper**
```bash
chmod +x mvnw
./mvnw spring-boot:run
```

**Option 2: Using pre-built JAR**
```bash
java -jar target/screenai-server-1.0.0.jar
```

### Server Started!

```
═══════════════════════════════════════════════════════
   ScreenAI-Server (Relay Mode) Started Successfully   
═══════════════════════════════════════════════════════

📍 WebSocket Endpoint:
   Local:   ws://localhost:8080/screenshare
   Network: ws://<your-ip>:8080/screenshare

🔐 Security: ENABLED
   ✅ JWT Authentication
   ✅ Room password protection
   ✅ Rate limiting active

🔧 Server Mode: RELAY ONLY
   ✅ Room management enabled
   ✅ Binary data relay enabled
```

---

## 🔐 Authentication API

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/auth/register` | POST | Register new user |
| `/api/auth/login` | POST | Login and get JWT tokens |
| `/api/auth/refresh` | POST | Refresh access token |
| `/api/auth/logout` | POST | Invalidate refresh token |

### Register User

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "SecurePass123!"
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "User registered successfully",
  "username": "testuser"
}
```

### Login

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "SecurePass123!"
  }'
```

**Response:**
```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "username": "testuser",
  "expiresIn": 900
}
```

### Refresh Token

**Request:**
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }'
```

---

## 🔌 WebSocket Protocol

### Endpoint
```
ws://localhost:8080/screenshare
```

### Authentication
Include JWT token in WebSocket connection or send after connecting:
```json
{"type": "auth", "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}
```

### Message Types

#### 1. Create Room (Presenter)

**Basic Room:**
```json
{"type": "create-room", "roomId": "my-room-123"}
```

**Password-Protected Room:**
```json
{
  "type": "create-room",
  "roomId": "secure-room",
  "password": "roomPass123",
  "requireApproval": false
}
```

**Response:**
```json
{
  "type": "room-created",
  "roomId": "secure-room",
  "role": "presenter",
  "accessCode": "ABC123",
  "message": "Room created successfully"
}
```

#### 2. Join Room (Viewer)

**Public Room:**
```json
{"type": "join-room", "roomId": "my-room-123"}
```

**Password-Protected Room (with access code):**
```json
{
  "type": "join-room",
  "roomId": "secure-room",
  "accessCode": "ABC123"
}
```

**Response:**
```json
{
  "type": "room-joined",
  "roomId": "secure-room",
  "role": "viewer",
  "hasPresenter": true
}
```

#### 3. Viewer Management (Presenter only)

**Approve Viewer:**
```json
{"type": "approve-viewer", "viewerSessionId": "session-id-123"}
```

**Deny Viewer:**
```json
{"type": "deny-viewer", "viewerSessionId": "session-id-123"}
```

**Kick Viewer:**
```json
{"type": "kick-viewer", "viewerSessionId": "session-id-123"}
```

**Ban Viewer:**
```json
{"type": "ban-viewer", "viewerSessionId": "session-id-123"}
```

#### 4. Leave Room

```json
{"type": "leave-room"}
```

#### 5. Get Viewer Count (Presenter only)

```json
{"type": "get-viewer-count"}
```

**Response:**
```json
{"type": "viewer-count", "count": 5}
```

---

## 📨 Server Events

| Event | Description |
|-------|-------------|
| `connected` | Connection established |
| `room-created` | Room created (includes `accessCode` if password-protected) |
| `room-joined` | Joined room as viewer |
| `waiting` | Room exists but no presenter yet |
| `presenter-joined` | Presenter started streaming |
| `presenter-left` | Presenter disconnected |
| `viewer-joined` | New viewer joined |
| `viewer-left` | Viewer disconnected |
| `viewer-request` | Viewer requesting approval (if `requireApproval` enabled) |
| `viewer-approved` | Viewer was approved |
| `viewer-denied` | Viewer was denied |
| `viewer-kicked` | Viewer was kicked |
| `viewer-banned` | Viewer was banned |
| `room-closed` | Room was closed |
| `error` | Error occurred |

---

## 🏗️ Project Structure

```
src/main/java/com/screenai/
├── ScreenAIApplication.java      # Main application entry
├── config/
│   ├── SecurityConfig.java       # Spring Security configuration
│   ├── WebSocketConfig.java      # WebSocket configuration
│   └── JwtConfig.java            # JWT settings
├── controller/
│   └── AuthController.java       # REST API for auth
├── dto/
│   ├── AuthRequest.java          # Login/Register request
│   ├── AuthResponse.java         # Auth response with tokens
│   └── RefreshRequest.java       # Token refresh request
├── handler/
│   └── ScreenShareHandler.java   # WebSocket message handler
├── model/
│   ├── User.java                 # User entity
│   ├── Room.java                 # Room entity with security
│   └── RefreshToken.java         # Refresh token entity
├── repository/
│   ├── UserRepository.java       # User persistence
│   └── RefreshTokenRepository.java
├── security/
│   ├── JwtTokenProvider.java     # JWT generation/validation
│   ├── JwtAuthenticationFilter.java
│   ├── RateLimiter.java          # Rate limiting
│   └── IpBlocker.java            # IP blocking
├── service/
│   ├── AuthService.java          # Authentication logic
│   ├── RoomService.java          # Room management
│   └── UserService.java          # User management
└── validation/
    └── PasswordValidator.java    # Password policy enforcement
```

---

## ⚙️ Configuration

### application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:mem:screenai
    driver-class-name: org.h2.Driver

jwt:
  secret: your-256-bit-secret-key-here
  access-token-expiration: 900000      # 15 minutes
  refresh-token-expiration: 604800000  # 7 days

security:
  max-failed-attempts: 5
  lockout-duration: 900000             # 15 minutes
  rate-limit:
    messages-per-second: 60
    burst-size: 100
```

---

## 🧪 Testing

### Using wscat

```bash
# Install wscat
npm install -g wscat

# Connect and authenticate
wscat -c ws://localhost:8080/screenshare
> {"type":"auth","token":"your-jwt-token"}

# Create a password-protected room
> {"type":"create-room","roomId":"test","password":"secret123"}

# Join the room (Viewer - new terminal)
wscat -c ws://localhost:8080/screenshare
> {"type":"auth","token":"viewer-jwt-token"}
> {"type":"join-room","roomId":"test","accessCode":"ABC123"}
```

---

## 📊 Performance

| Metric | Value |
|--------|-------|
| Connections | 1000+ concurrent |
| Latency | < 50ms relay |
| CPU Usage | 5-15% (relay only) |
| Memory | ~256MB base |

---

## 🛡️ Security Best Practices

1. **Change JWT Secret** - Use a strong 256-bit secret in production
2. **Use HTTPS/WSS** - Enable TLS in production
3. **Configure CORS** - Restrict allowed origins
4. **Database** - Use PostgreSQL/MySQL in production instead of H2
5. **Monitoring** - Enable audit log monitoring
6. **Rate Limits** - Adjust based on expected traffic

---

## 📄 License

MIT License - See [LICENSE](LICENSE) file

