package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenAiRealtimeTranscriptionService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String realtimeUrl;
    private final String model;
    private final String delay;
    private final ScheduledExecutorService keepAliveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "openai-realtime-stt-keepalive");
        thread.setDaemon(true);
        return thread;
    });

    public OpenAiRealtimeTranscriptionService(
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${sauti.stt.openai.realtime-url:wss://api.openai.com/v1/realtime}") String realtimeUrl,
            @Value("${sauti.stt.openai.realtime-model:gpt-realtime-whisper}") String model,
            @Value("${sauti.stt.openai.realtime-delay:low}") String delay
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.realtimeUrl = realtimeUrl;
        this.model = model;
        this.delay = delay;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public CompletableFuture<RealtimeSttSession> open(Agent agent, RealtimeTranscriptListener listener) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("OPENAI_API_KEY is required for OpenAI realtime transcription")
            );
        }
        var uri = URI.create(realtimeUrl + "?model=" + encode(model));
        return httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .buildAsync(uri, new OpenAiWebSocketListener(objectMapper, listener, model, delay))
                .thenApply(webSocket -> new OpenAiRealtimeSttSession(webSocket, objectMapper, keepAliveExecutor));
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static final class OpenAiWebSocketListener implements WebSocket.Listener {
        private final ObjectMapper objectMapper;
        private final RealtimeTranscriptListener listener;
        private final String model;
        private final String delay;
        private final StringBuilder textBuffer = new StringBuilder();
        private final StringBuilder partialBuffer = new StringBuilder();

        private OpenAiWebSocketListener(
                ObjectMapper objectMapper,
                RealtimeTranscriptListener listener,
                String model,
                String delay
        ) {
            this.objectMapper = objectMapper;
            this.listener = listener;
            this.model = model;
            this.delay = delay;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            try {
                var event = objectMapper.writeValueAsString(Map.of(
                        "type", "session.update",
                        "session", Map.of(
                                "type", "transcription",
                                "audio", Map.of(
                                        "input", Map.of(
                                                "format", Map.of("type", "audio/pcm", "rate", 24000),
                                                "transcription", Map.of(
                                                        "model", model,
                                                        "delay", delay
                                                )
                                        )
                                )
                        )
                ));
                webSocket.sendText(event, true);
            } catch (Exception exception) {
                listener.onError(exception);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (!last) {
                webSocket.request(1);
                return null;
            }
            var payload = textBuffer.toString();
            textBuffer.setLength(0);
            handle(payload);
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onError(error);
        }

        private void handle(String payload) {
            try {
                var node = objectMapper.readTree(payload);
                var type = node.path("type").asText("");
                if ("conversation.item.input_audio_transcription.delta".equals(type)) {
                    var delta = node.path("delta").asText("");
                    if (!delta.isBlank()) {
                        partialBuffer.append(delta);
                        listener.onPartialTranscript(partialBuffer.toString(), 1.0);
                    }
                    return;
                }
                if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                    var transcript = node.path("transcript").asText("").trim();
                    partialBuffer.setLength(0);
                    if (!transcript.isBlank()) listener.onFinalTranscript(transcript);
                    return;
                }
                if ("error".equals(type)) {
                    listener.onError(new IllegalStateException(node.path("error").path("message").asText("OpenAI realtime transcription failed")));
                }
            } catch (Exception exception) {
                listener.onError(exception);
            }
        }
    }

    private static final class OpenAiRealtimeSttSession implements RealtimeSttSession {
        private final WebSocket webSocket;
        private final ObjectMapper objectMapper;
        private final ScheduledFuture<?> keepAliveTask;

        private OpenAiRealtimeSttSession(
                WebSocket webSocket,
                ObjectMapper objectMapper,
                ScheduledExecutorService keepAliveExecutor
        ) {
            this.webSocket = webSocket;
            this.objectMapper = objectMapper;
            this.keepAliveTask = keepAliveExecutor.scheduleAtFixedRate(
                    () -> webSocket.sendPing(ByteBuffer.wrap(new byte[] {1})),
                    8,
                    8,
                    TimeUnit.SECONDS
            );
        }

        @Override
        public void sendPcmAudio(byte[] pcm16kAudio) {
            if (pcm16kAudio == null || pcm16kAudio.length == 0) return;
            try {
                var pcm24k = pcm16kToPcm24k(pcm16kAudio);
                var event = objectMapper.writeValueAsString(Map.of(
                        "type", "input_audio_buffer.append",
                        "audio", Base64.getEncoder().encodeToString(pcm24k)
                ));
                webSocket.sendText(event, true);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to send OpenAI realtime audio", exception);
            }
        }

        @Override
        public void close() {
            keepAliveTask.cancel(false);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended");
        }

        private byte[] pcm16kToPcm24k(byte[] input) {
            var inputSamples = input.length / 2;
            var outputSamples = inputSamples * 3 / 2;
            var output = new byte[outputSamples * 2];
            for (int index = 0; index < outputSamples; index++) {
                double sourceIndex = index * (2.0 / 3.0);
                int leftIndex = Math.min(inputSamples - 1, (int) Math.floor(sourceIndex));
                int rightIndex = Math.min(inputSamples - 1, leftIndex + 1);
                double fraction = sourceIndex - leftIndex;
                int left = sample(input, leftIndex);
                int right = sample(input, rightIndex);
                int value = (int) Math.round(left + (right - left) * fraction);
                output[index * 2] = (byte) (value & 0xff);
                output[index * 2 + 1] = (byte) ((value >>> 8) & 0xff);
            }
            return output;
        }

        private int sample(byte[] bytes, int sampleIndex) {
            var index = sampleIndex * 2;
            return (short) ((bytes[index] & 0xff) | (bytes[index + 1] << 8));
        }
    }
}
