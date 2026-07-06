package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TelnyxMediaFrameFactoryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TelnyxMediaFrameFactory factory = new TelnyxMediaFrameFactory(objectMapper);

    @Test
    void buildsBidirectionalMediaFrameWithoutTwilioFields() throws Exception {
        var frame = objectMapper.readTree(factory.media("ignored", new byte[] {1, 2, 3}));

        assertThat(frame.path("event").asText()).isEqualTo("media");
        assertThat(frame.path("media").path("payload").asText()).isEqualTo("AQID");
        assertThat(frame.has("streamSid")).isFalse();
        assertThat(frame.has("stream_id")).isFalse();
    }

    @Test
    void buildsMarkAndClearFrames() throws Exception {
        var mark = objectMapper.readTree(factory.mark("ignored", "turn-1-end"));
        var clear = objectMapper.readTree(factory.clear("ignored"));

        assertThat(mark.path("mark").path("name").asText()).isEqualTo("turn-1-end");
        assertThat(clear.path("event").asText()).isEqualTo("clear");
    }

    @Test
    void rejectsEmptyMediaPayload() {
        assertThatThrownBy(() -> factory.media("ignored", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("audio payload is required");
    }
}
