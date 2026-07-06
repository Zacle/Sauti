package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TwilioMediaFrameFactoryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TwilioMediaFrameFactory factory = new TwilioMediaFrameFactory(objectMapper);

    @Test
    void buildsOutboundMediaFrame() throws Exception {
        var frame = objectMapper.readTree(factory.media("MZ123", new byte[] {1, 2, 3}));

        assertThat(frame.path("event").asText()).isEqualTo("media");
        assertThat(frame.path("streamSid").asText()).isEqualTo("MZ123");
        assertThat(frame.path("media").path("payload").asText()).isEqualTo("AQID");
    }

    @Test
    void buildsMarkFrame() throws Exception {
        var frame = objectMapper.readTree(factory.mark("MZ123", "turn-1-end"));

        assertThat(frame.path("event").asText()).isEqualTo("mark");
        assertThat(frame.path("streamSid").asText()).isEqualTo("MZ123");
        assertThat(frame.path("mark").path("name").asText()).isEqualTo("turn-1-end");
    }

    @Test
    void buildsClearFrame() throws Exception {
        var frame = objectMapper.readTree(factory.clear("MZ123"));

        assertThat(frame.path("event").asText()).isEqualTo("clear");
        assertThat(frame.path("streamSid").asText()).isEqualTo("MZ123");
    }

    @Test
    void rejectsEmptyMediaPayload() {
        assertThatThrownBy(() -> factory.media("MZ123", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("audio payload is required");
    }
}
