package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
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
        var webSocketListener = new ElevenLabsWebSocketListener(objectMapper, listener);
        return httpClient.newWebSocketBuilder()
                .header("xi-api-key", apiKey)
                .buildAsync(uri(resolvedVoiceId, resolvedModelId), webSocketListener)
                .thenApply(webSocket -> {
                    sendInitialization(webSocket);
                    return new RealtimeTtsSession() {
            @Override
            public void speak(String text, boolean flush) {
                if (flush) {
                    webSocketListener.beginFlush();
                }
                sendJson(webSocket, Map.of("text", text == null ? "" : text, "flush", flush));
            }

            @Override
            public void close() {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended");
            }
                    };
                });
    }

    private URI uri(String voiceId, String modelId) {
        return URI.create(baseUrl + "/" + voiceId
                + "/stream-input?model_id=" + modelId
                + "&output_format=pcm_16000&auto_mode=true");
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

    private static final class ElevenLabsWebSocketListener implements WebSocket.Listener {
        private final ObjectMapper objectMapper;
        private final TtsAudioListener listener;
        private final StringBuilder textBuffer = new StringBuilder();
        private final AtomicBoolean completeFired = new AtomicBoolean(false);

        private ElevenLabsWebSocketListener(ObjectMapper objectMapper, TtsAudioListener listener) {
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

        private void handle(String payload) {
            try {
                var node = objectMapper.readTree(payload);
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
