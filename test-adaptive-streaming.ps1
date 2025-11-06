# Test script for Adaptive Streaming functionality
# This script tests the adaptive streaming API endpoints

$baseUrl = "http://localhost:8080"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Testing Adaptive Streaming Functionality" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Check Network Quality
Write-Host "Test 1: Checking Network Quality..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/network-quality" -Method Get
    Write-Host "✓ Network Quality API Response:" -ForegroundColor Green
    Write-Host "  - Active Sessions: $($response.activeSessions)" -ForegroundColor White
    Write-Host "  - Overall Quality: $($response.overallQuality)" -ForegroundColor White
    Write-Host "  - Average Latency: $([math]::Round($response.averageLatency, 2))ms" -ForegroundColor White
    Write-Host "  - Average Jitter: $([math]::Round($response.averageJitter, 2))ms" -ForegroundColor White
    Write-Host ""
} catch {
    Write-Host "✗ Failed to get network quality: $_" -ForegroundColor Red
    Write-Host ""
}

# Test 2: Check Status API
Write-Host "Test 2: Checking Capture Status..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/status" -Method Get
    Write-Host "✓ Status API Response:" -ForegroundColor Green
    Write-Host "  - Initialized: $($response.initialized)" -ForegroundColor White
    Write-Host "  - Capturing: $($response.capturing)" -ForegroundColor White
    Write-Host "  - Capture Method: $($response.captureMethod)" -ForegroundColor White
    Write-Host ""
} catch {
    Write-Host "✗ Failed to get status: $_" -ForegroundColor Red
    Write-Host ""
}

# Test 3: Start Capture
Write-Host "Test 3: Starting Screen Capture..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/start-capture" -Method Post
    Write-Host "✓ Capture started successfully!" -ForegroundColor Green
    Write-Host "  - Message: $($response.message)" -ForegroundColor White
    Write-Host ""
    
    # Wait for capture to stabilize
    Write-Host "Waiting 5 seconds for capture to stabilize..." -ForegroundColor Cyan
    Start-Sleep -Seconds 5
    
} catch {
    Write-Host "✗ Failed to start capture: $_" -ForegroundColor Red
    Write-Host ""
}

# Test 4: Check Performance Metrics
Write-Host "Test 4: Checking Performance Metrics..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/performance" -Method Get
    Write-Host "✓ Performance Metrics:" -ForegroundColor Green
    Write-Host "  - FPS: $($response.fps)" -ForegroundColor White
    Write-Host "  - Encoder: $($response.encoderType)" -ForegroundColor White
    Write-Host "  - Frames Captured: $($response.totalFramesCaptured)" -ForegroundColor White
    Write-Host "  - Frames Dropped: $($response.totalFramesDropped)" -ForegroundColor White
    Write-Host ""
} catch {
    Write-Host "✗ Failed to get performance metrics: $_" -ForegroundColor Red
    Write-Host ""
}

# Test 5: Monitor Network Quality Over Time
Write-Host "Test 5: Monitoring Adaptive Streaming (30 seconds)..." -ForegroundColor Yellow
Write-Host "This will monitor quality changes and log them." -ForegroundColor Cyan
Write-Host ""

$lastQuality = ""
for ($i = 0; $i -lt 3; $i++) {
    Start-Sleep -Seconds 10
    
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/network-quality" -Method Get
        $currentQuality = $response.overallQuality
        
        if ($currentQuality -ne $lastQuality) {
            Write-Host "  [$(Get-Date -Format 'HH:mm:ss')] Quality changed: $lastQuality → $currentQuality" -ForegroundColor Yellow
            $lastQuality = $currentQuality
        } else {
            Write-Host "  [$(Get-Date -Format 'HH:mm:ss')] Quality: $currentQuality (stable)" -ForegroundColor Green
        }
        
        Write-Host "    Latency: $([math]::Round($response.averageLatency, 2))ms, Jitter: $([math]::Round($response.averageJitter, 2))ms" -ForegroundColor White
        
    } catch {
        Write-Host "  [$(Get-Date -Format 'HH:mm:ss')] Failed to check quality" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Test 6: Triggering Manual Network Ping..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/network-quality/ping" -Method Get
    Write-Host "✓ Ping triggered for $($response.sessionCount) sessions" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host "✗ Failed to trigger ping: $_" -ForegroundColor Red
    Write-Host ""
}

# Test 7: Check Session-Specific Network Quality
Write-Host "Test 7: Checking Session Network Quality..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/network-quality/sessions" -Method Get
    Write-Host "✓ Session Quality Data:" -ForegroundColor Green
    
    if ($response.sessions -and $response.sessions.PSObject.Properties.Count -gt 0) {
        foreach ($session in $response.sessions.PSObject.Properties) {
            Write-Host "  Session: $($session.Name)" -ForegroundColor White
            Write-Host "    Quality: $($session.Value.quality)" -ForegroundColor White
            Write-Host "    Avg Latency: $([math]::Round($session.Value.averageLatency, 2))ms" -ForegroundColor White
            Write-Host "    Jitter: $([math]::Round($session.Value.jitter, 2))ms" -ForegroundColor White
        }
    } else {
        Write-Host "  No active sessions with network data" -ForegroundColor Gray
    }
    Write-Host ""
} catch {
    Write-Host "✗ Failed to get session quality: $_" -ForegroundColor Red
    Write-Host ""
}

# Test 8: Stop Capture
Write-Host "Test 8: Stopping Screen Capture..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/stop-capture" -Method Post
    Write-Host "✓ Capture stopped successfully!" -ForegroundColor Green
    Write-Host "  - Message: $($response.message)" -ForegroundColor White
    Write-Host ""
} catch {
    Write-Host "✗ Failed to stop capture: $_" -ForegroundColor Red
    Write-Host ""
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Testing Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Check the application logs for adaptive streaming messages:" -ForegroundColor Yellow
Write-Host "  - Look for '🎚️' emoji for parameter adaptations" -ForegroundColor White
Write-Host "  - Look for '📊' emoji for quality assessments" -ForegroundColor White
Write-Host "  - Look for '✅' emoji for successful changes" -ForegroundColor White
Write-Host ""
