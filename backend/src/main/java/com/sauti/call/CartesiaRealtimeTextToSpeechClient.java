package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CartesiaRealtimeTextToSpeechClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CartesiaRealtimeTextToSpeechClient.class);
    public static final String VOICE_PREFIX = "cartesia:";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String version;
    private final String websocketUrl;
    private final String bytesUrl;
    private final String modelId;
    private final String outputContainer;
    private final String outputEncoding;
    private final int outputSampleRate;
    private final String previewContainer;
    private final String previewEncoding;
    private final int previewSampleRate;
    private final int maxBufferDelayMs;

    public CartesiaRealtimeTextToSpeechClient(
            ObjectMapper objectMapper,
            @Value("${sauti.tts.cartesia.api-key:}") String apiKey,
            @Value("${sauti.tts.cartesia.version:2026-03-01}") String version,
            @Value("${sauti.tts.cartesia.websocket-url:wss://api.cartesia.ai/tts/websocket}") String websocketUrl,
            @Value("${sauti.tts.cartesia.bytes-url:https://api.cartesia.ai/tts/bytes}") String bytesUrl,
            @Value("${sauti.tts.cartesia.model-id:sonic-3.5}") String modelId,
            @Value("${sauti.tts.cartesia.output-container:raw}") String outputContainer,
            @Value("${sauti.tts.cartesia.output-encoding:pcm_s16le}") String outputEncoding,
            @Value("${sauti.tts.cartesia.output-sample-rate:16000}") int outputSampleRate,
            @Value("${sauti.tts.cartesia.preview-container:mp3}") String previewContainer,
            @Value("${sauti.tts.cartesia.preview-encoding:mp3}") String previewEncoding,
            @Value("${sauti.tts.cartesia.preview-sample-rate:44100}") int previewSampleRate,
            @Value("${sauti.tts.cartesia.max-buffer-delay-ms:0}") int maxBufferDelayMs
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.version = version;
        this.websocketUrl = websocketUrl;
        this.bytesUrl = bytesUrl;
        this.modelId = modelId;
        this.outputContainer = outputContainer;
        this.outputEncoding = outputEncoding;
        this.outputSampleRate = outputSampleRate;
        this.previewContainer = previewContainer;
        this.previewEncoding = previewEncoding;
        this.previewSampleRate = previewSampleRate;
        this.maxBufferDelayMs = Math.max(0, Math.min(5000, maxBufferDelayMs));
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public CompletableFuture<RealtimeTtsSession> open(String language, String voiceId, TtsAudioListener listener) {
        if (!isConfigured()) {
            throw new IllegalStateException("Cartesia TTS is not configured");
        }
        var providerVoiceId = providerVoiceId(voiceId);
        LOGGER.info(
                "Opening realtime TTS session provider=cartesia engine=cartesia language={} voiceId={} modelId={}",
                safe(language),
                safe(voiceId),
                safe(modelId)
        );
        var webSocketListener = new CartesiaWebSocketListener(objectMapper, listener);
        return httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .header("Cartesia-Version", version)
                .buildAsync(websocketUri(), webSocketListener)
                .thenApply(webSocket -> new RealtimeTtsSession() {
                    private String contextId = UUID.randomUUID().toString();

                    @Override
                    public synchronized void speak(String text, boolean flush) {
                        sendGeneration(webSocket, providerVoiceId, language, text, flush, contextId);
                        if (flush) contextId = UUID.randomUUID().toString();
                    }

                    @Override
                    public void close() {
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended");
                    }
                });
    }

    public byte[] preview(String voiceId, String language, String text) {
        if (!isConfigured()) {
            throw new IllegalStateException("Cartesia TTS is not configured");
        }
        try {
            var body = objectMapper.createObjectNode()
                    .put("model_id", modelId)
                    .put("transcript", text == null ? "" : text)
                    .put("language", language);
            body.set("voice", objectMapper.createObjectNode()
                    .put("mode", "id")
                    .put("id", providerVoiceId(voiceId)));
            var outputFormat = objectMapper.createObjectNode()
                    .put("container", previewContainer)
                    .put("sample_rate", previewSampleRate);
            if (!"mp3".equalsIgnoreCase(previewContainer)) {
                outputFormat.put("encoding", previewEncoding);
            }
            body.set("output_format", outputFormat);
            body.set("generation_config", objectMapper.createObjectNode()
                    .put("volume", 1)
                    .put("speed", 1));
            var request = HttpRequest.newBuilder(URI.create(bytesUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Cartesia-Version", version)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Cartesia preview failed with status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cartesia preview request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate the Cartesia preview", exception);
        }
    }

    private void sendGeneration(
            WebSocket webSocket,
            String providerVoiceId,
            String language,
            String text,
            boolean flush,
            String contextId
    ) {
        try {
            var payload = objectMapper.createObjectNode()
                    .put("model_id", modelId)
                    .put("transcript", text == null ? "" : text)
                    .put("language", normalizedLanguage(language))
                    .put("context_id", contextId)
                    .put("continue", !flush)
                    .put("max_buffer_delay_ms", maxBufferDelayMs);
            payload.set("voice", objectMapper.createObjectNode()
                    .put("mode", "id")
                    .put("id", providerVoiceId));
            payload.set("output_format", objectMapper.createObjectNode()
                    .put("container", outputContainer)
                    .put("encoding", outputEncoding)
                    .put("sample_rate", outputSampleRate));
            webSocket.sendText(objectMapper.writeValueAsString(payload), true);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to send Cartesia TTS payload", exception);
        }
    }

    private URI websocketUri() {
        var separator = websocketUrl.contains("?") ? "&" : "?";
        return URI.create(websocketUrl + separator + "cartesia_version=" + version);
    }

    private String providerVoiceId(String voiceId) {
        var normalized = voiceId == null ? "" : voiceId.trim();
        if (normalized.startsWith(VOICE_PREFIX)) {
            normalized = normalized.substring(VOICE_PREFIX.length());
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Cartesia voice ID is invalid");
        }
        return normalized;
    }

    private String normalizedLanguage(String language) {
        var normalized = language == null ? "" : language.trim().toLowerCase();
        return switch (normalized) {
            case "fr", "ar" -> normalized;
            default -> "en";
        };
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    static final class CartesiaWebSocketListener implements WebSocket.Listener {
        private final ObjectMapper objectMapper;
        private final TtsAudioListener listener;
        private final StringBuilder textBuffer = new StringBuilder();
        private final ByteArrayOutputStream binaryMessageBuffer = new ByteArrayOutputStream();
        private int pendingPcmByte = -1;

        CartesiaWebSocketListener(ObjectMapper objectMapper, TtsAudioListener listener) {
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
            binaryMessageBuffer.writeBytes(bytes);
            if (last) {
                forwardAlignedPcm(binaryMessageBuffer.toByteArray());
                binaryMessageBuffer.reset();
            }
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
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onError(error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (statusCode != WebSocket.NORMAL_CLOSURE) {
                listener.onError(new IllegalStateException(
                        "Cartesia TTS connection closed with status " + statusCode
                ));
            }
            return null;
        }

        private void handle(String payload) {
            try {
                var node = objectMapper.readTree(payload);
                var type = node.path("type").asText("");
                if ("error".equals(type)) {
                    listener.onError(new IllegalStateException(node.path("message").asText("Cartesia TTS failed")));
                    return;
                }
                if ("chunk".equals(type)) {
                    var data = node.path("data").asText("");
                    if (!data.isBlank()) {
                        forwardAlignedPcm(Base64.getDecoder().decode(data));
                    }
                    return;
                }
                if ("done".equals(type) || node.path("done").asBoolean(false)) {
                    if (pendingPcmByte >= 0 || binaryMessageBuffer.size() > 0) {
                        listener.onError(new IllegalStateException("Cartesia returned an incomplete PCM sample"));
                        pendingPcmByte = -1;
                        binaryMessageBuffer.reset();
                        return;
                    }
                    listener.onComplete();
                }
            } catch (Exception exception) {
                listener.onError(exception);
            }
        }

        private void forwardAlignedPcm(byte[] bytes) {
            if (bytes.length == 0) return;
            var prefix = pendingPcmByte >= 0 ? 1 : 0;
            var combined = new byte[prefix + bytes.length];
            if (prefix == 1) combined[0] = (byte) pendingPcmByte;
            System.arraycopy(bytes, 0, combined, prefix, bytes.length);
            pendingPcmByte = -1;

            var alignedLength = combined.length - (combined.length % 2);
            if (alignedLength < combined.length) {
                pendingPcmByte = Byte.toUnsignedInt(combined[combined.length - 1]);
            }
            if (alignedLength == 0) return;
            if (alignedLength == combined.length) {
                listener.onPcmAudio(combined);
                return;
            }
            var aligned = new byte[alignedLength];
            System.arraycopy(combined, 0, aligned, 0, alignedLength);
            listener.onPcmAudio(aligned);
        }
    }
}
