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

### Core Features
- ✅ **Reactive WebSocket Relay** - Non-blocking I/O with Spring WebFlux + Netty
- ✅ **Room-Based Architecture** - Isolated streaming rooms (1 presenter, multiple viewers)
- ✅ **Binary Video Streaming** - H.264/fMP4 video relay support
- ✅ **Init Segment Caching** - Late joiners receive cached init segment instantly
- ✅ **Auto Backpressure** - Handles slow consumers gracefully
- ✅ **Low Resource Usage** - Minimal CPU (~5-15%), server only relays data

### Security Features
- ✅ **JWT Authentication** - Secure token-based auth (15 min access + refresh tokens)
- ✅ **Account Lockout** - 5 failed attempts → 15 min lock
- ✅ **Password Policy** - Min 8 chars, uppercase, lowercase, digit, special char
- ✅ **Rate Limiting** - Message rate limiting per session/IP
- ✅ **IP Blocking** - Automatic blocking of suspicious IPs
- ✅ **Room Password Protection** - Optional password for private rooms
- ✅ **Audit Logging** - All security events recorded with masked usernames
- ✅ **Role-Based Access** - ADMIN/USER roles for API endpoints

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
│   ├── DatabaseInitializer.java          # H2 database setup
│   ├── JacksonConfig.java                # JSON serialization
│   └── WebFluxWebSocketConfig.java       # WebSocket configuration
├── controller/
│   ├── AuthController.java               # Login, Register, Token Refresh
│   ├── AdminController.java              # Audit logs, IP blocking
│   └── PerformanceController.java        # Performance metrics
├── dto/
│   ├── AuthResponse.java                 # Auth response DTO
│   ├── LoginRequest.java                 # Login request DTO
│   ├── RegisterRequest.java              # Register request DTO
│   └── RefreshTokenRequest.java          # Token refresh DTO
├── handler/
│   └── ReactiveScreenShareHandler.java   # WebSocket message handler
├── model/
│   ├── ReactiveRoom.java                 # Room state with security
│   ├── User.java                         # User entity with lockout
│   ├── AuditEvent.java                   # Audit event entity
│   ├── BlockedIp.java                    # Blocked IP entity
│   └── PerformanceMetrics.java           # Metrics model
├── repository/
│   ├── UserRepository.java               # User data access
│   ├── AuditEventRepository.java         # Audit log data access
│   └── BlockedIpRepository.java          # Blocked IP data access
├── security/
│   ├── SecurityConfig.java               # Spring Security config
│   ├── JwtAuthenticationFilter.java      # JWT filter
│   └── WebSocketAuthHandler.java         # WebSocket authentication
├── service/
│   ├── AuthService.java                  # Authentication logic
│   ├── JwtService.java                   # JWT token generation
│   ├── RateLimitService.java             # Message rate limiting
│   ├── ConnectionThrottleService.java    # IP throttling
│   ├── RoomSecurityService.java          # Room password/approval
│   ├── SecurityAuditService.java         # Event logging
│   └── PerformanceMonitorService.java    # Performance tracking
└── validation/
    └── InputValidator.java               # Input validation

```

---

## 🔐 Authentication Flow

```
┌──────────┐     POST /api/auth/login      ┌──────────────────┐
│  Client  │ ─────────────────────────────►│  AuthController  │
└──────────┘  {username, password}         └────────┬─────────┘
                                                    │
                                                    ▼
                                           ┌───────────────────┐
                                           │   AuthService     │
                                           │ - Validate pass   │
                                           │ - Check lockout   │
                                           │ - Generate tokens │
                                           └────────┬──────────┘
                                                    │
                     ┌──────────────────────────────┴─────────────────────────────┐
                     ▼                                                            ▼
           ┌─────────────────┐                                         ┌──────────────────┐
           │   JwtService    │                                         │ SecurityAuditSvc │
           │ - accessToken   │                                         │ - LOGIN_SUCCESS  │
           │ - refreshToken  │                                         │ - LOGIN_FAILURE  │
           └─────────────────┘                                         └──────────────────┘
```

---

## 📺 Screen Sharing WebSocket Flow

```
┌──────────────┐                                              ┌───────────────┐
│  PRESENTER   │                                              │    VIEWERS    │
└──────┬───────┘                                              └───────┬───────┘
       │                                                              │
       │ 1. Connect WS + JWT Token                                    │
       │─────────────────────────────►┌────────────────────┐          │
       │                              │ ReactiveScreenShare │          │
       │                              │     Handler         │          │
       │                              └─────────┬──────────┘          │
       │                                        │                      │
       │ 2. "create-room"                       │ Auth + Rate Check   │
       │─────────────────────────────►┌─────────▼──────────┐          │
       │                              │   Create Room      │          │
       │◄─────────────────────────────│   {roomId, role}   │          │
       │   "room-created"             └────────────────────┘          │
       │                                                              │
       │                                        │ 3. "join-room"      │
       │                              ┌─────────▼──────────┐◄─────────│
       │                              │   Add Viewer       │          │
       │                              │   (password check) │──────────│
       │                              └────────────────────┘ "viewer-joined"
       │                                                              │
       │ 4. Binary Video Frames (H.264/fMP4)                          │
       │═══════════════════════════►┌────────────────────┐            │
       │                            │   RELAY to all     │════════════│
       │                            │   viewers          │            │
       │                            └────────────────────┘            │
```

---

## 🛡️ Security Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                        Security Stack                            │
├─────────────────────────────────────────────────────────────────┤
│  Layer 1: IP Blocking (ConnectionThrottleService)               │
│  └─ Blocks IPs with too many failed connections                 │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: Rate Limiting (RateLimitService)                      │
│  └─ Limits messages per session/IP                              │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: JWT Authentication (JwtAuthenticationFilter)          │
│  └─ Validates tokens for REST & WebSocket                       │
├─────────────────────────────────────────────────────────────────┤
│  Layer 4: Role-Based Access (SecurityConfig)                    │
│  └─ ADMIN role required for /api/admin/**                       │
├─────────────────────────────────────────────────────────────────┤
│  Layer 5: Input Validation (InputValidator)                     │
│  └─ Validates roomId, binary size, JSON payloads                │
├─────────────────────────────────────────────────────────────────┤
│  Layer 6: Room Security (RoomSecurityService)                   │
│  └─ Password protection, viewer approval, banning               │
├─────────────────────────────────────────────────────────────────┤
│  Layer 7: Audit Logging (SecurityAuditService)                  │
│  └─ Logs all security events with masked usernames              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔑 REST API Endpoints

### Authentication (No token required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/login` | Login and get tokens |
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/refresh` | Refresh access token |

### Admin (Requires ADMIN Bearer token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/logs` | Get all audit logs |
| `GET` | `/api/admin/logs?limit=50&offset=0` | Paginated logs |
| `GET` | `/api/admin/logs/user/{username}` | Logs by username |
| `GET` | `/api/admin/logs/type/{eventType}` | Logs by event type |
| `GET` | `/api/admin/logs/severity/{severity}` | Logs by severity |
| `GET` | `/api/admin/blocked-ips` | Get blocked IPs |

### Default Admin User

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Admin@123` | ADMIN |

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
| Security | Spring Security | 6.x |
| Database | H2 (In-Memory) | 2.x |
| Java | OpenJDK | 17+ |
| Build | Maven | 3.9.x |

---

## 🎮 Quick Start Flow

1. **Start Server**: `mvn spring-boot:run`
2. **Login** (get token): `POST /api/auth/login` with `admin`/`Admin@123`
3. **Connect WebSocket**: `ws://localhost:8080/screenshare?token=<JWT>`
4. **Create Room**: Send `{"type": "create-room", "roomId": "my-room"}`
5. **Viewers Join**: Connect with `{"type": "join-room", "roomId": "my-room"}`
6. **Stream Video**: Send binary H.264/fMP4 frames
7. **View Logs**: `GET /api/admin/logs` (with Bearer token)

---

## 📋 Security Testing

See [SECURITY_TEST.md](SECURITY_TEST.md) for complete security testing guide with Postman.

### Security Features Summary

| Feature | How to Test | Expected Result |
|---------|-------------|-----------------|
| JWT Authentication | Login → Use token | Token works for 15 min |
| Password Validation | Register with weak password | Rejected |
| Account Lockout | 5 wrong passwords | Account locked 15 min |
| Audit Logging | Any action → Check logs | Event recorded |
| Username Masking | View logs | Usernames show as `ad***in` |
| Role-Based Access | USER tries admin endpoint | 403 Forbidden |

---

## 📄 Related Projects

- **[ScreenAI-Client](https://github.com/vijayvir/ScreenAiClient)** - JavaFX desktop client for screen sharing

---



# ScreenAI Security Testing Guide

A beginner-friendly guide to test all security features using Postman.

---

## Prerequisites

1. **Download Postman**: https://www.postman.com/downloads/
2. **Start the Server**: Run `mvn spring-boot:run` in the ScreenAi folder
3. **Verify Server is Running**: Open http://localhost:8080 in browser

---

## Default Users

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Admin@123` | ADMIN |

---

## Step 1: Login (Get Your Token)

| Setting | Value |
|---------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/auth/login` |

### Setup in Postman:
1. Click **"+"** to create a new request tab
2. Change **GET** to **POST** (click the dropdown)
3. Enter URL: `http://localhost:8080/api/auth/login`
4. Click **"Body"** tab (below the URL)
5. Select **"raw"**
6. Change **"Text"** to **"JSON"** (dropdown on the right)
7. Paste this in the body:

```json
{
    "username": "admin",
    "password": "Admin@123"
}
```

8. Click **"Send"** (blue button)

### Expected Response:
```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "abc123...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "username": "admin",
    "role": "ADMIN"
}
```

> ⚠️ **Important**: Copy the `accessToken` value - you'll need it for protected endpoints!

---

## Step 2: Register New User

| Setting | Value |
|---------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/auth/register` |

### Body (raw JSON):
```json
{
    "username": "testuser",
    "password": "Test@123456"
}
```

### Password Requirements:
- ✅ At least 8 characters
- ✅ At least 1 uppercase letter (A-Z)
- ✅ At least 1 lowercase letter (a-z)
- ✅ At least 1 digit (0-9)
- ✅ At least 1 special character (!@#$%^&*)

### Username Requirements:
- 3-32 characters
- Only letters, numbers, underscores, hyphens

### Expected Response:
```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "xyz789...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "username": "testuser",
    "role": "USER"
}
```

---

## Step 3: Access Protected Endpoint (Admin Logs)

| Setting | Value |
|---------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/admin/logs` |

### Setup in Postman:
1. Create new request tab (**+**)
2. Method: **GET**
3. URL: `http://localhost:8080/api/admin/logs`
4. Click **"Headers"** tab
5. Add a new header:
   - **Key**: `Authorization`
   - **Value**: `Bearer eyJhbGciOiJIUzI1NiJ9...` (paste your token after "Bearer ")
6. Click **Send**

### Expected Response:
```json
[
    {
        "id": 1,
        "eventType": "LOGIN_SUCCESS",
        "username": "ad***in",
        "ipAddress": "0:0:0:0:0:0:0:1",
        "details": "User logged in successfully",
        "severity": "INFO",
        "createdAt": "2026-01-24T17:48:30.981297"
    }
]
```

---

## Step 4: Test Account Lockout (5 Failed Attempts)

| Setting | Value |
|---------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/auth/login` |

### Body (raw JSON) - Wrong Password:
```json
{
    "username": "admin",
    "password": "WrongPassword"
}
```

### Test Steps:
1. Click **Send** - Attempt 1
2. Click **Send** - Attempt 2
3. Click **Send** - Attempt 3
4. Click **Send** - Attempt 4
5. Click **Send** - Attempt 5

### Expected Response After 5 Attempts:
```json
{
    "tokenType": "Bearer",
    "expiresIn": 0,
    "message": "Account is locked"
}
```

> ⚠️ **Note**: Account will be locked for 15 minutes. Restart server to reset.

---

## Step 5: Test Token Refresh

| Setting | Value |
|---------|-------|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/auth/refresh` |

### Body (raw JSON):
```json
{
    "refreshToken": "paste_your_refresh_token_here"
}
```

### Expected Response:
```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...(new token)",
    "refreshToken": "newRefreshToken...",
    "tokenType": "Bearer",
    "expiresIn": 900
}
```

---

## Step 6: View Blocked IPs

| Setting | Value |
|---------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/admin/blocked-ips` |
| **Headers** | `Authorization: Bearer <your_token>` |

### Expected Response:
```json
[]
```

> Empty array means no IPs are blocked (this is normal for new installation)

---

## Step 7: View Logs by User

| Setting | Value |
|---------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/admin/logs/user/admin` |
| **Headers** | `Authorization: Bearer <your_token>` |

### Expected Response:
```json
[
    {
        "id": 1,
        "eventType": "LOGIN_SUCCESS",
        "username": "ad***in",
        ...
    }
]
```

---

## Quick Reference: All Endpoints

### Authentication (No token required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/login` | Login and get tokens |
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/refresh` | Refresh access token |

### Admin (Requires Bearer token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/logs` | Get all audit logs |
| `GET` | `/api/admin/logs?limit=50&offset=0` | Paginated logs |
| `GET` | `/api/admin/logs/user/{username}` | Logs by username |
| `GET` | `/api/admin/logs/type/{eventType}` | Logs by event type |
| `GET` | `/api/admin/logs/severity/{severity}` | Logs by severity |
| `GET` | `/api/admin/blocked-ips` | Get blocked IPs |

---

## Common Errors & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `405 Method Not Allowed` | Wrong HTTP method | Use POST for login, GET for logs |
| `401 Unauthorized` | Missing/expired token | Login again to get new token |
| `403 Forbidden` | User lacks admin role | Login as admin |
| `400 Bad Request` | Invalid data format | Check JSON body format |
| `Connection refused` | Server not running | Start the server |
| `Account is locked` | 5 failed login attempts | Wait 15 min or restart server |

---

## Security Features Tested

| Feature | How to Test | Expected Result |
|---------|-------------|-----------------|
| JWT Authentication | Login → Use token | Token works for 15 min |
| Password Validation | Register with weak password | Rejected |
| Account Lockout | 5 wrong passwords | Account locked 15 min |
| Audit Logging | Any action → Check logs | Event recorded |
| Username Masking | View logs | Usernames show as `ad***in` |
| Role-Based Access | USER tries admin endpoint | 403 Forbidden |

---

## Visual Guide: Postman Layout

```
┌─────────────────────────────────────────────────────────────┐
│  [POST ▼]  http://localhost:8080/api/auth/login    [Send]   │
├─────────────────────────────────────────────────────────────┤
│  Params   Authorization   Headers   [Body]   Pre-request    │
├─────────────────────────────────────────────────────────────┤
│  ○ none  ○ form-data  ○ x-www...  ● raw  ○ binary  [JSON ▼] │
├─────────────────────────────────────────────────────────────┤
│  {                                                          │
│      "username": "admin",                                   │
│      "password": "Admin@123"                                │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## Event Types in Audit Logs

| Event Type | Description |
|------------|-------------|
| `LOGIN_SUCCESS` | User logged in successfully |
| `LOGIN_FAILURE` | Failed login attempt |
| `REGISTRATION_SUCCESS` | New user registered |
| `REGISTRATION_FAILURE` | Registration failed |
| `TOKEN_REFRESH` | Access token refreshed |
| `LOGOUT` | User logged out |

---

## Severity Levels

| Severity | Meaning |
|----------|---------|
| `INFO` | Normal operation |
| `WARN` | Something to watch |
| `ERROR` | Something went wrong |
| `CRITICAL` | Serious security issue |

---

*Last Updated: January 2026*
