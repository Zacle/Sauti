package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.TextMessage;

@Component
public class TwilioMediaWebSocketHandler extends AbstractWebSocketHandler {
    private static final String STREAM_SID_ATTRIBUTE = "twilioStreamSid";
    private static final String CALL_SID_ATTRIBUTE = "twilioCallSid";

    private final ObjectMapper objectMapper;
    private final CallRepository callRepository;
    private final CallPipelineService callPipelineService;
    private final TwilioMediaStreamService mediaStreamService;

    public TwilioMediaWebSocketHandler(
            ObjectMapper objectMapper,
            CallRepository callRepository,
            CallPipelineService callPipelineService,
            TwilioMediaStreamService mediaStreamService
    ) {
        this.objectMapper = objectMapper;
        this.callRepository = callRepository;
        this.callPipelineService = callPipelineService;
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
        var event = node.path("event").asText();

        if ("connected".equals(event)) {
            return;
        }
        if ("start".equals(event)) {
            handleStart(session, node);
            return;
        }
        if ("mark".equals(event)) {
            var streamSid = streamSid(session, node);
            if (!streamSid.isBlank()) {
                mediaStreamService.markReceived(callSid(session), streamSid, node.path("mark").path("name").asText(""));
            }
            return;
        }
        if ("stop".equals(event)) {
            session.close(CloseStatus.NORMAL);
            return;
        }
        if ("dtmf".equals(event)) {
            mediaStreamService.acceptDtmf(callSid(session), node.path("dtmf").path("digit").asText(""));
            return;
        }
        if (!"media".equals(event)) {
            return;
        }

        var call = callRepository.findByTwilioCallSid(callSid(session)).orElse(null);
        if (call == null || !call.isActive()) {
            session.close(CloseStatus.NORMAL);
            return;
        }

        var payload = node.path("media").path("payload").asText("");
        if (payload.isBlank()) {
            return;
        }

        byte[] audio;
        try {
            audio = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException exception) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        var streamSid = streamSid(session, node);
        mediaStreamService.acceptInboundAudio(
                call.getTwilioCallSid(),
                streamSid,
                node.path("sequenceNumber").asText(""),
                node.path("media").path("chunk").asText(""),
                node.path("media").path("timestamp").asText(""),
                audio
        );
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var streamSid = attribute(session, STREAM_SID_ATTRIBUTE);
        mediaStreamService.stop(callSid(session), streamSid);
        callPipelineService.completeActiveCall(callSid(session), "completed");
    }

    private void handleStart(WebSocketSession session, JsonNode node) throws Exception {
        var start = node.path("start");
        var streamSid = start.path("streamSid").asText(node.path("streamSid").asText(""));
        var callSid = start.path("callSid").asText(callSid(session));
        if (streamSid.isBlank() || callSid.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        var call = callRepository.findByTwilioCallSid(callSid).orElse(null);
        if (call == null || !call.isActive()) {
            session.close(CloseStatus.NORMAL);
            return;
        }

        session.getAttributes().put(STREAM_SID_ATTRIBUTE, streamSid);
        session.getAttributes().put(CALL_SID_ATTRIBUTE, callSid);
        mediaStreamService.start(
                callSid,
                streamSid,
                mediaFormat(start.path("mediaFormat")),
                customParameters(start.path("customParameters")),
                new TwilioOutboundMediaSender() {
                    @Override
                    public void send(String textFrame) {
                        sendFrame(session, textFrame);
                    }

                    @Override
                    public void close() {
                        closeSession(session);
                    }
                }
        );
    }

    private TwilioMediaFormat mediaFormat(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return TwilioMediaFormat.unknown();
        }
        return new TwilioMediaFormat(
                node.path("encoding").asText(""),
                node.path("sampleRate").asInt(0),
                node.path("channels").asInt(0)
        );
    }

    private Map<String, String> customParameters(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        var parameters = new HashMap<String, String>();
        node.fields().forEachRemaining(entry -> parameters.put(entry.getKey(), entry.getValue().asText("")));
        return parameters;
    }

    private String streamSid(WebSocketSession session, JsonNode node) {
        var streamSid = node.path("streamSid").asText(attribute(session, STREAM_SID_ATTRIBUTE));
        if (!streamSid.isBlank()) {
            session.getAttributes().put(STREAM_SID_ATTRIBUTE, streamSid);
        }
        return streamSid;
    }

    private String callSid(WebSocketSession session) {
        var callSid = attribute(session, CALL_SID_ATTRIBUTE);
        if (!callSid.isBlank()) {
            return callSid;
        }
        var path = session.getUri() == null ? "" : session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String attribute(WebSocketSession session, String name) {
        var value = session.getAttributes().get(name);
        return value == null ? "" : value.toString();
    }

    private void sendFrame(WebSocketSession session, String frame) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(frame));
            }
        } catch (Exception exception) {
            closeSession(session);
        }
    }

    private void closeSession(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (Exception ignored) {
        }
    }
}
