package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.tts.streaming-provider", havingValue = "elevenlabs")
public class ElevenLabsRealtimeTextToSpeechProvider implements RealtimeTextToSpeechProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElevenLabsRealtimeTextToSpeechProvider.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String modelId;
    private final String englishModelId;
    private final String frenchModelId;
    private final String arabicModelId;
    private final String defaultVoiceId;
    private final double stability;
    private final double similarityBoost;
    private final double style;
    private final double speed;
    private final boolean speakerBoost;
    private final CartesiaRealtimeTextToSpeechClient cartesiaClient;

    public ElevenLabsRealtimeTextToSpeechProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.tts.elevenlabs.api-key}") String apiKey,
            @Value("${sauti.tts.elevenlabs.base-url:wss://api.elevenlabs.io/v1/text-to-speech}") String baseUrl,
            @Value("${sauti.tts.elevenlabs.model-id:eleven_flash_v2_5}") String modelId,
            @Value("${sauti.tts.elevenlabs.model-id-en:}") String englishModelId,
            @Value("${sauti.tts.elevenlabs.model-id-fr:}") String frenchModelId,
            @Value("${sauti.tts.elevenlabs.model-id-ar:}") String arabicModelId,
            @Value("${sauti.tts.elevenlabs.default-voice-id}") String defaultVoiceId,
            @Value("${sauti.tts.elevenlabs.stability:0.38}") double stability,
            @Value("${sauti.tts.elevenlabs.similarity-boost:0.78}") double similarityBoost,
            @Value("${sauti.tts.elevenlabs.style:0.12}") double style,
            @Value("${sauti.tts.elevenlabs.speed:0.98}") double speed,
            @Value("${sauti.tts.elevenlabs.speaker-boost:true}") boolean speakerBoost,
            CartesiaRealtimeTextToSpeechClient cartesiaClient
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelId = modelId;
        this.englishModelId = blankToDefault(englishModelId, modelId);
        this.frenchModelId = blankToDefault(frenchModelId, modelId);
        this.arabicModelId = blankToDefault(arabicModelId, modelId);
        this.defaultVoiceId = defaultVoiceId;
        this.stability = bounded(stability, 0, 1, "stability");
        this.similarityBoost = bounded(similarityBoost, 0, 1, "similarityBoost");
        this.style = bounded(style, 0, 1, "style");
        this.speed = bounded(speed, 0.7, 1.2, "speed");
        this.speakerBoost = speakerBoost;
        this.cartesiaClient = cartesiaClient;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public CompletableFuture<RealtimeTtsSession> open(String language, String voiceId, TtsAudioListener listener) {
        if (voiceId != null && voiceId.startsWith(CartesiaRealtimeTextToSpeechClient.VOICE_PREFIX)) {
            LOGGER.info(
                    "Opening realtime TTS session provider=elevenlabs engine=cartesia language={} voiceId={} modelId=cartesia",
                    safe(language),
                    safe(voiceId)
            );
            return cartesiaClient.open(language, voiceId, listener);
        }
        var resolvedVoiceId = voiceId == null || voiceId.isBlank() ? defaultVoiceId : voiceId;
        var resolvedModelId = modelId(language);
        LOGGER.info(
                "Opening realtime TTS session provider=elevenlabs engine=elevenlabs language={} voiceId={} resolvedVoiceId={} modelId={}",
                safe(language),
                safe(voiceId),
                safe(resolvedVoiceId),
                safe(resolvedModelId)
        );
        var audioStarted = new AtomicBoolean(false);
        var fallbackStarted = new AtomicBoolean(false);
        var closed = new AtomicBoolean(false);
        var turnComplete = new AtomicBoolean(true);
        var turnVersion = new AtomicLong();
        var spokenText = new StringBuilder();
        var webSocketRef = new WebSocket[1];
        Runnable startFallback = () -> {
            var text = spokenText.toString().trim();
            if (text.isBlank() || audioStarted.get() || closed.get()
                    || !fallbackStarted.compareAndSet(false, true)) return;
            LOGGER.warn("ElevenLabs WebSocket produced no audio; retrying over HTTP voiceId={} modelId={}",
                    safe(resolvedVoiceId), safe(resolvedModelId));
            if (webSocketRef[0] != null) {
                webSocketRef[0].sendClose(WebSocket.NORMAL_CLOSURE, "switching transport");
            }
            streamOverHttp(resolvedVoiceId, resolvedModelId, language, text, listener,
                    audioStarted, closed, turnComplete);
        };
        var webSocketListener = new ElevenLabsWebSocketListener(objectMapper, new TtsAudioListener() {
            @Override
            public void onPcmAudio(byte[] pcm16kAudio) {
                if (fallbackStarted.get() || closed.get()) return;
                audioStarted.set(true);
                listener.onPcmAudio(pcm16kAudio);
            }

            @Override
            public void onComplete() {
                if (!fallbackStarted.get() && audioStarted.get()) {
                    turnComplete.set(true);
                    listener.onComplete();
                }
                else startFallback.run();
            }

            @Override
            public void onError(Throwable error) {
                LOGGER.warn("ElevenLabs WebSocket failed; attempting HTTP fallback", error);
                startFallback.run();
            }
        });
        return httpClient.newWebSocketBuilder()
                .header("xi-api-key", apiKey)
                .buildAsync(uri(resolvedVoiceId, resolvedModelId), webSocketListener)
                .thenApply(webSocket -> {
                    webSocketRef[0] = webSocket;
                    sendInitialization(webSocket);
                    return new RealtimeTtsSession() {
            @Override
            public void speak(String text, boolean flush) {
                if (text != null && !text.isEmpty()) {
                    if (turnComplete.compareAndSet(true, false)) {
                        spokenText.setLength(0);
                        audioStarted.set(false);
                        fallbackStarted.set(false);
                        turnVersion.incrementAndGet();
                    }
                    spokenText.append(text);
                }
                if (flush) {
                    webSocketListener.beginFlush();
                    var scheduledVersion = turnVersion.get();
                    CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS).execute(() -> {
                        if (turnVersion.get() == scheduledVersion && !turnComplete.get()) startFallback.run();
                    });
                }
                sendJson(webSocket, Map.of("text", text == null ? "" : text, "flush", flush));
            }

            @Override
            public void close() {
                closed.set(true);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended");
            }
                    };
                });
    }

    private void streamOverHttp(
            String voiceId, String modelId, String language, String text,
            TtsAudioListener listener, AtomicBoolean audioStarted, AtomicBoolean closed,
            AtomicBoolean turnComplete
    ) {
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "text", text,
                    "model_id", modelId,
                    "language_code", language,
                    "voice_settings", Map.of(
                            "stability", stability,
                            "similarity_boost", similarityBoost,
                            "style", style,
                            "speed", speed,
                            "use_speaker_boost", speakerBoost
                    )
            ));
            var request = HttpRequest.newBuilder(httpUri(voiceId, modelId))
                    .timeout(Duration.ofSeconds(20))
                    .header("xi-api-key", apiKey)
                    .header("Accept", "audio/pcm")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAcceptAsync(response -> consumeHttpAudio(
                            response, listener, audioStarted, closed, turnComplete))
                    .exceptionally(error -> {
                        if (!closed.get()) listener.onError(error);
                        return null;
                    });
        } catch (Exception exception) {
            listener.onError(exception);
        }
    }

    private void consumeHttpAudio(
            HttpResponse<InputStream> response, TtsAudioListener listener,
            AtomicBoolean audioStarted, AtomicBoolean closed, AtomicBoolean turnComplete
    ) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            listener.onError(new IllegalStateException(
                    "ElevenLabs HTTP streaming failed with status " + response.statusCode()));
            return;
        }
        try (var input = response.body()) {
            var buffer = new byte[4096];
            int read;
            while (!closed.get() && (read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                audioStarted.set(true);
                listener.onPcmAudio(java.util.Arrays.copyOf(buffer, read));
            }
            if (!closed.get()) {
                turnComplete.set(true);
                listener.onComplete();
            }
        } catch (Exception exception) {
            if (!closed.get()) listener.onError(exception);
        }
    }

    URI httpUri(String voiceId, String modelId) {
        var httpBase = baseUrl.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
        return URI.create(httpBase + "/" + voiceId
                + "/stream?model_id=" + modelId + "&output_format=pcm_16000");
    }

    private URI uri(String voiceId, String modelId) {
        return URI.create(baseUrl + "/" + voiceId
                + "/stream-input?model_id=" + modelId
                + "&output_format=pcm_16000");
    }

    String modelId(String language) {
        return switch (language == null ? "" : language.trim().toLowerCase()) {
            case "en" -> englishModelId;
            case "fr" -> frenchModelId;
            case "ar" -> arabicModelId;
            default -> modelId;
        };
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void sendJson(WebSocket webSocket, Map<String, Object> payload) {
        try {
            webSocket.sendText(objectMapper.writeValueAsString(payload), true);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to send ElevenLabs TTS payload", exception);
        }
    }

    private void sendInitialization(WebSocket webSocket) {
        var voiceSettings = new LinkedHashMap<String, Object>();
        voiceSettings.put("stability", stability);
        voiceSettings.put("similarity_boost", similarityBoost);
        voiceSettings.put("style", style);
        voiceSettings.put("speed", speed);
        voiceSettings.put("use_speaker_boost", speakerBoost);
        sendJson(webSocket, Map.of(
                "text", " ",
                "voice_settings", voiceSettings,
                "generation_config", Map.of(
                        "chunk_length_schedule", List.of(60, 100, 160, 240)
                )
        ));
    }

    private double bounded(double value, double minimum, double maximum, String setting) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    "ElevenLabs " + setting + " must be between " + minimum + " and " + maximum
            );
        }
        return value;
    }

    static final class ElevenLabsWebSocketListener implements WebSocket.Listener {
        private final ObjectMapper objectMapper;
        private final TtsAudioListener listener;
        private final StringBuilder textBuffer = new StringBuilder();
        private final AtomicBoolean completeFired = new AtomicBoolean(false);

        ElevenLabsWebSocketListener(ObjectMapper objectMapper, TtsAudioListener listener) {
            this.objectMapper = objectMapper;
            this.listener = listener;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            var bytes = new byte[data.remaining()];
            data.get(bytes);
            listener.onPcmAudio(bytes);
            webSocket.request(1);
            return null;
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
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            completeOnce();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onError(error);
        }

        void handle(String payload) {
            try {
                var node = objectMapper.readTree(payload);
                if (node.has("error") || node.has("message") && node.path("audio").isMissingNode()) {
                    var message = node.path("error").path("message").asText(
                            node.path("message").asText("ElevenLabs TTS failed")
                    );
                    listener.onError(new IllegalStateException(message));
                    return;
                }
                var audio = node.path("audio").asText("");
                if (!audio.isBlank()) {
                    listener.onPcmAudio(Base64.getDecoder().decode(audio));
                }
                if (node.path("isFinal").asBoolean(false) || node.path("final").asBoolean(false)) {
                    completeOnce();
                }
            } catch (Exception exception) {
                listener.onError(exception);
            }
        }

        private void completeOnce() {
            if (completeFired.compareAndSet(false, true)) {
                listener.onComplete();
            }
        }

        private void beginFlush() {
            completeFired.set(false);
        }
    }
}
