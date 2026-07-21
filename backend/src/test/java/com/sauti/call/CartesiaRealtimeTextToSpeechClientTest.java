package com.sauti.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import org.junit.jupiter.api.Test;

class CartesiaRealtimeTextToSpeechClientTest {

    @Test
    void errorPayloadWithDoneFlagReportsFailureInsteadOfCompletion() {
        var audioListener = mock(TtsAudioListener.class);
        var socketListener = new CartesiaRealtimeTextToSpeechClient.CartesiaWebSocketListener(
                new ObjectMapper(), audioListener
        );
        var webSocket = mock(WebSocket.class);

        socketListener.onText(
                webSocket,
                "{\"type\":\"error\",\"done\":true,\"message\":\"generation failed\"}",
                true
        );

        verify(audioListener).onError(any(IllegalStateException.class));
        verify(audioListener, never()).onComplete();
    }

    @Test
    void abnormalSocketCloseReportsFailure() {
        var audioListener = mock(TtsAudioListener.class);
        var socketListener = new CartesiaRealtimeTextToSpeechClient.CartesiaWebSocketListener(
                new ObjectMapper(), audioListener
        );

        socketListener.onClose(mock(WebSocket.class), 1011, "provider failure");

        verify(audioListener).onError(any(IllegalStateException.class));
    }
}
