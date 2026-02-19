# ScreenAI Server — Setup & Deployment Guide

## Prerequisites

- **Java 17+** (Java 21 recommended)

```bash
java -version
# Should show: openjdk version "17" or higher
```

---

## Quick Start (Development)

### 1. Run with Maven

```bash
cd ScreenAi-security-server
mvn spring-boot:run
```

### 2. Or Build & Run JAR

```bash
mvn clean package -DskipTests
java -jar target/screenai-server-1.0.0.jar
```

### 3. Verify

You should see:

```
═══════════════════════════════════════════════════════
   ScreenAI-Server (Relay Mode) Started Successfully   
═══════════════════════════════════════════════════════

📍 WebSocket Endpoint:
   Local:   ws://localhost:8080/screenshare
   Network: ws://<your-ip>:8080/screenshare

🔐 Security: ENABLED
```

### Default Admin Account

No admin account is created by default. To bootstrap an admin user, set the `ADMIN_PASSWORD` environment variable before starting:

```bash
export ADMIN_PASSWORD="YourStrongAdminPass123!"
mvn spring-boot:run
```

| Setting | Env Var | Default |
|---------|---------|--------|
| Username | `ADMIN_USERNAME` | `admin` |
| Password | `ADMIN_PASSWORD` | *(not set — bootstrap skipped if blank)* |

---

## Configuration

All settings are in `src/main/resources/application.yml` and can be overridden with environment variables.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Server port |
| `JWT_SECRET` | *(empty — random key generated per startup)* | JWT signing secret (min 32 chars). **Set this for production!** |
| `CORS_ALLOWED_ORIGINS` | `localhost` | Comma-separated allowed origins |
| `ADMIN_USERNAME` | `admin` | Default admin username |
| `ADMIN_PASSWORD` | *(empty — bootstrap skipped)* | Admin password. **Must be set to create admin user.** |
| `DATABASE_URL` | H2 in-memory | R2DBC database URL |
| `DB_USERNAME` | `sa` | Database username |
| `DB_PASSWORD` | (empty) | Database password |
| `H2_CONSOLE_ENABLED` | `false` | Enable H2 web console |

### Setting Environment Variables

**Linux/macOS:**
```bash
export JWT_SECRET="your-super-secure-256-bit-secret-key-here!!"
export SERVER_PORT=9090
mvn spring-boot:run
```

**Or pass them inline:**
```bash
JWT_SECRET="my-secret" SERVER_PORT=9090 mvn spring-boot:run
```

---

## Production Deployment

### 1. Use a Proper Database

Replace H2 with PostgreSQL:

```bash
export DATABASE_URL="r2dbc:postgresql://localhost:5432/screenai"
export DB_USERNAME="screenai_user"
export DB_PASSWORD="strong-password"
```

Add the PostgreSQL R2DBC driver to `pom.xml`:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 2. Set a Strong JWT Secret

```bash
# Generate a 256-bit secret
openssl rand -base64 32
# Use the output as JWT_SECRET
export JWT_SECRET="generated-secret-here"
```

### 3. Enable HTTPS/WSS

Place behind a reverse proxy (nginx, Caddy) with TLS:

**nginx example:**
```nginx
server {
    listen 443 ssl;
    server_name screenai.example.com;

    ssl_certificate /etc/letsencrypt/live/screenai.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/screenai.example.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;
    }
}
```

### 4. Configure CORS

```bash
export CORS_ALLOWED_ORIGINS="https://screenai.example.com,https://app.example.com"
```

### 5. Change Default Admin Password

```bash
export ADMIN_USERNAME="myadmin"
export ADMIN_PASSWORD="MyStrongAdminPass123!"
```

### 6. Run as a Service (systemd)

Create `/etc/systemd/system/screenai-server.service`:

```ini
[Unit]
Description=ScreenAI Server
After=network.target

[Service]
Type=simple
User=screenai
WorkingDirectory=/opt/screenai-server
ExecStart=/usr/bin/java -jar screenai-server-1.0.0.jar
Environment=JWT_SECRET=your-secret
Environment=DATABASE_URL=r2dbc:postgresql://localhost:5432/screenai
Environment=DB_USERNAME=screenai_user
Environment=DB_PASSWORD=strong-password
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable screenai-server
sudo systemctl start screenai-server
```

---

## Testing the Server

### Using curl

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "Test@12345"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "Test@12345"}'

# Admin logs (use admin token)
curl http://localhost:8080/api/admin/logs \
  -H "Authorization: Bearer <admin-token>"
```

### Using wscat

```bash
npm install -g wscat

# Connect with JWT token
wscat -c "ws://localhost:8080/screenshare?token=<your-jwt>"

# Create a room
> {"type":"create-room","roomId":"test-room"}

# Join a room (from another terminal)
> {"type":"join-room","roomId":"test-room"}
```

---

## Logging

Logs are written to both console and `logs/screenai.log`.

| Logger | Level | Content |
|--------|-------|---------|
| Root | INFO | General application logs |
| `com.screenai` | INFO | Application logic |
| `com.screenai.security` | DEBUG | Security events (auth, rate limits, IP blocks) |
| `reactor.netty` | WARN | Netty internals (reduced noise) |

Log file rotation: 10 MB max size, 5 history files.

### Custom Log Levels

```bash
# Enable debug logging for all ScreenAI packages
export LOGGING_LEVEL_COM_SCREENAI=DEBUG

# Or for specific packages
export LOGGING_LEVEL_COM_SCREENAI_HANDLER=DEBUG
```
