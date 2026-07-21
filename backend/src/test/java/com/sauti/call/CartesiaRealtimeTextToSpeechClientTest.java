package com.sauti.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class CartesiaRealtimeTextToSpeechClientTest {

    @Test
    void reassemblesFragmentedBinaryMessagesBeforeForwardingPcm() {
        var audioListener = mock(TtsAudioListener.class);
        var socketListener = new CartesiaRealtimeTextToSpeechClient.CartesiaWebSocketListener(
                new ObjectMapper(), audioListener
        );
        var webSocket = mock(WebSocket.class);

        socketListener.onBinary(webSocket, ByteBuffer.wrap(new byte[] {1, 2, 3}), false);
        socketListener.onBinary(webSocket, ByteBuffer.wrap(new byte[] {4, 5, 6}), true);

        verify(audioListener).onPcmAudio(aryEq(new byte[] {1, 2, 3, 4, 5, 6}));
    }

    @Test
    void carriesAnOddPcmByteAcrossProviderMessages() {
        var audioListener = mock(TtsAudioListener.class);
        var socketListener = new CartesiaRealtimeTextToSpeechClient.CartesiaWebSocketListener(
                new ObjectMapper(), audioListener
        );
        var webSocket = mock(WebSocket.class);

        socketListener.onBinary(webSocket, ByteBuffer.wrap(new byte[] {1, 2, 3}), true);
        socketListener.onBinary(webSocket, ByteBuffer.wrap(new byte[] {4, 5, 6}), true);

        verify(audioListener).onPcmAudio(aryEq(new byte[] {1, 2}));
        verify(audioListener).onPcmAudio(aryEq(new byte[] {3, 4, 5, 6}));
    }

    @Test
    void alignsBase64PcmChunksAcrossTextEvents() {
        var audioListener = mock(TtsAudioListener.class);
        var socketListener = new CartesiaRealtimeTextToSpeechClient.CartesiaWebSocketListener(
                new ObjectMapper(), audioListener
        );
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket, "{\"type\":\"chunk\",\"data\":\"AQID\"}", true);
        socketListener.onText(webSocket, "{\"type\":\"chunk\",\"data\":\"BAUG\"}", true);

        verify(audioListener).onPcmAudio(aryEq(new byte[] {1, 2}));
        verify(audioListener).onPcmAudio(aryEq(new byte[] {3, 4, 5, 6}));
    }

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
