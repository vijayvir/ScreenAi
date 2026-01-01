package com.screenai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;

import com.screenai.model.ViewerSession;
import java.time.LocalDateTime;

/**
 * Integration tests for viewer session joining and expiry
 */
@DisplayName("Viewer Session Integration Tests")
@SpringBootTest
class ViewerSessionJoiningTest {
    
    @Test
    @DisplayName("ViewerSession can be created with proper timestamps")
    void testViewerSessionCreation() {
        // Arrange
        String sessionId = "SESSION-123";
        LocalDateTime expiryTime = LocalDateTime.now().plusHours(1);
        LocalDateTime hostCreatedAt = LocalDateTime.now();
        
        // Act
        ViewerSession session = new ViewerSession(sessionId, expiryTime, hostCreatedAt);
        
        // Assert
        assert session.getSessionId().equals(sessionId) : "SessionId should match";
        assert session.getExpiryTime().equals(expiryTime) : "ExpiryTime should match";
        assert session.getHostCreatedAt().equals(hostCreatedAt) : "HostCreatedAt should match";
        assert session.getViewerJoinedAt() != null : "ViewerJoinedAt should be set";
        assert !session.isExpired() : "Session should not be expired";
        
        System.out.println("✓ ViewerSession created successfully");
        System.out.println("  - Session ID: " + session.getSessionId());
        System.out.println("  - Host created at: " + session.getHostCreatedAt());
        System.out.println("  - Viewer joined at: " + session.getViewerJoinedAt());
        System.out.println("  - Expires at: " + session.getExpiryTime());
    }
    
    @Test
    @DisplayName("ViewerSession correctly detects expiration")
    void testSessionExpiration() {
        // Arrange
        String sessionId = "SESSION-456";
        LocalDateTime pastExpiryTime = LocalDateTime.now().minusMinutes(1); // Already expired
        LocalDateTime hostCreatedAt = LocalDateTime.now().minusHours(1);
        
        // Act
        ViewerSession session = new ViewerSession(sessionId, pastExpiryTime, hostCreatedAt);
        
        // Assert
        assert session.isExpired() : "Session should be expired";
        
        System.out.println("✓ Session expiration detected correctly");
        System.out.println("  - Session expired: " + session.isExpired());
    }
    
    @Test
    @DisplayName("ViewerSession string representation")
    void testSessionToString() {
        // Arrange
        String sessionId = "SESSION-789";
        LocalDateTime expiryTime = LocalDateTime.now().plusHours(1);
        LocalDateTime hostCreatedAt = LocalDateTime.now();
        
        // Act
        ViewerSession session = new ViewerSession(sessionId, expiryTime, hostCreatedAt);
        String stringRep = session.toString();
        
        // Assert
        assert stringRep.contains(sessionId) : "String should contain sessionId";
        assert stringRep.contains("expiryTime") : "String should contain expiryTime";
        
        System.out.println("✓ Session string representation correct");
        System.out.println("  - " + stringRep);
    }
}
