package com.screenai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.lang.NonNull;
import com.screenai.handler.ScreenShareWebSocketHandler;

/**
 * WebSocket configuration class
 * Configures WebSocket endpoints for real-time screen sharing
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
     * @param registry WebSocket handler registry
     */
    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        // Register the screen share handler for endpoint /screenshare
        // Allow all origins for simplicity (in production, restrict this)
        registry.addHandler(screenShareHandler, "/screenshare")
                .setAllowedOrigins("*");
    }
}
