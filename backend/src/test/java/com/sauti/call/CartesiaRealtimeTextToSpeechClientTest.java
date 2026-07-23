package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CartesiaRealtimeTextToSpeechClientTest {

    @Test
    void createsShortLivedTtsOnlyBrowserCredentialWithoutExposingTheApiKey() throws Exception {
        var requestBody = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/access-token", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"token\":\"browser-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new CartesiaRealtimeTextToSpeechClient(
                    new ObjectMapper(),
                    "server-api-key",
                    "2026-03-01",
                    "wss://api.cartesia.ai/tts/websocket",
                    "https://api.cartesia.ai/tts/bytes",
                    "http://localhost:" + server.getAddress().getPort() + "/access-token",
                    "sonic-3.5",
                    "raw",
                    "pcm_s16le",
                    16000,
                    "mp3",
                    "mp3",
                    44100,
                    0
            );

            var session = client.createBrowserSession("cartesia:voice-123", 4200);
            var body = new ObjectMapper().readTree(requestBody.get());

            assertThat(authorization.get()).isEqualTo("Bearer server-api-key");
            assertThat(body.path("expires_in").asInt()).isEqualTo(3600);
            assertThat(body.path("grants").path("tts").asBoolean()).isTrue();
            assertThat(body.path("grants").has("stt")).isFalse();
            assertThat(body.path("grants").has("agent")).isFalse();
            assertThat(session.clientToken()).isEqualTo("browser-token");
            assertThat(session.voiceId()).isEqualTo("voice-123");
            assertThat(session.modelId()).isEqualTo("sonic-3.5");
        } finally {
            server.stop(0);
        }
    }

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
