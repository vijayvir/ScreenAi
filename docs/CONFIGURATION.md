# ScreenAI Server — Configuration Reference

Complete reference for all configurable settings in `application.yml`.

---

## Server

```yaml
server:
  port: ${SERVER_PORT:8080}
  netty:
    connection-timeout: ${CONNECTION_TIMEOUT:60s}
    idle-timeout: ${IDLE_TIMEOUT:600s}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `server.port` | `SERVER_PORT` | `8080` | HTTP/WebSocket port |
| `server.netty.connection-timeout` | `CONNECTION_TIMEOUT` | `60s` | TCP connection timeout |
| `server.netty.idle-timeout` | `IDLE_TIMEOUT` | `600s` | Idle connection timeout |

---

## Spring WebFlux

```yaml
spring:
  webflux:
    base-path: /
  codec:
    max-in-memory-size: ${MAX_BINARY_MESSAGE_SIZE:10485760}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `spring.codec.max-in-memory-size` | `MAX_BINARY_MESSAGE_SIZE` | 10 MB | Max size for WebFlux codecs (video frames) |

---

## Database (R2DBC)

```yaml
spring:
  r2dbc:
    url: ${DATABASE_URL:r2dbc:h2:mem:///screenai_db;DB_CLOSE_DELAY=-1}
    username: ${DB_USERNAME:sa}
    password: ${DB_PASSWORD:}
    pool:
      initial-size: 5
      max-size: 10
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `spring.r2dbc.url` | `DATABASE_URL` | H2 in-memory | R2DBC connection URL |
| `spring.r2dbc.username` | `DB_USERNAME` | `sa` | Database username |
| `spring.r2dbc.password` | `DB_PASSWORD` | (empty) | Database password |
| `spring.r2dbc.pool.initial-size` | — | `5` | Initial connection pool size |
| `spring.r2dbc.pool.max-size` | — | `10` | Max connection pool size |

**Production example (PostgreSQL):**
```
DATABASE_URL=r2dbc:postgresql://db-host:5432/screenai
```

---

## JWT

```yaml
security:
  jwt:
    secret: ${JWT_SECRET:}
    access-token-expiration: ${ACCESS_TOKEN_EXPIRATION:900000}
    refresh-token-expiration: ${REFRESH_TOKEN_EXPIRATION:604800000}
    issuer: ${JWT_ISSUER:ScreenAI}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `security.jwt.secret` | `JWT_SECRET` | *(empty — random key generated per startup)* | HMAC-SHA256 signing key (min 32 chars). **Set for production!** |
| `security.jwt.access-token-expiration` | `ACCESS_TOKEN_EXPIRATION` | `900000` (15 min) | Access token TTL in ms |
| `security.jwt.refresh-token-expiration` | `REFRESH_TOKEN_EXPIRATION` | `604800000` (7 days) | Refresh token TTL in ms |
| `security.jwt.issuer` | `JWT_ISSUER` | `ScreenAI` | JWT issuer claim |

---

## Password Policy

```yaml
security:
  password:
    min-length: 8
    require-uppercase: true
    require-lowercase: true
    require-digit: true
    require-special-char: true
```

| Setting | Default | Description |
|---------|---------|-------------|
| `security.password.min-length` | `8` | Minimum password length |
| `security.password.require-uppercase` | `true` | Require uppercase letter |
| `security.password.require-lowercase` | `true` | Require lowercase letter |
| `security.password.require-digit` | `true` | Require digit |
| `security.password.require-special-char` | `true` | Require special character |

---

## Account Lockout

```yaml
security:
  lockout:
    max-attempts: ${LOCKOUT_MAX_ATTEMPTS:5}
    duration-minutes: ${LOCKOUT_DURATION_MINUTES:15}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `security.lockout.max-attempts` | `LOCKOUT_MAX_ATTEMPTS` | `5` | Failed logins before lock |
| `security.lockout.duration-minutes` | `LOCKOUT_DURATION_MINUTES` | `15` | Lock duration in minutes |

---

## Rate Limiting

```yaml
security:
  rate-limit:
    messages-per-second: ${RATE_LIMIT_MESSAGES_PER_SECOND:100}
    room-creations-per-hour: ${RATE_LIMIT_ROOM_CREATIONS_PER_HOUR:10}
    failed-auth-before-block: ${RATE_LIMIT_FAILED_AUTH_BEFORE_BLOCK:5}
    ip-block-duration-minutes: ${IP_BLOCK_DURATION_MINUTES:15}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `messages-per-second` | `RATE_LIMIT_MESSAGES_PER_SECOND` | `100` | Max WebSocket messages per session per second |
| `room-creations-per-hour` | `RATE_LIMIT_ROOM_CREATIONS_PER_HOUR` | `10` | Max room creations per IP per hour |
| `failed-auth-before-block` | `RATE_LIMIT_FAILED_AUTH_BEFORE_BLOCK` | `5` | Failed auths before IP block |
| `ip-block-duration-minutes` | `IP_BLOCK_DURATION_MINUTES` | `15` | IP block duration in minutes |

---

## Resource Limits

```yaml
security:
  resource-limits:
    max-active-rooms: ${MAX_ACTIVE_ROOMS:100}
    max-viewers-per-room: ${MAX_VIEWERS_PER_ROOM:50}
    max-rooms-per-user: ${MAX_ROOMS_PER_USER:5}
    max-binary-message-size: ${MAX_BINARY_MESSAGE_SIZE:10485760}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `max-active-rooms` | `MAX_ACTIVE_ROOMS` | `100` | Max concurrent rooms |
| `max-viewers-per-room` | `MAX_VIEWERS_PER_ROOM` | `50` | Max viewers per room |
| `max-rooms-per-user` | `MAX_ROOMS_PER_USER` | `5` | Max rooms per user |
| `max-binary-message-size` | `MAX_BINARY_MESSAGE_SIZE` | 10 MB | Max binary frame size |

---

## Room Settings

```yaml
security:
  room:
    access-code-expiration-hours: ${ROOM_ACCESS_CODE_EXPIRATION_HOURS:24}
    idle-timeout-minutes: ${ROOM_IDLE_TIMEOUT_MINUTES:60}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `access-code-expiration-hours` | `ROOM_ACCESS_CODE_EXPIRATION_HOURS` | `24` | Access code TTL in hours |
| `idle-timeout-minutes` | `ROOM_IDLE_TIMEOUT_MINUTES` | `60` | Room idle timeout in minutes |

---

## CORS

```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,...}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | localhost variants | Comma-separated allowed origins |

---

## Admin Defaults

```yaml
admin:
  username: ${ADMIN_USERNAME:admin}
  password: ${ADMIN_PASSWORD:}
```

| Setting | Env Var | Default | Description |
|---------|---------|---------|-------------|
| `admin.username` | `ADMIN_USERNAME` | `admin` | Default admin username |
| `admin.password` | `ADMIN_PASSWORD` | *(empty — bootstrap skipped)* | Admin password. **Must be set to create admin user.** |

---

## Logging

```yaml
logging:
  level:
    root: INFO
    com.screenai: INFO
    com.screenai.security: DEBUG
    reactor.netty: WARN
  file:
    name: logs/screenai.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 5
```

| Setting | Default | Description |
|---------|---------|-------------|
| Root log level | `INFO` | Base logging level |
| `com.screenai` | `INFO` | Application logging |
| `com.screenai.security` | `DEBUG` | Security event logging |
| `reactor.netty` | `WARN` | Reduce Netty noise |
| Log file | `logs/screenai.log` | Log file path |
| Max file size | `10MB` | Rotate after 10 MB |
| Max history | `5` | Keep 5 rotated files |
