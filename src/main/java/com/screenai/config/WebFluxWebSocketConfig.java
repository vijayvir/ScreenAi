package com.screenai.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;

import com.screenai.handler.ReactiveCaptureViewHandler;
import com.screenai.handler.ReactiveScreenShareHandler;

/**
 * WebFlux WebSocket Configuration
 * Uses Netty for non-blocking, high-performance WebSocket handling
 */
@Configuration
public class WebFluxWebSocketConfig {

    private final ReactiveScreenShareHandler screenShareHandler;
    private final ReactiveCaptureViewHandler captureViewHandler;

    public WebFluxWebSocketConfig(ReactiveScreenShareHandler screenShareHandler,
                                  ReactiveCaptureViewHandler captureViewHandler) {
        this.screenShareHandler = screenShareHandler;
        this.captureViewHandler = captureViewHandler;
    }

    /**
     * Map WebSocket handlers to URL paths
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/screenshare", screenShareHandler);
        map.put("/capture", captureViewHandler);

        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        handlerMapping.setUrlMap(map);
        return handlerMapping;
    }

    /**
     * WebSocket handler adapter with custom Netty configuration
     */
    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter(webSocketService());
    }

    /**
     * Configure WebSocket service with Netty-specific settings
     * - Large buffer sizes for video streaming (10MB max frame)
     * - No compression (video is already compressed)
     */
    @Bean
    public WebSocketService webSocketService() {
        // Configure large max frame size for video streaming (10MB)
        ReactorNettyRequestUpgradeStrategy strategy = new ReactorNettyRequestUpgradeStrategy(
            () -> reactor.netty.http.server.WebsocketServerSpec.builder()
                .maxFramePayloadLength(10 * 1024 * 1024)  // 10MB max frame size
        );
        return new HandshakeWebSocketService(strategy);
    }
}
