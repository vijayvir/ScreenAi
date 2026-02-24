package com.screenai.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.screenai.service.ScreenCaptureService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive WebSocket handler for broadcasting server-captured screen frames.
 *
 * When a client connects to {@code /capture}, this handler sends a welcome
 * message followed by a continuous stream of Base64-encoded JPEG frames
 * captured by {@link ScreenCaptureService}.
 *
 * This is separate from {@link ReactiveScreenShareHandler} (which handles
 * the relay-based {@code /screenshare} endpoint) so that both modes can
 * coexist independently.
 */
@Component
public class ReactiveCaptureViewHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveCaptureViewHandler.class);

    private final ScreenCaptureService screenCaptureService;

    public ReactiveCaptureViewHandler(ScreenCaptureService screenCaptureService) {
        this.screenCaptureService = screenCaptureService;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        logger.info("New capture viewer connected: {}", session.getId());

        // Build a message flux: welcome message then continuous frame stream
        Flux<WebSocketMessage> messageFlux = Flux.concat(
                Flux.just(session.textMessage(
                        "{\"type\":\"connected\",\"message\":\"Connected to screen capture\"}")),
                screenCaptureService.getFrameFlux()
                        .map(frameData -> session.textMessage(
                                "{\"type\":\"frame\",\"data\":\"data:image/jpeg;base64,"
                                        + frameData + "\"}"))
        );

        return session.send(messageFlux)
                .doOnTerminate(() -> logger.info("Capture viewer disconnected: {}", session.getId()))
                .doOnError(e -> logger.debug("Capture viewer error {}: {}", session.getId(), e.getMessage()));
    }
}
