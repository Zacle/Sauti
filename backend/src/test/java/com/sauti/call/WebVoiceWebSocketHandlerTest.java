package com.sauti.call;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class WebVoiceWebSocketHandlerTest {

    @Test
    void treatsAPlaybackStallAsAnInterruptionOfTheServerTtsContext() {
        var tokenService = mock(WebVoiceTokenService.class);
        var sessionService = mock(WebVoiceSessionService.class);
        var socket = mock(WebSocketSession.class);
        var attributes = new HashMap<String, Object>();
        attributes.put("webVoiceCallSid", "web-call-1");
        when(socket.getAttributes()).thenReturn(attributes);
        var handler = new WebVoiceWebSocketHandler(tokenService, sessionService);

        handler.handleTextMessage(socket, new TextMessage("{\"type\":\"playback_stalled\"}"));

        verify(sessionService).interrupt("web-call-1");
    }
}
