# WebSocket Connection Debugging Guide

## 🎯 Purpose
This guide helps you debug and verify the WebSocket connection between the JavaFX client and the server.

## ✅ Current Setup

### Server
- **Status**: ✅ Running
- **URL**: `ws://localhost:8080/screenshare`
- **Terminal**: Check for connection logs

### Client  
- **Status**: ✅ Enhanced logging enabled
- **Default Server**: `localhost`
- **Default Port**: `8080`

---

## 📝 Step-by-Step Testing Process

### Step 1: Verify Server is Running

Check the server terminal - you should see:
```
═══════════════════════════════════════════════════════
   ScreenAI-Server (Relay Mode) Started Successfully   
═══════════════════════════════════════════════════════
```

✅ If you see this, server is ready!

---

### Step 2: Start the Client

```bash
cd /Users/rajatkumar/Documents/Rajat/ScreenAI-Client/untitled
./run.sh
```

Wait for:
```
✅ JavaFX UI loaded successfully
```

✅ The JavaFX window should now be open!

---

### Step 3: Fill in Connection Details

In the **Host Section** of the JavaFX window:

1. **Server Address**: `localhost` (should be pre-filled)
2. **Port**: `8080` (should be pre-filled)
3. **Room ID**: Leave as-is or enter your own (e.g., `test-room-123`)

---

### Step 4: Click Connect Button

When you click the **"Connect"** button, watch the **client terminal** for detailed logs:

#### What You Should See:

```
=================================================
🔌 connectAsHost() called - START
=================================================
📍 Server input: 'localhost'
📍 Port input: '8080'
✅ Port parsed successfully: 8080
🔄 Updating UI to 'Connecting...'
📞 Calling hostController.connect(localhost, 8080)
=================================================
🔌 connectAsHost() called - END
=================================================

=================================================
🔌 [HOST] connect() called
=================================================
📍 serverHost: localhost
📍 serverPort: 8080
🌐 Full WebSocket URL: ws://localhost:8080/screenshare
✅ URL constructed successfully
🏗️ Creating ServerConnectionService...
✅ ServerConnectionService initialized
📍 Server URL: ws://localhost:8080/screenshare
✅ ServerConnectionService created
🔧 Setting up connection handlers...
✅ All handlers configured
🚀 Starting connection in background thread...
✅ Background connection thread submitted
=================================================
🔌 [HOST] connect() method completed
=================================================

🔄 [THREAD] Calling serverConnection.connect()...
🔌 [ServerConnectionService] Attempting to connect to: ws://localhost:8080/screenshare
📍 Host: localhost:8080
📍 Path: /screenshare
⏳ [ServerConnectionService] Starting WebSocket handshake...
⏳ [ServerConnectionService] Waiting for connection (timeout: 10000ms)...
✅ [ServerConnectionService] Connection established! Session: <session-id>
🎉 [HANDLER] Connection OPENED!
✅ [HOST] Connected to server!
```

#### In the Server Terminal:

```
🔌 New connection: <session-id> from IP: /127.0.0.1:xxxxx
📊 Server stats: 1 total sessions, 0 rooms
```

---

### Step 5: Verify Connection Status

**In the JavaFX GUI**, you should see:

- **Connection Status Label**: Changes from `🔴 Disconnected` to `✅ Connected`
- **Connect Button**: Becomes disabled (grayed out)
- **Start Button**: Becomes enabled
- **Status Label**: Shows `✅ Connected to server`

---

## 🐛 Common Issues & Solutions

### Issue 1: Nothing Happens When Clicking Connect

**Check:**
1. Is the server actually running? Check server terminal
2. Are the Server/Port fields filled in the GUI?
3. Check client terminal for error messages

**Solution:**
```bash
# Verify server is running
lsof -i:8080

# If nothing shown, restart server
cd /Users/rajatkumar/Documents/Rajat/ScreenAi
java -jar /Users/rajatkumar/Documents/Rajat/ScreenAi/target/screenai-server-1.0.0.jar
```

---

### Issue 2: Connection Timeout

**Client Terminal Shows:**
```
❌ [ServerConnectionService] Connection timeout after 10000ms
   Make sure the server is running on localhost:8080
```

**Solution:**
- Server is not running or not accessible
- Restart the server
- Check firewall settings

---

### Issue 3: Port Already in Use

**Server Terminal Shows:**
```
Port 8080 was already in use.
```

**Solution:**
```bash
# Kill process on port 8080
lsof -ti:8080 | xargs kill -9

# Restart server
java -jar /Users/rajatkumar/Documents/Rajat/ScreenAi/target/screenai-server-1.0.0.jar
```

---

### Issue 4: GUI Shows "Disconnected" but Logs Show "Connected"

**Possible Cause:**
- UI update might be slow
- Check if handlers are being called

**In Client Terminal, Look For:**
```
🎉 [HANDLER] Connection OPENED!
```

If you see this but GUI doesn't update, there might be a JavaFX Platform.runLater issue.

---

## 🔍 What Each Status Means

### Client Terminal Messages:

| Message | Meaning |
|---------|---------|
| `🔌 connectAsHost() called - START` | Connect button was clicked |
| `✅ Port parsed successfully` | Server address is valid |
| `🏗️ Creating ServerConnectionService` | Starting connection process |
| `🚀 Starting connection in background thread` | Connection attempt in progress |
| `✅ [ServerConnectionService] Connection established!` | **WebSocket connected!** |
| `🎉 [HANDLER] Connection OPENED!` | **Connection confirmed!** |
| `❌ Connection timeout` | Server not reachable |

### GUI Status Indicators:

| Indicator | Meaning |
|-----------|---------|
| `🔴 Disconnected` | Not connected to server |
| `⏳ Connecting...` | Connection in progress |
| `✅ Connected` | **Successfully connected!** |
| `⚠️ Enter server address and port` | Fill in required fields |

---

## 🧪 Manual Connection Test

If GUI is not working, test WebSocket manually:

```bash
# Install wscat (if not installed)
npm install -g wscat

# Test connection
wscat -c ws://localhost:8080/screenshare

# Expected output:
Connected (press CTRL+C to quit)
< {"type":"connected","sessionId":"xxx","message":"Connected to ScreenAI Relay Server","role":"pending"}

# Try creating a room:
> {"type":"create-room","roomId":"manual-test"}

# Expected response:
< {"type":"room-created","roomId":"manual-test","role":"presenter"}
```

If this works, your server is fine - the issue is in the client.

---

## ✨ Testing the Full Flow

### 1. Start Server
```bash
cd /Users/rajatkumar/Documents/Rajat/ScreenAi
java -jar /Users/rajatkumar/Documents/Rajat/ScreenAi/target/screenai-server-1.0.0.jar
```

### 2. Start Client (Host)
```bash
cd /Users/rajatkumar/Documents/Rajat/ScreenAI-Client/untitled
./run.sh
```

### 3. In GUI:
- Keep `localhost` and `8080`
- Click **"Connect"** button
- Wait for `✅ Connected`
- Click **"Start Streaming"** button

### 4. Check Server Terminal:
```
🔌 New connection: <id> from IP: /127.0.0.1
✅ Room created: room-xxx by presenter: <id>
```

### 5. Start Second Client (Viewer)
- Open new terminal
- Run `./run.sh` again
- Switch to **"Viewer"** mode
- Enter same Room ID
- Click **"Connect"** then **"Join Room"**

### 6. Server Should Show:
```
✅ Viewer <id> joined room: room-xxx (Total viewers: 1)
```

---

## 📊 Success Checklist

- [ ] Server shows: `ScreenAI-Server (Relay Mode) Started Successfully`
- [ ] Client terminal shows: `✅ JavaFX UI loaded successfully`
- [ ] After clicking Connect: `🎉 [HANDLER] Connection OPENED!`
- [ ] GUI shows: `✅ Connected`
- [ ] Server shows: `🔌 New connection: <id>`
- [ ] Connect button becomes disabled
- [ ] Start button becomes enabled

If all checkboxes are checked ✅ **Your connection is working perfectly!**

---

## 🎯 Next Steps After Connection

Once connected successfully:

1. **Start Streaming**: Click "Start Streaming" button
2. **Create Room**: Server will create a room with your Room ID
3. **Share Room ID**: Give the Room ID to viewers
4. **Monitor**: Watch viewer count and performance metrics
5. **Stop**: Click "Stop" when done

---

## 📞 Need More Help?

Check these logs in order:

1. **Client Terminal** - Look for exceptions or errors
2. **Server Terminal** - Check if connection attempts are received
3. **GUI Status Labels** - What do they show?
4. **Network** - Run `netstat -an | grep 8080` to verify port is open

The enhanced logging will show you exactly where the process stops!
