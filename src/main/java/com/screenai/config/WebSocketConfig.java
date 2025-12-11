package com.screenai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.screenai.handler.ScreenShareRelayHandler;

/**
 * WebSocket configuration class
 * Configures WebSocket endpoints for real-time screen sharing RELAY
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final ScreenShareRelayHandler relayHandler;
    
    public WebSocketConfig(ScreenShareRelayHandler relayHandler) {
        this.relayHandler = relayHandler;
    }
    
    /**
     * Register WebSocket handlers and configure allowed origins
     * @param registry WebSocket handler registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register the relay handler for endpoint /screenshare
        registry.addHandler(relayHandler, "/screenshare")
                .setAllowedOrigins("*");
    }
}
