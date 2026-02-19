# ScreenAI Security Documentation

Comprehensive documentation of the security architecture, features, and implementation details for the ScreenAI platform (server + client).

---

## Table of Contents

- [Security Architecture Overview](#security-architecture-overview)
- [Authentication System](#authentication-system)
- [JWT Implementation](#jwt-implementation)
- [Password Security](#password-security)
- [Account Protection](#account-protection)
- [Rate Limiting & IP Blocking](#rate-limiting--ip-blocking)
- [Room Security](#room-security)
- [WebSocket Security](#websocket-security)
- [Client-Side Security](#client-side-security)
- [Audit Logging](#audit-logging)
- [Role-Based Access Control](#role-based-access-control)
- [Security Headers](#security-headers)
- [Input Validation & Sanitization](#input-validation--sanitization)
- [Configuration Reference](#configuration-reference)
- [API Endpoints](#api-endpoints)
- [Security Best Practices for Production](#security-best-practices-for-production)

---

## Security Architecture Overview

ScreenAI implements a multi-layered defense-in-depth security model that covers authentication, authorization, transport, and data protection across both server and client applications.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          SECURITY LAYERS                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─── Layer 1: Network Security ───────────────────────────────────────┐ │
│  │  • IP Blocking (ConcurrentHashMap + DB)                             │ │
│  │  • Rate Limiting (Sliding Window per session/IP)                    │ │
│  │  • Connection Throttling                                            │ │
│  │  • Security Headers (CSP, HSTS, X-XSS-Protection)                  │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌─── Layer 2: Authentication ─────────────────────────────────────────┐ │
│  │  • JWT Access Tokens (HMAC-SHA256, 15 min TTL)                      │ │
│  │  • Opaque Refresh Tokens (SecureRandom, 7 day TTL)                  │ │
│  │  • BCrypt Password Hashing (cost factor 12)                         │ │
│  │  • Account Lockout (5 attempts → 15 min lock)                       │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌─── Layer 3: Authorization ──────────────────────────────────────────┐ │
│  │  • Role-Based Access Control (USER / ADMIN)                         │ │
│  │  • Room-Level Permissions (presenter / viewer)                      │ │
│  │  • Viewer Approval Workflow (approve / deny / ban / kick)           │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌─── Layer 4: Data Protection ────────────────────────────────────────┐ │
│  │  • AES-256-GCM Encrypted Token Storage (client-side)                │ │
│  │  • SHA-256 + Salt Room Password Hashing (timing-safe comparison)    │ │
│  │  • Input Validation & Sanitization                                  │ │
│  │  • Audit Logging with Privacy Masking                               │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Authentication System

### How It Works

The authentication flow follows a standard OAuth2-inspired pattern with JWT access tokens and opaque refresh tokens:

```
  Client                                      Server
    │                                            │
    │  POST /api/auth/login                      │
    │  { username, password }                    │
    │───────────────────────────────────────────►│
    │                                            │  ① Validate credentials (BCrypt)
    │                                            │  ② Check account lock status
    │                                            │  ③ Generate JWT access token
    │                                            │  ④ Generate opaque refresh token
    │                                            │  ⑤ Store refresh token in DB
    │  { accessToken, refreshToken, expiresIn }  │
    │◄───────────────────────────────────────────│
    │                                            │
    │  ── Access Token used for all requests ──  │
    │                                            │
    │  POST /api/auth/refresh  (1 min before expiry)
    │  { refreshToken }                          │
    │───────────────────────────────────────────►│
    │                                            │  ⑥ Validate refresh token
    │                                            │  ⑦ Rotate both tokens
    │  { newAccessToken, newRefreshToken }        │
    │◄───────────────────────────────────────────│
    │                                            │
```

### Key Files

| File | Description |
|------|-------------|
| `security/JwtAuthenticationFilter.java` | WebFlux filter — extracts JWT from `Authorization: Bearer` header or `?token=` query param, validates, and sets security context |
| `service/AuthService.java` | Core auth logic — register, login, token refresh, logout with account lockout handling |
| `service/JwtService.java` | JWT generation (HMAC-SHA256) and validation with claims extraction |
| `controller/AuthController.java` | REST endpoints: `/api/auth/login`, `/register`, `/refresh`, `/logout`, `/validate` |

### Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|:---:|-------------|
| `POST` | `/api/auth/register` | No | Create a new user account |
| `POST` | `/api/auth/login` | No | Authenticate and receive tokens |
| `POST` | `/api/auth/refresh` | No | Exchange refresh token for new token pair |
| `POST` | `/api/auth/logout` | Yes | Invalidate refresh token |
| `GET`  | `/api/auth/validate` | Yes | Check if current access token is valid |

---

## JWT Implementation

### Access Tokens

| Property | Value |
|----------|-------|
| **Algorithm** | HMAC-SHA256 (HS256) |
| **TTL** | 15 minutes (configurable) |
| **Secret** | Minimum 256-bit (32 characters). If not set, a random key is generated per startup (dev only). |
| **Issuer** | `ScreenAI` (configurable) |

**JWT Claims:**

```json
{
  "sub": "username",
  "role": "USER",
  "type": "access",
  "iss": "ScreenAI",
  "iat": 1706100000,
  "exp": 1706100900
}
```

### Refresh Tokens

| Property | Value |
|----------|-------|
| **Format** | 32-byte `SecureRandom`, Base64-URL encoded (opaque, NOT a JWT) |
| **TTL** | 7 days (configurable) |
| **Storage** | Server-side in `users` table |
| **Rotation** | New refresh token issued on every refresh (old one invalidated) |

Refresh token rotation prevents token reuse attacks — once a refresh token is exchanged for a new pair, the old refresh token becomes invalid.

---

## Password Security

### Password Policy

Enforced server-side by `InputValidator` and `AuthService`, with basic client-side validation in `LoginDialog`:

| Requirement | Details |
|-------------|---------|
| Minimum length | 8 characters |
| Maximum length | 128 characters |
| Uppercase letter | At least 1 (A-Z) |
| Lowercase letter | At least 1 (a-z) |
| Digit | At least 1 (0-9) |
| Special character | At least 1 (`!@#$%^&*` etc.) |

All policy parameters are configurable via `application.yml`.

### Password Hashing

| Property | Value |
|----------|-------|
| **Algorithm** | BCrypt |
| **Cost Factor** | 12 (4,096 iterations) |
| **Implementation** | Spring Security `BCryptPasswordEncoder` |

Passwords are never stored or transmitted in plain text after initial submission.

---

## Account Protection

### Account Lockout

Brute-force protection is implemented at the user-account level:

| Parameter | Default | Configurable |
|-----------|---------|:---:|
| Max failed attempts | 5 | ✅ |
| Lockout duration | 15 minutes | ✅ |

**How it works:**
1. Each failed login increments `failedLoginAttempts` on the `User` record
2. At the threshold (5), `lockedUntil` is set to `now + lockoutDuration`
3. Subsequent login attempts are rejected with `"Account is locked"` until the lock expires
4. A successful login resets `failedLoginAttempts` to 0

### Username Requirements

| Rule | Details |
|------|---------|
| Length | 3 – 32 characters |
| Characters | Letters, digits, underscores, hyphens only |
| Case | Stored and compared in lowercase |

---

## Rate Limiting & IP Blocking

### Rate Limiting

Implemented in `RateLimitService` using a **sliding window** algorithm with configurable buckets:

| Limit Type | Default | Scope |
|------------|---------|-------|
| Message rate | 100 messages/sec | Per WebSocket session |
| Room creation | 10 rooms/hour | Per IP address |
| Failed auth | 5 failures before IP block | Per IP address |

A background cleanup thread runs every 60 seconds to evict expired rate-limit buckets and prevent memory leaks.

### IP Blocking

Implemented in `ConnectionThrottleService` with a **dual-layer architecture**:

```
  Incoming Connection
         │
         ▼
  ┌──────────────────────────────┐
  │  In-Memory ConcurrentHashMap │  ◄── Fast sync check (WebSocket connect)
  │  (cache layer)               │
  └──────────┬───────────────────┘
             │ synced with
  ┌──────────▼───────────────────┐
  │  H2 Database                 │  ◄── Persistence (survives restarts)
  │  blocked_ips table           │
  └──────────────────────────────┘
```

| Parameter | Default |
|-----------|---------|
| Block duration | 15 minutes |
| Trigger | 5 failed auth attempts per IP |
| Admin override | Manual block/unblock via API |

Blocked IPs are rejected immediately at the WebSocket connection phase, before any message processing occurs.

---

## Room Security

### Password Protection

Rooms can optionally be password-protected at creation time. Implemented in `RoomSecurityService`:

| Property | Implementation |
|----------|---------------|
| **Hash algorithm** | SHA-256 |
| **Salt** | 16-byte random salt per room |
| **Comparison** | Timing-safe (constant-time XOR) to prevent timing attacks |

### Access Codes

As an alternative to entering a room password, hosts can share auto-generated **access codes**:

| Property | Value |
|----------|-------|
| Length | 8 characters |
| Character set | Alphanumeric (excluding confusing chars: `0/O`, `1/I/L`) |
| Expiry | 24 hours (configurable) |
| Display | Shown in host UI with a copy-to-clipboard button |

### Viewer Approval Workflow

When `requireApproval` is enabled (auto-enabled for password-protected rooms):

```
  Viewer                    Server                    Host
    │                         │                         │
    │  join-room + accessCode │                         │
    │────────────────────────►│  viewer-request          │
    │                         │────────────────────────►│
    │                         │                         │  (approve / deny)
    │                         │  approve-viewer          │
    │  viewer-approved        │◄────────────────────────│
    │◄────────────────────────│                         │
```

**Viewer management actions (host only):**

| Action | Effect |
|--------|--------|
| `approve-viewer` | Grant access to a pending viewer |
| `deny-viewer` | Reject a pending viewer |
| `kick-viewer` | Remove an active viewer from the room |
| `ban-viewer` | Remove and permanently block a viewer from the room |

Banned sessions are tracked in memory and cannot rejoin the same room.

### Room Limits

| Limit | Default |
|-------|---------|
| Max viewers per room | 50 |
| Max rooms per user | 5 |
| Max total rooms | 100 |
| Room idle timeout | 60 minutes |

---

## WebSocket Security

The WebSocket endpoint (`ws://localhost:8080/screenshare`) has its own dedicated security pipeline handled by `ReactiveScreenShareHandler`:

```
  WebSocket Connect
         │
         ▼
  ┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
  │ 1. IP Block     │────►│ 2. JWT Auth      │────►│ 3. Session Setup  │
  │    Check        │     │    (?token=...)   │     │    + Rate Limit   │
  └─────────────────┘     └──────────────────┘     └───────────────────┘
    Reject if blocked      Reject if invalid        Apply per-session
                           token                    rate limits
```

1. **IP Block Check** — `ConnectionThrottleService.isBlockedSync()` rejects blocked IPs before any processing
2. **JWT Authentication** — `WebSocketAuthHandler` extracts and validates the JWT from the `?token=` query parameter
3. **Session Rate Limiting** — `RateLimitService` enforces per-session message rate limits during the connection

All WebSocket events (room creation, join, leave, kick, ban, etc.) are recorded by the audit system.

---

## Client-Side Security

### Encrypted Token Storage

The client persists authentication tokens locally using strong encryption, implemented in `TokenStorageService`:

| Property | Value |
|----------|-------|
| **Cipher** | AES-256-GCM (authenticated encryption) |
| **Key derivation** | PBKDF2-HMAC-SHA256, 65,536 iterations |
| **IV** | 12-byte random IV per encryption operation |
| **Storage format** | JSON → Encrypt → Base64 → write to file |
| **File location** | `~/.screenai/credentials.enc` |

The encryption key is derived from the `TOKEN_ENCRYPTION_KEY` environment variable. If not set, a per-user fallback key is generated automatically (`"ScreenAI-" + username + "-default-key-pad!"`), and a security warning is printed to the console. **Set `TOKEN_ENCRYPTION_KEY` in `.env` for production use.**

### Auto-Login & Remember Me

When "Remember Me" is checked:
1. Tokens are encrypted and persisted to disk via `TokenStorageService`
2. On next launch, `AuthenticationService.tryAutoLogin()` loads and decrypts the stored refresh token
3. The refresh token is exchanged for a new access token via `/api/auth/refresh`
4. If the refresh token has expired or the server is unreachable after retries, the login dialog appears

### Auto-Connect

The client reads `SERVER_HOST` and `SERVER_PORT` from `.env` and auto-connects on launch — no manual server input fields exist in the default UI.

### Token Auto-Refresh with Retry

The client proactively refreshes the access token using a `ScheduledExecutorService`:
- Scheduled to run **1 minute before** the access token expires
- On failure: retries up to **3 times** with exponential backoff (5s → 10s → 20s)
- **Smart retry classification:** only retries on transient errors (timeout, 500). Non-retryable errors (401, 403, "invalid", "expired", "revoked") skip retry
- Tokens are only cleared on definitive rejection (HTTP 401/403), not on transient failures
- If all retries fail, the login dialog appears (with duplicate dialog prevention)

### Login Dialog

`LoginDialog` (JavaFX) provides:
- White card design with blue gradient header and ScreenAI branding
- Separate **Login** and **Sign Up** pages (toggle between them)
- Client-side input validation (username required, password min 8 chars for registration, password confirmation)
- Remember Me checkbox
- Loading indicators and error feedback
- Duplicate dialog prevention (`loginDialogShowing` volatile flag in MainController)
- Server address auto-configured from `.env` — no manual URL input field

### Room Password Dialog

`RoomPasswordDialog` (JavaFX) provides:
- **Create mode:** Room ID validation (3–50 chars, alphanumeric + hyphens/underscores), optional password with confirmation (min 4 chars), "require approval" toggle
- **Join mode:** Password field or access code field (either can be used)

---

## Audit Logging

All security-relevant events are persisted to the `audit_events` database table and written to the application log via `SecurityAuditService`.

### Event Types

| Event Type | Severity | Description |
|------------|----------|-------------|
| `LOGIN_SUCCESS` | INFO | Successful authentication |
| `LOGIN_FAILURE` | WARN | Failed login attempt |
| `ACCOUNT_LOCKED` | WARN | Account locked after max failed attempts |
| `REGISTRATION_SUCCESS` | INFO | New user registered |
| `REGISTRATION_FAILURE` | WARN | Registration rejected |
| `TOKEN_REFRESH` | INFO | Access token refreshed |
| `LOGOUT` | INFO | User logged out |
| `ROOM_CREATED` | INFO | New room created |
| `ROOM_CLOSED` | INFO | Room closed |
| `VIEWER_JOINED` | INFO | Viewer joined a room |
| `VIEWER_LEFT` | INFO | Viewer left a room |
| `VIEWER_APPROVED` | INFO | Host approved a viewer |
| `VIEWER_DENIED` | WARN | Host denied a viewer |
| `VIEWER_KICKED` | WARN | Host kicked a viewer |
| `VIEWER_BANNED` | WARN | Host banned a viewer |
| `RATE_LIMIT_EXCEEDED` | WARN | Session exceeded message rate limit |
| `IP_BLOCKED` | ERROR | IP address blocked |
| `IP_UNBLOCKED` | INFO | IP address unblocked |
| `SUSPICIOUS_ACTIVITY` | CRITICAL | Anomalous behavior detected |
| `ADMIN_ACTION` | INFO | Administrative action performed |
| `INVALID_TOKEN` | WARN | Invalid JWT presented |
| `CONNECTION_REJECTED` | WARN | WebSocket connection rejected |

### Severity Levels

| Level | Meaning |
|-------|---------|
| `DEBUG` | Development-level detail |
| `INFO` | Normal security operations |
| `WARN` | Potential security concern |
| `ERROR` | Security rule violation |
| `CRITICAL` | Serious security incident requiring attention |

### Privacy Protection

- **Usernames** are masked in logs: `admin` → `ad***in`
- **Session IDs** are truncated to 8 characters
- Audit data is queryable by username, event type, severity, and time range via the admin API

---

## Role-Based Access Control

### Roles

| Role | Scope |
|------|-------|
| `USER` | Standard user — can register, login, create rooms, join rooms, stream |
| `ADMIN` | Full access — all USER permissions + admin API endpoints |

### Enforcement

Roles are enforced at multiple levels:

1. **JWT Claims** — role embedded in token payload
2. **SecurityConfig** — URL-pattern rules: `/api/admin/**` requires `ROLE_ADMIN`
3. **Method Security** — `@EnableReactiveMethodSecurity` for fine-grained control

### Admin API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/logs` | Get all audit logs (supports `?limit=` and `?offset=`) |
| `GET` | `/api/admin/logs/user/{username}` | Filter logs by username |
| `GET` | `/api/admin/logs/type/{eventType}` | Filter logs by event type |
| `GET` | `/api/admin/logs/severity/{severity}` | Filter logs by severity level |
| `GET` | `/api/admin/blocked-ips` | List all currently blocked IPs |

---

## Security Headers

Configured in `SecurityConfig.java` for all HTTP responses:

| Header | Value | Purpose |
|--------|-------|---------|
| Content-Security-Policy | `default-src 'self'` | Prevent XSS and data injection |
| X-Content-Type-Options | `nosniff` | Prevent MIME-type sniffing |
| X-XSS-Protection | `1; mode=block` | Legacy XSS filter |
| Referrer-Policy | `strict-origin-when-cross-origin` | Control referrer leakage |
| Permissions-Policy | `geolocation=(), microphone=(), camera=(), payment=()` | Block unnecessary browser APIs |
| Cache-Control | Disabled | Prevent caching of sensitive responses |

---

## Input Validation & Sanitization

Centralized in `InputValidator` with strict regex patterns applied to all user inputs:

| Input | Rules |
|-------|-------|
| **Room ID** | Alphanumeric + hyphens/underscores, max 64 chars |
| **Username** | Alphanumeric + underscores/hyphens, 3–32 chars |
| **Access Code** | Uppercase alphanumeric, 6–12 chars |
| **Password** | 8–128 chars, requires uppercase + lowercase + digit + special |
| **Text messages** | Max 1,000 characters |
| **Binary messages** | Max 10 MB |

Control characters are stripped from all text inputs.

---

## Configuration Reference

All security settings are in `application.yml` and can be overridden with environment variables:

```yaml
# JWT Configuration
jwt:
  secret: ${JWT_SECRET:}                        # Empty = random key per startup (dev)
  access-token-expiration: 900000               # 15 minutes (ms)
  refresh-token-expiration: 604800000           # 7 days (ms)
  issuer: ScreenAI

# Password Policy
security:
  password:
    min-length: 8
    require-uppercase: true
    require-lowercase: true
    require-digit: true
    require-special: true

# Account Lockout
  max-failed-attempts: 5
  lockout-duration: 900000                      # 15 minutes (ms)

# Rate Limiting
  rate-limit:
    messages-per-second: 100
    room-creations-per-hour: 10
    failed-auth-threshold: 5
    ip-block-duration: 900000                   # 15 minutes (ms)

# Room Limits
  room:
    max-rooms: 100
    max-viewers-per-room: 50
    max-rooms-per-user: 5
    access-code-expiry: 86400000                # 24 hours (ms)
    idle-timeout: 3600000                       # 60 minutes (ms)
```

### Client Environment Variables (.env)

```env
# Server auto-connect
SERVER_HOST=localhost
SERVER_PORT=8080
SCREENAI_SERVER_URL=ws://localhost:8080/screenshare
SCREENAI_HTTP_URL=http://localhost:8080

# Security
TOKEN_ENCRYPTION_KEY=your-32-char-key-for-aes-256!!   # Required for secure storage
CREDENTIALS_STORAGE_DIR=~/.screenai
```

---

## API Endpoints

### Authentication (Public)

| Method | Endpoint | Request Body | Response |
|--------|----------|-------------|----------|
| `POST` | `/api/auth/register` | `{ "username": "...", "password": "..." }` | `{ accessToken, refreshToken, expiresIn, username, role }` |
| `POST` | `/api/auth/login` | `{ "username": "...", "password": "..." }` | `{ accessToken, refreshToken, expiresIn, username, role }` |
| `POST` | `/api/auth/refresh` | `{ "refreshToken": "..." }` | `{ accessToken, refreshToken, expiresIn }` |
| `POST` | `/api/auth/logout` | — | `{ message }` |
| `GET`  | `/api/auth/validate` | — | `{ valid: true/false }` |

### Default Users

No admin account is created by default. To bootstrap an admin user, set environment variables before starting the server:

```bash
export ADMIN_PASSWORD="YourStrongAdminPass123!"
mvn spring-boot:run
```

| Setting | Env Var | Default |
|---------|---------|--------|
| Username | `ADMIN_USERNAME` | `admin` |
| Password | `ADMIN_PASSWORD` | *(not set — bootstrap skipped if blank)* |

---

## Security Best Practices for Production

| Area | Recommendation |
|------|----------------|
| **JWT Secret** | Set a strong, unique 256-bit+ secret via `JWT_SECRET` env var. Never use the default. |
| **Transport** | Enable TLS (HTTPS/WSS) — all tokens are bearer tokens and must be protected in transit. |
| **CORS** | Restrict `cors.allowed-origins` to your specific domains. |
| **Database** | Replace H2 with PostgreSQL or MySQL. H2 is in-memory and for development only. |
| **Encryption Key** | Set `TOKEN_ENCRYPTION_KEY` to a unique 32-character key in the client `.env` file. |
| **Monitoring** | Set up alerts on `CRITICAL` and `ERROR` severity audit events. |
| **Rate Limits** | Tune rate limits based on expected traffic patterns. |
| **Log Retention** | Configure log rotation and retention policies for audit logs. |
| **Firewall** | Restrict direct access to port 8080; place behind a reverse proxy (nginx, Caddy). |

---

## Project Security File Map

### Server (`ScreenAi-security-server`)

```
src/main/java/com/screenai/
├── security/
│   ├── SecurityConfig.java            # Spring Security config (CSRF, CORS, headers, auth rules)
│   ├── JwtAuthenticationFilter.java   # HTTP request JWT filter
│   └── WebSocketAuthHandler.java      # WebSocket connection JWT auth
├── service/
│   ├── AuthService.java               # Login, register, refresh, lockout logic
│   ├── JwtService.java                # JWT generation & validation (HS256)
│   ├── RateLimitService.java          # Sliding window rate limiting
│   ├── ConnectionThrottleService.java # IP blocking (memory + DB)
│   ├── RoomSecurityService.java       # Room passwords, access codes
│   └── SecurityAuditService.java      # Audit event logging (25+ event types)
├── controller/
│   ├── AuthController.java            # /api/auth/* REST endpoints
│   └── AdminController.java           # /api/admin/* (logs, blocked IPs)
├── validation/
│   └── InputValidator.java            # Centralized input validation & sanitization
├── model/
│   ├── User.java                      # User entity (password hash, lockout fields)
│   ├── ReactiveRoom.java              # Room with security (password, approval, bans)
│   ├── AuditEvent.java                # Audit event entity (25 event types, 5 severities)
│   └── BlockedIp.java                 # Blocked IP entity
└── handler/
    └── ReactiveScreenShareHandler.java # WebSocket handler (integrates all security layers)
```

### Client (`ScreenAiClient-security-client`)

```
src/main/java/
├── config/
│   └── EnvConfig.java                 # Environment config (.env loading, encryption key)
├── service/
│   ├── AuthenticationService.java     # JWT auth client (login, refresh, auto-login)
│   └── TokenStorageService.java       # AES-256-GCM encrypted token persistence
└── controller/
    ├── LoginDialog.java               # Login/Register UI with validation
    └── RoomPasswordDialog.java        # Room security dialogs (create/join)
```

---

