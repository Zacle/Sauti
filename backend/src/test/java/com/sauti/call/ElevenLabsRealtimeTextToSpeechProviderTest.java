package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ElevenLabsRealtimeTextToSpeechProviderTest {

    @Test
    void selectsLanguageSpecificModelIds() {
        var provider = new ElevenLabsRealtimeTextToSpeechProvider(
                new ObjectMapper(),
                "key",
                "wss://example.test/text-to-speech",
                "eleven_flash_v2_5",
                "",
                "eleven_multilingual_v2",
                "eleven_multilingual_v2",
                "voice",
                0.38,
                0.78,
                0.12,
                0.98,
                true,
                new CartesiaRealtimeTextToSpeechClient(
                        new ObjectMapper(),
                        "",
                        "2026-03-01",
                        "wss://example.test/tts/websocket",
                        "https://example.test/tts/bytes",
                        "sonic-3.5",
                        "raw",
                        "pcm_s16le",
                        16000,
                        "mp3",
                        "pcm_s16le",
                        44100
                )
        );

        assertThat(provider.modelId("en")).isEqualTo("eleven_flash_v2_5");
        assertThat(provider.modelId("fr")).isEqualTo("eleven_multilingual_v2");
        assertThat(provider.modelId("ar")).isEqualTo("eleven_multilingual_v2");
        assertThat(provider.modelId("unknown")).isEqualTo("eleven_flash_v2_5");
    }
}
