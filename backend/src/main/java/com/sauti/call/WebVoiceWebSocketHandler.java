package com.sauti.call;

import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WebVoiceWebSocketHandler extends AbstractWebSocketHandler {
    private static final String CALL_SID = "webVoiceCallSid";
    private final WebVoiceTokenService tokenService;
    private final WebVoiceSessionService sessionService;

    public WebVoiceWebSocketHandler(WebVoiceTokenService tokenService, WebVoiceSessionService sessionService) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            var token = queryParameter(session.getUri(), "token");
            var principal = tokenService.verify(token);
            var pathCallSid = pathCallSid(session.getUri());
            if (!principal.callSid().equals(pathCallSid)) throw new IllegalArgumentException("Session token mismatch");
            session.getAttributes().put(CALL_SID, pathCallSid);
            sessionService.start(pathCallSid, principal.publicAgentId(), session);
        } catch (Exception exception) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid Web Voice session"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        var callSid = (String) session.getAttributes().get(CALL_SID);
        if (callSid == null) return;
        var buffer = message.getPayload();
        var audio = new byte[buffer.remaining()];
        buffer.get(audio);
        sessionService.acceptAudio(callSid, audio);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var callSid = (String) session.getAttributes().get(CALL_SID);
        if (callSid == null) return;
        if (message.getPayload().contains("\"type\":\"interrupt\"")) sessionService.interrupt(callSid);
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
