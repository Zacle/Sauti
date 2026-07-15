package com.sauti.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.CartesiaRealtimeTextToSpeechClient;
import org.junit.jupiter.api.Test;

class VoiceCatalogServiceTest {
    @Test
    void exposesOpenAiRealtimeVoicesAsASeparateProvider() {
        var service = new VoiceCatalogService(
                new ObjectMapper(),
                mock(CartesiaRealtimeTextToSpeechClient.class),
                "", "2026-03-01", "https://cartesia.invalid/voices",
                "openai-test-key", "https://openai.invalid/speech", "gpt-4o-mini-tts"
        );

        var catalog = service.list();

        assertThat(catalog.enabledProviders()).containsExactly("openai");
        assertThat(catalog.voices()).extracting(VoiceCatalogDtos.VoiceOption::id)
                .contains("openai:marin", "openai:cedar", "openai:coral");
        assertThat(catalog.voices()).allSatisfy(voice -> {
            assertThat(voice.provider()).isEqualTo("openai");
            assertThat(voice.languages()).contains("en", "fr", "ar");
        });
    }
}
