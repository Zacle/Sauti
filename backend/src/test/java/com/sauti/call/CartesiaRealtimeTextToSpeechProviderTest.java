package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class CartesiaRealtimeTextToSpeechProviderTest {

    @Test
    void rejectsLegacyVoicesWhenNoCartesiaDefaultIsConfigured() {
        var provider = new CartesiaRealtimeTextToSpeechProvider(
                mock(CartesiaRealtimeTextToSpeechClient.class), ""
        );

        assertThatThrownBy(() -> provider.open("fr", "legacy-provider-id", mock(TtsAudioListener.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cartesia voice");
    }

    @Test
    void replacesLegacyVoiceWithConfiguredCartesiaDefault() {
        var client = mock(CartesiaRealtimeTextToSpeechClient.class);
        var listener = mock(TtsAudioListener.class);
        var provider = new CartesiaRealtimeTextToSpeechProvider(client, "cartesia-default");

        provider.open("fr", "legacy-voice", listener);

        verify(client).open("fr", "cartesia:cartesia-default", listener);
    }
}
