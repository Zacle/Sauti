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
    private static final int INPUT_SAMPLE_RATE = 16_000;
    private static final int SPEECH_RMS_THRESHOLD = 450;
    private static final long MIN_COMMIT_AUDIO_MS = 400;
    private static final long SILENCE_COMMIT_MS = 700;
    private static final long MAX_COMMIT_AUDIO_MS = 15_000;

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
                    var error = node.path("error");
                    var code = error.path("code").asText("");
                    var message = error.path("message").asText("OpenAI realtime transcription failed");
                    listener.onError(new IllegalStateException(code.isBlank() ? message : code + ": " + message));
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
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);
        private long pendingAudioMillis;
        private long trailingSilenceMillis;
        private boolean pendingSpeech;

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
                var durationMillis = audioDurationMillis(pcm16kAudio);
                var speech = rms(pcm16kAudio) >= SPEECH_RMS_THRESHOLD;
                if (!speech && !pendingSpeech) return;

                appendAudio(pcm16kToPcm24k(pcm16kAudio));
                pendingAudioMillis += durationMillis;
                if (speech) {
                    pendingSpeech = true;
                    trailingSilenceMillis = 0;
                } else {
                    trailingSilenceMillis += durationMillis;
                }
                if (pendingSpeech
                        && pendingAudioMillis >= MIN_COMMIT_AUDIO_MS
                        && (trailingSilenceMillis >= SILENCE_COMMIT_MS || pendingAudioMillis >= MAX_COMMIT_AUDIO_MS)) {
                    commitAudio();
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to send OpenAI realtime audio", exception);
            }
        }

        @Override
        public void close() {
            keepAliveTask.cancel(false);
            if (pendingSpeech) {
                try {
                    commitAudio();
                } catch (Exception ignored) {
                }
            }
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended");
        }

        private void appendAudio(byte[] pcm24k) throws Exception {
            var event = objectMapper.writeValueAsString(Map.of(
                    "type", "input_audio_buffer.append",
                    "audio", Base64.getEncoder().encodeToString(pcm24k)
            ));
            sendEvent(event);
        }

        private void commitAudio() throws Exception {
            var event = objectMapper.writeValueAsString(Map.of("type", "input_audio_buffer.commit"));
            sendEvent(event);
            pendingAudioMillis = 0;
            trailingSilenceMillis = 0;
            pendingSpeech = false;
        }

        private synchronized void sendEvent(String event) {
            sendChain = sendChain.handle((ignored, error) -> null)
                    .thenCompose(ignored -> webSocket.sendText(event, true))
                    .thenApply(ignored -> null);
        }

        private long audioDurationMillis(byte[] input) {
            return (input.length / 2L) * 1000L / INPUT_SAMPLE_RATE;
        }

        private int rms(byte[] input) {
            var samples = input.length / 2;
            if (samples == 0) return 0;
            double sum = 0;
            for (var index = 0; index < samples; index++) {
                var value = sample(input, index);
                sum += value * (double) value;
            }
            return (int) Math.sqrt(sum / samples);
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
