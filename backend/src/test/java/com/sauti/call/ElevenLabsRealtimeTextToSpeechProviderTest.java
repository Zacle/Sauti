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
                "eleven_v3",
                "voice",
                0.38,
                0.78,
                0.12,
                0.98,
                true,
                new AzureRealtimeTextToSpeechClient("", "", "-2%", "+0%")
        );

        assertThat(provider.modelId("en")).isEqualTo("eleven_flash_v2_5");
        assertThat(provider.modelId("fr")).isEqualTo("eleven_multilingual_v2");
        assertThat(provider.modelId("ar")).isEqualTo("eleven_multilingual_v2");
        assertThat(provider.modelId("sw")).isEqualTo("eleven_v3");
        assertThat(provider.modelId("unknown")).isEqualTo("eleven_flash_v2_5");
    }
}
