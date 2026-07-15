package com.sauti.call;

import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class HybridVoiceWebSocketHandler extends AbstractWebSocketHandler {
    private static final String CALL_SID = "hybridVoiceCallSid";
    private final WebVoiceTokenService tokenService;
    private final HybridVoiceSessionService sessionService;

    public HybridVoiceWebSocketHandler(WebVoiceTokenService tokenService, HybridVoiceSessionService sessionService) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            var principal = tokenService.verify(queryParameter(session.getUri(), "token"));
            var callSid = pathCallSid(session.getUri());
            if (!principal.callSid().equals(callSid)) throw new IllegalArgumentException("Session token mismatch");
            session.getAttributes().put(CALL_SID, callSid);
            sessionService.start(
                    callSid,
                    principal.publicAgentId(),
                    new ConcurrentWebSocketSessionDecorator(session, 5_000, 1_048_576)
            );
        } catch (Exception exception) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid hybrid voice session"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var callSid = (String) session.getAttributes().get(CALL_SID);
        if (callSid != null) sessionService.accept(callSid, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var callSid = (String) session.getAttributes().get(CALL_SID);
        if (callSid != null) sessionService.stop(callSid);
    }

    private String queryParameter(URI uri, String name) {
        if (uri == null) throw new IllegalArgumentException("Missing WebSocket URI");
        var value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing token");
        return value;
    }

    private String pathCallSid(URI uri) {
        if (uri == null || uri.getPath() == null) throw new IllegalArgumentException("Missing session path");
        return uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
    }
}
