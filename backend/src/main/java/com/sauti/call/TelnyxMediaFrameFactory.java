package com.sauti.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TelnyxMediaFrameFactory implements TelephonyMediaFrameFactory {
    private final ObjectMapper objectMapper;

    public TelnyxMediaFrameFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String media(String streamId, byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("audio payload is required");
        }
        return write(Map.of(
                "event", "media",
                "media", Map.of("payload", Base64.getEncoder().encodeToString(audio))
        ));
    }

    @Override
    public String mark(String streamId, String markName) {
        if (markName == null || markName.isBlank()) {
            throw new IllegalArgumentException("markName is required");
        }
        return write(Map.of("event", "mark", "mark", Map.of("name", markName)));
    }

    @Override
    public String clear(String streamId) {
        return write(Map.of("event", "clear"));
    }

    private String write(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to build Telnyx media frame", exception);
        }
    }
}
