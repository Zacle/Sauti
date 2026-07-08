package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(name = "sauti.stt.streaming-provider", havingValue = "deepgram")
public class DeepgramRealtimeSpeechToTextProvider implements RealtimeSpeechToTextProvider {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int endpointingMs;
    private final int utteranceEndMs;
    private final ScheduledExecutorService keepAliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "deepgram-keepalive");
        thread.setDaemon(true);
        return thread;
    });

    public DeepgramRealtimeSpeechToTextProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.stt.deepgram.api-key}") String apiKey,
            @Value("${sauti.stt.deepgram.base-url:wss://api.deepgram.com/v1/listen}") String baseUrl,
            @Value("${sauti.stt.deepgram.model:nova-3}") String model,
            @Value("${sauti.stt.deepgram.endpointing-ms:300}") int endpointingMs,
            @Value("${sauti.stt.deepgram.utterance-end-ms:500}") int utteranceEndMs
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.endpointingMs = endpointingMs;
        this.utteranceEndMs = utteranceEndMs;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public CompletableFuture<RealtimeSttSession> open(RealtimeTranscriptListener listener) {
        return open(null, listener);
    }

    @Override
    public CompletableFuture<RealtimeSttSession> open(Agent agent, RealtimeTranscriptListener listener) {
        return httpClient.newWebSocketBuilder()
                .header("Authorization", "Token " + apiKey)
                .buildAsync(uri(agent), new DeepgramWebSocketListener(objectMapper, listener))
                .thenApply(webSocket -> new DeepgramRealtimeSttSession(webSocket, keepAliveExecutor));
    }

    private URI uri(Agent agent) {
        String defaultLanguage = agent == null ? null : agent.getDefaultLanguage();
        String resolvedModel = agent != null
                && "medical".equals(agent.getSttVocabularyDomain())
                && "en".equals(defaultLanguage)
                ? "nova-3-medical"
                : model;
        int resolvedEndpointing = agent == null ? endpointingMs : agent.getSttEndpointingMs();
        var builder = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("model", resolvedModel)
                .queryParam("encoding", "linear16")
                .queryParam("sample_rate", "16000")
                .queryParam("channels", "1")
                .queryParam("interim_results", "true")
                .queryParam("utterance_end_ms", utteranceEndMs)
                .queryParam("vad_events", "true")
                .queryParam("endpointing", resolvedEndpointing);
        var recognitionLanguage = deepgramLanguage(agent);
        if (recognitionLanguage != null && !recognitionLanguage.isBlank()) {
            builder.queryParam("language", recognitionLanguage);
        }
        if (agent != null && agent.getSttBoostedKeywords() != null) {
            for (String keyterm : agent.getSttBoostedKeywords().split(",")) {
                if (!keyterm.isBlank()) builder.queryParam("keyterm", keyterm.trim());
            }
        }
        return builder.build().toUri();
    }

    private String deepgramLanguage(Agent agent) {
        if (agent == null) return "en";
        var supported = agent.getSupportedLanguages();
        if (supported.size() > 1
                && supported.stream().allMatch(language -> java.util.Set.of("en", "fr").contains(language))) {
            return "multi";
        }
        // Deepgram's realtime Nova models do not currently support Swahili.
        // Omitting the parameter preserves the existing stream rather than
        // sending an unsupported language code. Browser tests use Whisper.
        return "sw".equals(agent.getDefaultLanguage()) ? null : agent.getDefaultLanguage();
    }

    private static final class DeepgramWebSocketListener implements WebSocket.Listener {
        private final ObjectMapper objectMapper;
        private final RealtimeTranscriptListener listener;
        private final StringBuilder textBuffer = new StringBuilder();
        private final StringBuilder finalTranscriptBuffer = new StringBuilder();

        private DeepgramWebSocketListener(ObjectMapper objectMapper, RealtimeTranscriptListener listener) {
            this.objectMapper = objectMapper;
            this.listener = listener;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
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
            flushFinalTranscript();
            listener.onError(error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            flushFinalTranscript();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        private void handle(String payload) {
            try {
                var node = objectMapper.readTree(payload);
                if ("UtteranceEnd".equals(node.path("type").asText())) {
                    flushFinalTranscript();
                    return;
                }
                var alternative = node.path("channel").path("alternatives").path(0);
                var transcript = alternative.path("transcript").asText("");
                if (transcript.isBlank()) {
                    return;
                }
                if (node.path("is_final").asBoolean(false) || node.path("speech_final").asBoolean(false)) {
                    if (!finalTranscriptBuffer.isEmpty()) {
                        finalTranscriptBuffer.append(' ');
                    }
                    finalTranscriptBuffer.append(transcript);
                } else {
                    listener.onPartialTranscript(transcript, alternative.path("confidence").asDouble(1.0));
                }
            } catch (Exception exception) {
                listener.onError(exception);
            }
        }

        private void flushFinalTranscript() {
            var finalTranscript = finalTranscriptBuffer.toString().trim();
            finalTranscriptBuffer.setLength(0);
            if (!finalTranscript.isBlank()) {
                listener.onFinalTranscript(finalTranscript);
            }
        }
    }

    private static final class DeepgramRealtimeSttSession implements RealtimeSttSession {
        private final WebSocket webSocket;
        private final ScheduledFuture<?> keepAliveTask;

        private DeepgramRealtimeSttSession(WebSocket webSocket, ScheduledExecutorService keepAliveExecutor) {
            this.webSocket = webSocket;
            this.keepAliveTask = keepAliveExecutor.scheduleAtFixedRate(
                    () -> webSocket.sendText("{\"type\":\"KeepAlive\"}", true),
                    8,
                    8,
                    TimeUnit.SECONDS
            );
        }

        @Override
        public void sendPcmAudio(byte[] pcm16kAudio) {
            if (pcm16kAudio != null && pcm16kAudio.length > 0) {
                webSocket.sendBinary(ByteBuffer.wrap(pcm16kAudio), true);
            }
        }

        @Override
        public void close() {
            keepAliveTask.cancel(false);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended");
        }
    }
}
