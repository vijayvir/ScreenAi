# ScreenAI Server — API Reference

Complete reference for all REST API endpoints and WebSocket protocol.

---

## Table of Contents

- [Base URL](#base-url)
- [Authentication API](#authentication-api)
- [Admin API](#admin-api)
- [Performance API](#performance-api)
- [WebSocket Protocol](#websocket-protocol)
- [Error Codes](#error-codes)

---

## Base URL

```
HTTP:      http://localhost:8080
WebSocket: ws://localhost:8080/screenshare
```

---

## Authentication API

**Base path:** `/api/auth` — No authentication required.

### POST `/api/auth/register`

Register a new user account.

**Request:**
```json
{
  "username": "myuser",
  "password": "SecurePass123!"
}
```

| Field | Rules |
|-------|-------|
| `username` | 3-32 chars, alphanumeric + underscores/hyphens, stored lowercase |
| `password` | 8-128 chars, requires uppercase + lowercase + digit + special char |

**Response (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "myuser",
  "role": "USER"
}
```

**Errors:**
| Code | Status | Reason |
|------|--------|--------|
| `AUTH_005` | 409 | Username already exists |
| `AUTH_006` | 400 | Password doesn't meet policy |
| `VAL_001` | 400 | Invalid input format |

---

### POST `/api/auth/login`

Authenticate and receive JWT tokens.

**Request:**
```json
{
  "username": "myuser",
  "password": "SecurePass123!"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "myuser",
  "role": "USER"
}
```

**Errors:**
| Code | Status | Reason |
|------|--------|--------|
| `AUTH_001` | 401 | Invalid username or password |
| `AUTH_002` | 403 | Account is locked (too many failed attempts) |

---

### POST `/api/auth/refresh`

Exchange a refresh token for a new access + refresh token pair (rotation).

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...(new)",
  "refreshToken": "bmV3IHJlZnJlc2ggdG9rZW4...(new)",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

> The old refresh token is invalidated after use (rotation).

**Errors:**
| Code | Status | Reason |
|------|--------|--------|
| `AUTH_003` | 401 | Refresh token expired or invalid |

---

### POST `/api/auth/logout`

Invalidate the user's refresh token.

**Headers:** `Authorization: Bearer <accessToken>`

**Response (200 OK):**
```json
{
  "tokenType": "Bearer",
  "message": "Logged out successfully"
}
```

---

### GET `/api/auth/validate`

Check if the current access token is valid.

**Headers:** `Authorization: Bearer <accessToken>`

**Response (200 OK):**
```json
{
  "username": "myuser",
  "role": "USER",
  "message": "Token is valid"
}
```

---

## Admin API

**Base path:** `/api/admin` — Requires `ADMIN` role.

**Headers:** `Authorization: Bearer <adminAccessToken>`

### GET `/api/admin/logs`

Get paginated audit logs.

| Param | Default | Description |
|-------|---------|-------------|
| `limit` | 100 | Max results (capped at 1000) |
| `offset` | 0 | Skip count |

**Response:** `Array<AuditEvent>`

```json
[
  {
    "id": 1,
    "eventType": "LOGIN_SUCCESS",
    "username": "ad***in",
    "sessionId": "abc12345",
    "ipAddress": "127.0.0.1",
    "details": "User logged in successfully",
    "severity": "INFO",
    "createdAt": "2026-01-24T17:48:30.981"
  }
]
```

### GET `/api/admin/logs/user/{username}`

Filter audit logs by username.

### GET `/api/admin/logs/type/{eventType}`

Filter by event type. Valid types: `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `ACCOUNT_LOCKED`, `ROOM_CREATED`, `IP_BLOCKED`, `RATE_LIMIT_EXCEEDED`, `SUSPICIOUS_ACTIVITY`, etc.

### GET `/api/admin/logs/severity/{severity}`

Filter by severity: `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`.

### GET `/api/admin/logs/since?after={ISO_DATETIME}`

Get logs after a timestamp (ISO-8601 format).

### GET `/api/admin/blocked-ips`

List all currently active IP blocks.

```json
[
  {
    "id": 1,
    "ipAddress": "192.168.1.100",
    "reason": "Exceeded failed authentication threshold",
    "blockedAt": "2026-01-24T18:00:00",
    "blockedUntil": "2026-01-24T18:15:00",
    "createdBy": "system"
  }
]
```

### POST `/api/admin/blocked-ips`

Manually block an IP address.

```json
{
  "ipAddress": "192.168.1.100",
  "durationMinutes": 60,
  "reason": "Suspicious activity"
}
```

### DELETE `/api/admin/blocked-ips/{ipAddress}`

Unblock an IP address.

### GET `/api/admin/blocked-ips/{ipAddress}/status`

Check if a specific IP is blocked.

### GET `/api/admin/stats`

Get security statistics.

```json
{
  "activeBlockedIps": 2,
  "recentAuditEvents": 150,
  "timestamp": "2026-01-24T18:30:00"
}
```

### GET `/api/admin/stats/failed-logins/{ipAddress}`

Get failed login count for an IP in the last hour.

---

## Performance API

**Base path:** `/api/performance` — Requires authentication.

### GET `/api/performance/metrics`

Current performance metrics snapshot.

### GET `/api/performance/stats`

Aggregated stats with drop rate and encoder info.

### GET `/api/performance/status`

Monitoring status (active/inactive, current FPS).

---

## WebSocket Protocol

### Connection

```
ws://localhost:8080/screenshare?token=<JWT_ACCESS_TOKEN>
```

JWT is **required** via the `?token=` query parameter. Connection is rejected without a valid token.

### Client → Server Messages

#### Create Room

```json
{
  "type": "create-room",
  "roomId": "my-room-123",
  "password": "optional-room-password",
  "maxViewers": 50
}
```

| Field | Required | Description |
|-------|:--------:|-------------|
| `roomId` | Yes | 1-64 chars, alphanumeric + hyphens/underscores |
| `password` | No | Enables password protection + auto-generates access code |
| `maxViewers` | No | Default: 50 |

#### Join Room

```json
{
  "type": "join-room",
  "roomId": "my-room-123",
  "password": "room-password",
  "accessCode": "ABC12345"
}
```

| Field | Required | Description |
|-------|:--------:|-------------|
| `roomId` | Yes | Room to join |
| `password` | Conditional | Required for password-protected rooms (if no access code) |
| `accessCode` | Conditional | Alternative to password for protected rooms |

#### Leave Room

```json
{ "type": "leave-room" }
```

#### Get Viewer Count

```json
{ "type": "get-viewer-count" }
```

#### Viewer Management (Presenter Only)

```json
{ "type": "approve-viewer", "viewerSessionId": "session-id-123" }
{ "type": "deny-viewer", "viewerSessionId": "session-id-123" }
{ "type": "kick-viewer", "viewerSessionId": "session-id-123" }
{ "type": "ban-viewer", "viewerSessionId": "session-id-123" }
```

#### Binary Video Frames (Presenter Only)

Send raw binary H.264/MPEG-TS frames. Max size: 10 MB per message.

---

### Server → Client Messages

| Type | Fields | Description |
|------|--------|-------------|
| `connected` | `sessionId`, `username`, `message` | Connection established |
| `room-created` | `roomId`, `role`, `passwordProtected`, `requiresApproval`, `accessCode?` | Room created successfully |
| `room-joined` | `roomId`, `role`, `viewerCount` | Joined room as viewer |
| `room-left` | `message` | Left room |
| `viewer-count` | `count` | Updated viewer count |
| `waiting-approval` | `roomId`, `message` | Viewer waiting for host approval |
| `viewer-request` | `viewerSessionId`, `viewerUsername`, `pendingCount` | New pending viewer (to presenter) |
| `viewer-approved` | `viewerSessionId`, `pendingCount` | Viewer approved (to presenter) |
| `viewer-denied` | `viewerSessionId`, `pendingCount` | Viewer denied (to presenter) |
| `access-denied` | `message` | Viewer was denied access |
| `viewer-kicked` | `viewerSessionId`, `viewerCount` | Viewer kicked (to presenter) |
| `kicked` | `message` | You were kicked (to viewer) |
| `viewer-banned` | `viewerSessionId`, `viewerCount` | Viewer banned (to presenter) |
| `banned` | `message` | You were banned (to viewer) |
| `presenter-left` | `message` | Presenter disconnected (to viewers) |
| `error` | `code`, `message` | Error message |

---

### WebSocket Flow Examples

#### Creating and Joining a Password-Protected Room

```
Presenter                    Server                         Viewer
    │                          │                              │
    │  create-room             │                              │
    │  { roomId, password }    │                              │
    │─────────────────────────►│                              │
    │                          │                              │
    │  room-created            │                              │
    │  { accessCode: "XYZ789"} │                              │
    │◄─────────────────────────│                              │
    │                          │                              │
    │  (share accessCode       │   join-room                  │
    │   out-of-band)           │   { roomId, accessCode }     │
    │                          │◄─────────────────────────────│
    │                          │                              │
    │                          │   room-joined                │
    │                          │──────────────────────────────►│
    │                          │                              │
    │  Binary H.264 frames     │   Binary H.264 frames        │
    │═════════════════════════►│══════════════════════════════►│
```

#### Viewer Approval Workflow

```
Viewer                       Server                        Presenter
   │                           │                              │
   │  join-room { roomId }     │                              │
   │──────────────────────────►│  viewer-request               │
   │                           │─────────────────────────────►│
   │  waiting-approval         │                              │
   │◄──────────────────────────│                              │
   │                           │  approve-viewer               │
   │                           │◄─────────────────────────────│
   │  room-joined              │                              │
   │◄──────────────────────────│  viewer-approved              │
   │                           │─────────────────────────────►│
```

---

## Error Codes

### Authentication Errors

| Code | Message |
|------|---------|
| `AUTH_001` | Invalid credentials |
| `AUTH_002` | Account locked |
| `AUTH_003` | Token expired |
| `AUTH_004` | Invalid token |
| `AUTH_005` | Username already exists |
| `AUTH_006` | Password too weak |
| `AUTH_007` | Account disabled |
| `AUTH_008` | Unauthorized |
| `AUTH_009` | Token refresh failed |

### Room Errors

| Code | Message |
|------|---------|
| `ROOM_001` | Room not found |
| `ROOM_002` | Room already exists |
| `ROOM_003` | Invalid room password |
| `ROOM_004` | Room is full |
| `ROOM_005` | Access denied |
| `ROOM_006` | Banned from room |
| `ROOM_007` | Waiting for approval |
| `ROOM_008` | Invalid room ID format |
| `ROOM_009` | Room creation limit reached |

### Rate Limit Errors

| Code | Message |
|------|---------|
| `RATE_001` | Rate limit exceeded |
| `RATE_002` | Too many requests |
| `RATE_003` | IP blocked |

### Validation Errors

| Code | Message |
|------|---------|
| `VAL_001` | Invalid input |
| `VAL_002` | Missing required field |
| `VAL_003` | Value too long |
| `VAL_004` | Invalid format |

### Server Errors

| Code | Message |
|------|---------|
| `SRV_001` | Internal server error |
| `SRV_002` | Service unavailable |
| `SRV_003` | Database error |
