package com.sauti.call;

import com.sauti.dashboard.DashboardWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final DashboardWebSocketHandler dashboardHandler;
    private final String allowedOriginPatterns;

    public WebSocketConfig(
            DashboardWebSocketHandler dashboardHandler,
            @Value("${sauti.websocket.allowed-origin-patterns:*}") String allowedOriginPatterns
    ) {
        this.dashboardHandler = dashboardHandler;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var origins = java.util.Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        registry.addHandler(dashboardHandler, "/ws/dashboard/{tenantId}")
                .setAllowedOriginPatterns(origins);
    }
}
