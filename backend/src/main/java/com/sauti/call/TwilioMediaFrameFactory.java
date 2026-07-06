package com.sauti.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TwilioMediaFrameFactory implements TelephonyMediaFrameFactory {
    private final ObjectMapper objectMapper;

    public TwilioMediaFrameFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String media(String streamSid, byte[] mulawAudio) {
        if (streamSid == null || streamSid.isBlank()) {
            throw new IllegalArgumentException("streamSid is required");
        }
        if (mulawAudio == null || mulawAudio.length == 0) {
            throw new IllegalArgumentException("audio payload is required");
        }
        return write(Map.of(
                "event", "media",
                "streamSid", streamSid,
                "media", Map.of("payload", Base64.getEncoder().encodeToString(mulawAudio))
        ));
    }

    @Override
    public String mark(String streamSid, String markName) {
        if (streamSid == null || streamSid.isBlank()) {
            throw new IllegalArgumentException("streamSid is required");
        }
        if (markName == null || markName.isBlank()) {
            throw new IllegalArgumentException("markName is required");
        }
        return write(Map.of(
                "event", "mark",
                "streamSid", streamSid,
                "mark", Map.of("name", markName)
        ));
    }

    @Override
    public String clear(String streamSid) {
        if (streamSid == null || streamSid.isBlank()) {
            throw new IllegalArgumentException("streamSid is required");
        }
        return write(Map.of("event", "clear", "streamSid", streamSid));
    }

    private String write(Map<String, Object> frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to build Twilio media frame", exception);
        }
    }
}
