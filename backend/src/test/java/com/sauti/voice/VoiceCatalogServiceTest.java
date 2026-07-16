package com.sauti.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.CartesiaRealtimeTextToSpeechClient;
import org.junit.jupiter.api.Test;

class VoiceCatalogServiceTest {
    @Test
    void keepsOpenAiRealtimeInternalAndDoesNotExposeItsVoices() {
        var service = new VoiceCatalogService(
                new ObjectMapper(),
                mock(CartesiaRealtimeTextToSpeechClient.class),
                "", "2026-03-01", "https://cartesia.invalid/voices",
                "openai-test-key", "https://openai.invalid/speech", "gpt-4o-mini-tts"
        );

        var catalog = service.list();

        assertThat(catalog.enabledProviders()).isEmpty();
        assertThat(catalog.voices()).isEmpty();
    }

    @Test
    void cachesExactCartesiaGreetingWithoutLoadingTheCatalog() {
        var cartesia = mock(CartesiaRealtimeTextToSpeechClient.class);
        when(cartesia.preview("cartesia:voice-1", "fr", "Bonjour, comment puis-je vous aider ?"))
                .thenReturn(new byte[] {1, 2, 3});
        var service = new VoiceCatalogService(
                new ObjectMapper(), cartesia,
                "", "2026-03-01", "https://cartesia.invalid/voices",
                "", "https://openai.invalid/speech", "gpt-4o-mini-tts"
        );

        assertThat(service.cachedCartesiaGreeting(
                "cartesia:voice-1", "fr", "Bonjour, comment puis-je vous aider ?"
        )).containsExactly(1, 2, 3);
        assertThat(service.cachedCartesiaGreeting(
                "cartesia:voice-1", "fr", "Bonjour, comment puis-je vous aider ?"
        )).containsExactly(1, 2, 3);

        verify(cartesia).preview("cartesia:voice-1", "fr", "Bonjour, comment puis-je vous aider ?");
    }
}
