# ScreenAI-Server

A **secure WebSocket relay server** for real-time screen sharing. Built with **Spring Boot WebFlux + Netty** for high-performance, non-blocking video streaming with comprehensive authentication and security features.

> **📖 For detailed security documentation, see [SECURITY.md](docs/SECURITY.md)**

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/ARCHITECTURE.md) | System architecture, tech stack, project structure, database schema, design decisions |
| [API Reference](docs/API.md) | Complete REST API + WebSocket protocol documentation with examples |
| [Setup Guide](docs/SETUP.md) | Installation, build, run, production deployment (PostgreSQL, nginx, systemd) |
| [Configuration](docs/CONFIGURATION.md) | Full reference for all `application.yml` settings and environment variables |
| [Security](docs/SECURITY.md) | Multi-layered security architecture, JWT, rate limiting, encryption, audit logging |

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

### 🔐 Security Features ([full docs](SECURITY.md))
- ✅ **JWT Authentication** - HMAC-SHA256 signed tokens (15 min access + 7 day opaque refresh tokens with rotation)
- ✅ **User Registration** - Secure account creation with BCrypt hashing (cost factor 12)
- ✅ **Account Lockout** - 5 failed attempts → 15 min lock (configurable)
- ✅ **Password Policy** - Min 8 chars, uppercase, lowercase, digit, special char (configurable)
- ✅ **Rate Limiting** - Sliding window per session (100 msg/sec) and per IP (10 rooms/hour)
- ✅ **IP Blocking** - Dual-layer (memory + DB) automatic blocking of suspicious IPs
- ✅ **Room Password Protection** - SHA-256 + salt hashing with timing-safe comparison
- ✅ **Access Codes** - 8-char alphanumeric codes (24-hour expiry) for password-protected rooms
- ✅ **Viewer Approval** - Optional manual approve/deny workflow for viewers
- ✅ **Viewer Management** - Kick/ban viewers from rooms (banned sessions cannot rejoin)
- ✅ **Audit Logging** - 25+ event types with privacy-masked usernames and severity levels
- ✅ **Role-Based Access** - ADMIN/USER roles with URL-pattern and method-level enforcement
- ✅ **Token Refresh** - Automatic token renewal with refresh token rotation
- ✅ **Security Headers** - CSP, X-XSS-Protection, Permissions-Policy, and more
- ✅ **Input Validation** - Centralized sanitization and validation for all user inputs
- ✅ **Encrypted Client Storage** - AES-256-GCM with PBKDF2 key derivation for token persistence

---

## 🚀 Quick Start

### Prerequisites

- **Java 17+** (Java 21 recommended)

```bash
java -version
# Should show: openjdk version "17" or higher
```

### Run the Server

**Option 1: Quick Start (dev mode — no admin account, random JWT key)**
```bash
mvn spring-boot:run
```

**Option 2: With Admin Account + Persistent JWT**
```bash
export JWT_SECRET="your-super-secure-256-bit-secret-key-here!!"
export ADMIN_PASSWORD="Admin@123"
mvn spring-boot:run
```

**Option 3: Using pre-built JAR**
```bash
java -jar target/screenai-server-1.0.0.jar
```

> **Note:** If `JWT_SECRET` is not set, a random key is generated on each startup (tokens won't survive restarts). If `ADMIN_PASSWORD` is not set, no admin user is created.

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

## 🛡️ Security

ScreenAI implements a multi-layered security architecture including:

| Layer | Features |
|-------|----------|
| **Network** | IP blocking, rate limiting (sliding window), connection throttling, security headers |
| **Authentication** | JWT access tokens (HS256, 15 min), opaque refresh tokens (7 day), BCrypt password hashing (cost 12) |
| **Authorization** | Role-based access control (USER/ADMIN), room-level permissions, viewer approval workflow |
| **Data Protection** | AES-256-GCM encrypted client storage, SHA-256 + salt room passwords (timing-safe), input validation |
| **Audit** | 25+ event types, 5 severity levels, privacy-masked usernames, queryable by user/type/severity |

> **Full details →** [SECURITY.md](docs/SECURITY.md)

### Security Best Practices for Production

1. **Change JWT Secret** — Set `JWT_SECRET` env var with a strong 256-bit+ key (if not set, a random key is generated per startup)
2. **Set Admin Password** — Set `ADMIN_PASSWORD` env var to bootstrap an admin account (skipped if blank)
3. **Configure CORS** — Restrict `cors.allowed-origins` to your domains
4. **Database** — Replace H2 with PostgreSQL/MySQL for persistence
5. **Encryption Key** — Set `TOKEN_ENCRYPTION_KEY` in client `.env` with a unique 32-char key
6. **Monitoring** — Set up alerts on `CRITICAL` and `ERROR` severity audit events
7. **Rate Limits** — Tune based on expected traffic patterns
8. **Reverse Proxy** — Place behind nginx/Caddy; don't expose port 8080 directly

---

## 📄 License

MIT License - See [LICENSE](LICENSE) file

