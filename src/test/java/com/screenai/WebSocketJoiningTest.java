package com.screenai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.screenai.handler.ScreenShareWebSocketHandler;
import com.screenai.service.AccessCodeService;

/**
 * Tests for WebSocket joining functionality
 * Verifies that viewers can successfully join screen sharing sessions with proper authentication
 */
@DisplayName("WebSocket Joining Tests")
class WebSocketJoiningTest {
    
    private ScreenShareWebSocketHandler webSocketHandler;
    private AccessCodeService accessCodeService;
    private WebSocketSession mockSession;
    private String testSessionId;
    private String testToken;
    
    @BeforeEach
    void setUp() {
        accessCodeService = mock(AccessCodeService.class);
        webSocketHandler = new ScreenShareWebSocketHandler(accessCodeService);
        mockSession = mock(WebSocketSession.class);
        testSessionId = "SESSION-123";
        testToken = "TOKEN-ABC123";
    }
    
    @Test
    @DisplayName("Successful joining with valid token")
    void testSuccessfulJoining() throws Exception {
        // Arrange
        String query = "token=" + testToken;
        setupMockSession(testSessionId, query);
        when(accessCodeService.validateViewerToken(testToken))
            .thenReturn(testSessionId);
        
        // Act
        webSocketHandler.afterConnectionEstablished(mockSession);
        
        // Assert - should not close the connection
        verify(mockSession, never()).close();
        System.out.println("✓ Test PASSED: Viewer successfully joined with valid token");
    }
    
    @Test
    @DisplayName("Rejection when missing sessionId in URL")
    void testMissingSessionId() throws Exception {
        // Arrange
        String query = "token=" + testToken;
        setupMockSessionWithoutPathVariable(query);
        
        // Act
        webSocketHandler.afterConnectionEstablished(mockSession);
        
        // Assert - should close with POLICY_VIOLATION
        verify(mockSession).close(argThat(status -> 
            status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
        System.out.println("✓ Test PASSED: Connection rejected - missing sessionId");
    }
    
    @Test
    @DisplayName("Rejection when missing token in query")
    void testMissingToken() throws Exception {
        // Arrange
        String query = "other=value";
        setupMockSession(testSessionId, query);
        
        // Act
        webSocketHandler.afterConnectionEstablished(mockSession);
        
        // Assert
        verify(mockSession).close(argThat(status -> 
            status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
        System.out.println("✓ Test PASSED: Connection rejected - missing token");
    }
    
    @Test
    @DisplayName("Rejection when token is invalid")
    void testInvalidToken() throws Exception {
        // Arrange
        String query = "token=" + testToken;
        setupMockSession(testSessionId, query);
        when(accessCodeService.validateViewerToken(testToken))
            .thenReturn(null); // Invalid token
        
        // Act
        webSocketHandler.afterConnectionEstablished(mockSession);
        
        // Assert
        verify(mockSession).close(argThat(status -> 
            status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
        System.out.println("✓ Test PASSED: Connection rejected - invalid token");
    }
    
    @Test
    @DisplayName("Rejection when sessionId doesn't match token's sessionId")
    void testSessionIdMismatch() throws Exception {
        // Arrange
        String query = "token=" + testToken;
        String differentSessionId = "DIFFERENT-SESSION";
        setupMockSession(testSessionId, query);
        when(accessCodeService.validateViewerToken(testToken))
            .thenReturn(differentSessionId); // Returns different sessionId
        
        // Act
        webSocketHandler.afterConnectionEstablished(mockSession);
        
        // Assert
        verify(mockSession).close(argThat(status -> 
            status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
        System.out.println("✓ Test PASSED: Connection rejected - sessionId mismatch");
    }
    
    @Test
    @DisplayName("Multiple viewers can join same session")
    void testMultipleViewersJoining() throws Exception {
        // Arrange
        String query = "token=" + testToken;
        setupMockSession(testSessionId, query);
        when(accessCodeService.validateViewerToken(testToken))
            .thenReturn(testSessionId);
        
        // Act - First viewer joins
        webSocketHandler.afterConnectionEstablished(mockSession);
        verify(mockSession, never()).close();
        
        // Create second viewer session
        WebSocketSession mockSession2 = mock(WebSocketSession.class);
        String token2 = "TOKEN-XYZ789";
        String query2 = "token=" + token2;
        setupMockSession(mockSession2, testSessionId, query2);
        when(accessCodeService.validateViewerToken(token2))
            .thenReturn(testSessionId);
        
        // Act - Second viewer joins
        webSocketHandler.afterConnectionEstablished(mockSession2);
        
        // Assert
        verify(mockSession2, never()).close();
        System.out.println("✓ Test PASSED: Multiple viewers successfully joined same session");
    }
    
    // Helper methods
    
    private void setupMockSession(String sessionId, String query) throws Exception {
        Map<String, String> pathVariables = new HashMap<>();
        pathVariables.put("sessionId", sessionId);
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("org.springframework.web.socket.server.ServletServerContainerFactoryBean.PATH_VARIABLES", 
                      pathVariables);
        
        when(mockSession.getAttributes()).thenReturn(attributes);
        when(mockSession.getUri()).thenReturn(new URI("ws://localhost:8080/ws/" + sessionId + "?" + query));
    }
    
    private void setupMockSession(WebSocketSession session, String sessionId, String query) throws Exception {
        Map<String, String> pathVariables = new HashMap<>();
        pathVariables.put("sessionId", sessionId);
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("org.springframework.web.socket.server.ServletServerContainerFactoryBean.PATH_VARIABLES", 
                      pathVariables);
        
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/ws/" + sessionId + "?" + query));
    }
    
    private void setupMockSessionWithoutPathVariable(String query) throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("org.springframework.web.socket.server.ServletServerContainerFactoryBean.PATH_VARIABLES", 
                      null);
        
        when(mockSession.getAttributes()).thenReturn(attributes);
        when(mockSession.getUri()).thenReturn(new URI("ws://localhost:8080/ws/?" + query));
    }
}
