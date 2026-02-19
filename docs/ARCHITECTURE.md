# ScreenAI Server — Architecture

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Framework | Spring Boot 3.5.5 + **WebFlux** | Reactive, non-blocking server |
| Server | **Netty** (embedded) | High-performance async I/O (not Tomcat) |
| Database | **R2DBC** + H2 (in-memory) | Reactive database access |
| Auth | **JWT** (HMAC-SHA256) + BCrypt | Stateless token-based authentication |
| WebSocket | Reactor Netty WebSocket | Binary video relay + JSON control messages |
| Security | Spring Security WebFlux | Filters, RBAC, headers, CORS |
| Build | Maven 3.x | Dependency management |
| Java | 17+ (21 recommended) | Language runtime |

---

## High-Level Architecture

ScreenAI Server is a **relay-only** server. It does not process video — it receives H.264 binary frames from a presenter and relays them to all viewers in the same room.

```
                          ┌─────────────────────────────┐
                          │      ScreenAI Server         │
                          │   (Spring WebFlux + Netty)   │
                          ├─────────────────────────────┤
                          │                              │
  Presenter ──────────────┤►  ReactiveScreenShareHandler │──────────── Viewer 1
  (Binary H.264 frames)   │     (WebSocket Handler)      │  (Relayed   Viewer 2
                          │          │                   │   binary    Viewer 3
  REST Clients ───────────┤►  AuthController             │   frames)   ...
  (Login/Register)        │   AdminController            │
                          │   PerformanceController      │
                          │          │                   │
                          │   ┌──────▼──────┐            │
                          │   │   Services   │            │
                          │   │  AuthService │            │
                          │   │  JwtService  │            │
                          │   │  RateLimit   │            │
                          │   │  Audit       │            │
                          │   │  RoomSecurity│            │
                          │   └──────┬──────┘            │
                          │          │                   │
                          │   ┌──────▼──────┐            │
                          │   │   R2DBC      │            │
                          │   │  (H2 in-mem) │            │
                          │   └─────────────┘            │
                          └─────────────────────────────┘
```

---

## Request Flow

### HTTP Requests (REST API)

```
HTTP Request
    │
    ▼
SecurityConfig (CORS, CSRF disabled, headers)
    │
    ▼
JwtAuthenticationFilter (extracts + validates JWT)
    │
    ▼
Authorization Rules (permitAll for /api/auth/**, ADMIN for /api/admin/**)
    │
    ▼
Controller (AuthController / AdminController / PerformanceController)
    │
    ▼
Service Layer (AuthService / JwtService / SecurityAuditService)
    │
    ▼
Repository (R2DBC reactive queries)
    │
    ▼
H2 In-Memory Database
```

### WebSocket Connections

```
WebSocket Upgrade Request (ws://host:8080/screenshare?token=JWT)
    │
    ▼
WebFluxWebSocketConfig (maps /screenshare → handler)
    │
    ▼
ReactiveScreenShareHandler.handle(session)
    │
    ├── Step 1: IP Block Check (ConnectionThrottleService)
    ├── Step 2: JWT Auth (WebSocketAuthHandler)
    ├── Step 3: Create Outbound Sink (Sinks.Many, buffer 1024)
    └── Step 4: Set up Bidirectional Streams
              │
              ├── Inbound: handleMessage() → TEXT (JSON commands) / BINARY (video)
              └── Outbound: sink.asFlux() → responses + relayed video
```

---

## Project Structure

```
src/main/java/com/screenai/
├── ScreenAIApplication.java          # Entry point (CommandLineRunner, prints startup banner)
│
├── config/
│   ├── WebFluxWebSocketConfig.java   # Maps /screenshare, 10MB frame size, no compression
│   ├── JacksonConfig.java           # ISO-8601 dates, JavaTimeModule
│   ├── DatabaseInitializer.java     # Runs schema.sql via R2DBC (@Order(1))
│   └── DataInitializer.java         # Empty (admin created via AdminBootstrapService)
│
├── controller/
│   ├── AuthController.java          # /api/auth/* — login, register, refresh, logout, validate
│   ├── AdminController.java         # /api/admin/* — audit logs, blocked IPs, stats (ADMIN only)
│   └── PerformanceController.java   # /api/performance/* — FPS, latency, CPU metrics
│
├── dto/
│   ├── AuthResponse.java            # Token response (accessToken, refreshToken, expiresIn, etc.)
│   ├── LoginRequest.java            # { username, password }
│   ├── RegisterRequest.java         # { username (3-32 chars), password (8-128 chars) }
│   ├── RefreshTokenRequest.java     # { refreshToken }
│   └── ErrorResponse.java           # Structured error with code + message + 40+ error codes
│
├── exception/
│   ├── AuthException.java           # Authentication/authorization errors
│   ├── RateLimitException.java      # Rate limiting errors
│   └── RoomException.java           # Room-related errors
│
├── handler/
│   └── ReactiveScreenShareHandler.java  # WebSocket handler (1100+ lines)
│       # - In-memory room/session state maps
│       # - Full message protocol (create/join/leave room, viewer management)
│       # - Binary video relay with init segment caching
│       # - Rate limiting, input validation, audit logging
│
├── model/
│   ├── User.java                    # DB entity: users table (auth, lockout fields)
│   ├── AuditEvent.java              # DB entity: audit_events table (27 event types, 5 severities)
│   ├── BlockedIp.java               # DB entity: blocked_ips table
│   ├── ReactiveRoom.java            # In-memory room (viewers, password, access code, bans)
│   └── PerformanceMetrics.java      # In-memory metrics DTO (builder pattern)
│
├── repository/
│   ├── UserRepository.java          # ReactiveCrudRepository + custom @Query methods
│   ├── AuditEventRepository.java    # Paginated queries, time-range filters, counts
│   └── BlockedIpRepository.java     # Active block queries, expiration cleanup
│
├── security/
│   ├── SecurityConfig.java          # Spring Security config (CSRF, CORS, headers, auth rules)
│   ├── JwtAuthenticationFilter.java # HTTP request JWT filter (WebFilter)
│   └── WebSocketAuthHandler.java    # WebSocket JWT auth (?token= query param)
│
├── service/
│   ├── AuthService.java             # Register, login, refresh, logout, password validation, lockout
│   ├── JwtService.java              # JWT generation (HS256) + validation + claims extraction
│   ├── SecurityAuditService.java    # 20+ typed log methods, persists to audit_events table
│   ├── RateLimitService.java        # Sliding window rate limiting (per-session + per-IP)
│   ├── ConnectionThrottleService.java  # IP blocking (memory cache + DB persistence)
│   ├── RoomSecurityService.java     # Room password hashing (SHA-256+salt), access codes
│   ├── PerformanceMonitorService.java  # FPS, latency, CPU tracking
│   └── AdminBootstrapService.java   # Creates admin user on startup if ADMIN_PASSWORD is set
│
└── validation/
    └── InputValidator.java          # Regex validation for room IDs, usernames, passwords, messages

src/main/resources/
├── application.yml                  # All configuration (server, DB, JWT, security, limits, logging)
├── schema.sql                       # 3 tables: users, audit_events, blocked_ips (with indexes)
└── data.sql                         # Empty (admin created programmatically)
```

---

## Database Schema

Three tables, all managed via R2DBC (reactive):

### `users`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `username` | VARCHAR(32) UNIQUE | Stored lowercase |
| `password_hash` | VARCHAR(255) | BCrypt cost 12 |
| `role` | VARCHAR(20) | `USER` or `ADMIN` |
| `refresh_token` | VARCHAR(500) | Opaque token, nullable |
| `refresh_token_expires_at` | TIMESTAMP | 7-day TTL |
| `failed_login_attempts` | INT | Default 0, resets on success |
| `locked_until` | TIMESTAMP | Nullable, set after 5 failures |
| `created_at` | TIMESTAMP | Auto-set |
| `updated_at` | TIMESTAMP | Auto-set |
| `enabled` | BOOLEAN | Default TRUE |

### `audit_events`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `event_type` | VARCHAR(50) | 27 possible values |
| `username` | VARCHAR(32) | Privacy-masked in logs |
| `session_id` | VARCHAR(100) | Truncated to 8 chars in logs |
| `room_id` | VARCHAR(64) | Nullable |
| `ip_address` | VARCHAR(45) | IPv4 or IPv6 |
| `details` | VARCHAR(1000) | Human-readable description |
| `severity` | VARCHAR(20) | DEBUG/INFO/WARN/ERROR/CRITICAL |
| `created_at` | TIMESTAMP | Auto-set |

### `blocked_ips`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `ip_address` | VARCHAR(45) UNIQUE | |
| `reason` | VARCHAR(255) | |
| `blocked_at` | TIMESTAMP | Auto-set |
| `blocked_until` | TIMESTAMP | Expiration time |
| `created_by` | VARCHAR(32) | Admin username or "system" |

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **WebFlux over MVC** | Non-blocking I/O for concurrent WebSocket connections; Netty handles thousands of connections with minimal threads |
| **R2DBC over JPA** | Fully reactive stack — no blocking database calls in a reactive pipeline |
| **H2 in-memory** | Zero-config development; swap to PostgreSQL/MySQL for production |
| **Relay-only** | Server doesn't transcode video — just forwards binary frames, keeping CPU usage at 5-15% |
| **In-memory rooms** | Rooms are ephemeral and don't need persistence; `ConcurrentHashMap` for thread safety |
| **Opaque refresh tokens** | Stored server-side, rotated on every use — more secure than JWT refresh tokens |
| **Dual-layer IP blocking** | `ConcurrentHashMap` for fast sync checks (required for WebSocket connect), DB for persistence across restarts |
| **Init segment caching** | Late-joining viewers receive the H.264 init segment (SPS/PPS or ftyp/moov) immediately so their decoder can start |

---

## Performance Characteristics

| Metric | Value |
|--------|-------|
| Concurrent connections | 1,000+ |
| Relay latency | < 50ms |
| CPU usage (relay mode) | 5-15% |
| Memory baseline | ~256 MB |
| WebSocket frame size | Up to 10 MB |
| Max rooms | 100 (configurable) |
| Max viewers per room | 50 (configurable) |
