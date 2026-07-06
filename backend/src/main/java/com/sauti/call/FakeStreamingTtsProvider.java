package com.sauti.call;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class FakeStreamingTtsProvider implements StreamingTtsProvider {
    @Override
    public byte[] synthesize(String language, String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
