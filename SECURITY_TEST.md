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
