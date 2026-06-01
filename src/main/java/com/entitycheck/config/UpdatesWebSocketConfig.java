package com.entitycheck.config;

import com.entitycheck.ws.UpdatesWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class UpdatesWebSocketConfig implements WebSocketConfigurer {

    private final UpdatesWebSocketHandler updatesWebSocketHandler;

    public UpdatesWebSocketConfig(UpdatesWebSocketHandler updatesWebSocketHandler) {
        this.updatesWebSocketHandler = updatesWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(updatesWebSocketHandler, "/ws/updates").setAllowedOriginPatterns("*");
    }
}
