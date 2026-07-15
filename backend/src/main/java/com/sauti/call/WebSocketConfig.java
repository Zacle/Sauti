package com.sauti.call;

import com.sauti.dashboard.DashboardWebSocketHandler;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final TwilioMediaWebSocketHandler twilioHandler;
    private final DashboardWebSocketHandler dashboardHandler;
    private final WebVoiceWebSocketHandler webVoiceHandler;
    private final HybridVoiceWebSocketHandler hybridVoiceHandler;
    private final TelnyxMediaWebSocketHandler telnyxHandler;
    private final String allowedOriginPatterns;

    public WebSocketConfig(
            TwilioMediaWebSocketHandler twilioHandler,
            DashboardWebSocketHandler dashboardHandler,
            WebVoiceWebSocketHandler webVoiceHandler,
            HybridVoiceWebSocketHandler hybridVoiceHandler,
            TelnyxMediaWebSocketHandler telnyxHandler,
            @Value("${sauti.twilio.websocket.allowed-origin-patterns:*}") String allowedOriginPatterns
    ) {
        this.twilioHandler = twilioHandler;
        this.dashboardHandler = dashboardHandler;
        this.webVoiceHandler = webVoiceHandler;
        this.hybridVoiceHandler = hybridVoiceHandler;
        this.telnyxHandler = telnyxHandler;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var origins = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        registry.addHandler(twilioHandler, "/ws/twilio/media/{callSid}")
                .setAllowedOriginPatterns(origins);
        registry.addHandler(dashboardHandler, "/ws/dashboard/{tenantId}")
                .setAllowedOriginPatterns(Arrays.stream(allowedOriginPatterns.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toArray(String[]::new));
        registry.addHandler(webVoiceHandler, "/ws/web-voice/{callSid}")
                .setAllowedOriginPatterns(origins);
        registry.addHandler(hybridVoiceHandler, "/ws/hybrid-voice/{callSid}")
                .setAllowedOriginPatterns(origins);
        registry.addHandler(telnyxHandler, "/ws/telnyx/media/{callControlId}")
                .setAllowedOriginPatterns(origins);
    }
}
