package com.sauti.call;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class FakeStreamingSttProvider implements StreamingSttProvider {
    @Override
    public String transcribe(byte[] audioPayload) {
        var decoded = new String(audioPayload, StandardCharsets.UTF_8).trim();
        return decoded.isBlank() ? "hello" : decoded;
    }
}
