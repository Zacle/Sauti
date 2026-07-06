package com.sauti.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.HashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class TelnyxMediaWebSocketHandler extends AbstractWebSocketHandler {
    private static final String STREAM_ID = "telnyxStreamId";
    private static final String CALL_CONTROL_ID = "telnyxCallControlId";
    private final ObjectMapper objectMapper;
    private final CallRepository callRepository;
    private final TwilioMediaStreamService mediaStreamService;

    public TelnyxMediaWebSocketHandler(
            ObjectMapper objectMapper,
            CallRepository callRepository,
            TwilioMediaStreamService mediaStreamService
    ) {
        this.objectMapper = objectMapper;
        this.callRepository = callRepository;
        this.mediaStreamService = mediaStreamService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception exception) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        switch (node.path("event").asText("")) {
            case "start" -> start(session, node);
            case "media" -> media(session, node);
            case "mark" -> mediaStreamService.markReceived(
                    callControlId(session),
                    streamId(session, node),
                    node.path("mark").path("name").asText("")
            );
            case "dtmf" -> mediaStreamService.acceptDtmf(
                    callControlId(session),
                    node.path("dtmf").path("digit").asText("")
            );
            case "stop" -> session.close(CloseStatus.NORMAL);
            case "error" -> session.close(CloseStatus.SERVER_ERROR);
            default -> {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        mediaStreamService.stop(callControlId(session), attribute(session, STREAM_ID));
    }

    private void start(WebSocketSession session, JsonNode node) throws Exception {
        var start = node.path("start");
        var callControlId = start.path("call_control_id").asText(pathCallControlId(session));
        var streamId = node.path("stream_id").asText("");
        var call = callRepository.findByTwilioCallSid(callControlId).orElse(null);
        if (call == null || !call.isActive() || streamId.isBlank()) {
            session.close(CloseStatus.NORMAL);
            return;
        }
        session.getAttributes().put(CALL_CONTROL_ID, callControlId);
        session.getAttributes().put(STREAM_ID, streamId);
        var parameters = customParameters(start.path("custom_parameters"));
        parameters.put("_mediaProvider", "telnyx");
        mediaStreamService.start(
                callControlId,
                streamId,
                mediaFormat(start.path("media_format")),
                parameters,
                new TwilioOutboundMediaSender() {
                    @Override
                    public void send(String textFrame) {
                        try {
                            synchronized (session) {
                                if (session.isOpen()) session.sendMessage(new TextMessage(textFrame));
                            }
                        } catch (Exception exception) {
                            try {
                                session.close(CloseStatus.SERVER_ERROR);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void close() {
                        try {
                            if (session.isOpen()) session.close(CloseStatus.NORMAL);
                        } catch (Exception ignored) {
                        }
                    }
                }
        );
    }

    private void media(WebSocketSession session, JsonNode node) throws Exception {
        var callControlId = callControlId(session);
        var call = callRepository.findByTwilioCallSid(callControlId).orElse(null);
        if (call == null || !call.isActive()) {
            session.close(CloseStatus.NORMAL);
            return;
        }
        try {
            var encodedAudio = node.path("media").path("payload").asText("");
            if (encodedAudio.isBlank()) return;
            var audio = Base64.getDecoder().decode(encodedAudio);
            mediaStreamService.acceptInboundAudio(
                    callControlId,
                    streamId(session, node),
                    node.path("sequence_number").asText(""),
                    node.path("media").path("chunk").asText(""),
                    node.path("media").path("timestamp").asText(""),
                    audio
            );
        } catch (IllegalArgumentException exception) {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    private TwilioMediaFormat mediaFormat(JsonNode node) {
        return new TwilioMediaFormat(
                node.path("encoding").asText(""),
                node.path("sample_rate").asInt(0),
                node.path("channels").asInt(0)
        );
    }

    private HashMap<String, String> customParameters(JsonNode node) {
        var result = new HashMap<String, String>();
        if (node.isObject()) node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText("")));
        return result;
    }

    private String streamId(WebSocketSession session, JsonNode node) {
        var value = node.path("stream_id").asText(attribute(session, STREAM_ID));
        if (!value.isBlank()) session.getAttributes().put(STREAM_ID, value);
        return value;
    }

    private String callControlId(WebSocketSession session) {
        var value = attribute(session, CALL_CONTROL_ID);
        return value.isBlank() ? pathCallControlId(session) : value;
    }

    private String pathCallControlId(WebSocketSession session) {
        var path = session.getUri() == null ? "" : session.getUri().getPath();
        return java.net.URLDecoder.decode(
                path.substring(path.lastIndexOf('/') + 1),
                java.nio.charset.StandardCharsets.UTF_8
        );
    }

    private String attribute(WebSocketSession session, String key) {
        var value = session.getAttributes().get(key);
        return value == null ? "" : value.toString();
    }
}
