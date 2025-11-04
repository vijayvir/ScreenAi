# Phase-2 Implementation - Testing Guide

## ✅ **Implementation Complete**

Phase-2 viewer join functionality has been successfully implemented! Here's what was created:

### **📁 Files Created/Modified:**

1. ✅ **ViewerSession.java** - New data model for viewer sessions
2. ✅ **AccessCodeService.java** - Extended with join functionality
3. ✅ **AccessCodeController.java** - New `/api/join` endpoint added

### **🔧 How to Test**

#### **Step 1: Restart the Application**

The running application needs to be restarted to load the new Phase-2 code:

```bash
# Stop the current application (Ctrl+C in the terminal running it)
# Then restart:
cd ScreenAi
.\mvnw.cmd spring-boot:run
```

#### **Step 2: Create a Host Session**

Create a new session to get an access code:

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/start" -Method POST -ContentType "application/json" | Select-Object -ExpandProperty Content
```

**Expected Response:**
```json
{
  "code": "518037",
  "sessionId": "15da7a14-79c6-4347-8711-1d6b100974a3",
  "token": "c13795864b2d453bb6eb82c5b840819d"
}
```

**Save the `code` value** (e.g., "518037") for the next step.

#### **Step 3: Test Viewer Join**

Join the session with the access code:

```powershell
$body = '{"code": "518037"}'
Invoke-WebRequest -Uri "http://localhost:8080/api/join" -Method POST -Body $body -ContentType "application/json" | Select-Object -ExpandProperty Content
```

**✅ Expected Success Response:**
```json
{
  "message": "Access granted",
  "sessionId": "15da7a14-79c6-4347-8711-1d6b100974a3",
  "viewerToken": "a1b2c3d4e5f6789012345678901234567890abcd"
}
```

**❌ Expected Error Response (for invalid code):**
```json
{
  "error": "Invalid or expired access code"
}
```

#### **Step 4: Test with Invalid Code**

Test error handling with an invalid code:

```powershell
$body = '{"code": "999999"}'
Invoke-WebRequest -Uri "http://localhost:8080/api/join" -Method POST -Body $body -ContentType "application/json"
```

**Expected:** HTTP 400 Bad Request with error message

#### **Step 5: Test Missing Code**

Test error handling with missing code:

```powershell
$body = '{}'
Invoke-WebRequest -Uri "http://localhost:8080/api/join" -Method POST -Body $body -ContentType "application/json"
```

**Expected:** HTTP 400 Bad Request with "Access code is required"

## **📊 Complete System Flow**

### **Phase-1: Host Creates Session**
```
1. Host clicks "Start Sharing"
2. Frontend calls POST /api/start
3. Backend generates:
   - 6-digit code: "518037"
   - UUID sessionId: "15da7a14-79c6-4347-8711-1d6b100974a3"
   - Secure token: "c13795864b2d453bb6eb82c5b840819d"
4. Stores in sessionStorage HashMap:
   {"518037" → {sessionId, token, expiryTime}}
5. Returns code, sessionId, token to host
```

### **Phase-2: Viewer Joins Session**
```
1. Viewer enters access code: "518037"
2. Frontend calls POST /api/join with {"code": "518037"}
3. Backend validates:
   ✓ Checks if code exists in sessionStorage
   ✓ Checks if code is not expired
4. If valid:
   - Retrieves sessionId from sessionStorage
   - Generates viewer token: "random-uuid-string"
   - Stores in viewerStorage HashMap:
     {"viewerToken" → {sessionId, expiryTime}}
5. Returns: {message, sessionId, viewerToken}
```

## **🔐 Data Structure**

### **Host Sessions (Phase-1)**
```java
sessionStorage HashMap:
Key: "518037" (access code)
Value: SessionInfo {
    sessionId: "15da7a14-79c6-4347-8711-1d6b100974a3"
    token: "c13795864b2d453bb6eb82c5b840819d"
    expiryTime: LocalDateTime (30 min from now)
}
```

### **Viewer Sessions (Phase-2)**
```java
viewerStorage HashMap:
Key: "a1b2c3d4e5f6789012345678901234567890abcd" (viewer token)
Value: ViewerSession {
    sessionId: "15da7a14-79c6-4347-8711-1d6b100974a3"
    expiryTime: LocalDateTime (30 min from now)
}
```

## **🎯 Key Features**

✅ **Input Validation** - Checks for null/empty codes  
✅ **Session Expiry** - Automatic expiration after 30 minutes  
✅ **Thread Safety** - Uses ConcurrentHashMap  
✅ **Error Handling** - Comprehensive error messages  
✅ **Logging** - Detailed logs for debugging  
✅ **Beginner Comments** - Every step explained  

## **🚀 What's Next (Phase-3)**

Phase-3 will integrate WebSocket connections so viewers can:
- Connect using the viewerToken
- Receive real-time screen frames
- Display live screen sharing stream

## **📝 Example Complete Test Flow**

```powershell
# 1. Start session as host
$startResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/start" -Method POST -ContentType "application/json" | ConvertFrom-Json

# 2. Display access code to share
Write-Host "Share this code with viewers: $($startResponse.code)"
Write-Host "Session ID: $($startResponse.sessionId)"

# 3. Viewer joins with the code
$joinBody = @{code=$startResponse.code} | ConvertTo-Json
$joinResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/join" -Method POST -Body $joinBody -ContentType "application/json" | ConvertFrom-Json

# 4. Display results
Write-Host "Viewer joined successfully!"
Write-Host "Session ID: $($joinResponse.sessionId)"
Write-Host "Viewer Token: $($joinResponse.viewerToken)"
```

## **✅ Testing Checklist**

- [ ] Application restarts without errors
- [ ] POST /api/start creates new session
- [ ] POST /api/join with valid code returns sessionId + viewerToken
- [ ] POST /api/join with invalid code returns error
- [ ] POST /api/join with expired code returns error
- [ ] POST /api/join with missing code returns error
- [ ] Console logs show detailed information
- [ ] No null pointer exceptions

**Phase-2 is complete and ready for testing!** 🎉

