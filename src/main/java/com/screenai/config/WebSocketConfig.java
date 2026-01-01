package com.screenai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.screenai.handler.ScreenShareWebSocketHandler;

/**
 * PHASE 3: WebSocket Configuration with Authentication
 * Configures WebSocket endpoints for real-time screen sharing with token authentication
 * 
 * ENDPOINT: /ws/{sessionId}?token=VIEWER_TOKEN
 * Example: ws://localhost:8081/ws/550e8400-e29b-41d4-a716-446655440000?token=abc123...
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final ScreenShareWebSocketHandler screenShareHandler;
    
    public WebSocketConfig(ScreenShareWebSocketHandler screenShareHandler) {
        this.screenShareHandler = screenShareHandler;
    }
    
    /**
     * Register WebSocket handlers and configure allowed origins
     * 
     * PHASE 3: Handler validates token (?token=...) and extracts sessionId ({sessionId})
     * 
     * @param registry WebSocket handler registry
     */
    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        // Register handler at /ws/{sessionId}?token=VIEWER_TOKEN for Phase 3 authentication
        registry.addHandler(screenShareHandler, "/ws/{sessionId}")
                .setAllowedOrigins("*");
    }
}
