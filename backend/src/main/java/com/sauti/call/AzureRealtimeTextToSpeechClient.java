package com.sauti.call;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AzureRealtimeTextToSpeechClient {
    public static final String VOICE_PREFIX = "azure:";
    private static final String PCM_16K_FORMAT = "raw-16khz-16bit-mono-pcm";
    private static final String MP3_PREVIEW_FORMAT = "audio-24khz-48kbitrate-mono-mp3";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String region;
    private final String speakingRate;
    private final String pitch;

    public AzureRealtimeTextToSpeechClient(
            @Value("${sauti.tts.azure.api-key:}") String apiKey,
            @Value("${sauti.tts.azure.region:}") String region,
            @Value("${sauti.tts.azure.speaking-rate:-2%}") String speakingRate,
            @Value("${sauti.tts.azure.pitch:+0%}") String pitch
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.region = region == null ? "" : region.trim();
        this.speakingRate = validatedProsody(speakingRate, "-2%");
        this.pitch = validatedProsody(pitch, "+0%");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !region.isBlank();
    }

    public CompletableFuture<RealtimeTtsSession> open(String voiceId, TtsAudioListener listener) {
        requireConfigured();
        var providerVoiceId = providerVoiceId(voiceId);
        var closed = new AtomicBoolean(false);
        return CompletableFuture.completedFuture(new RealtimeTtsSession() {
            private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

            @Override
            public synchronized void speak(String text, boolean flush) {
                if (closed.get() || text == null || text.isBlank()) {
                    if (flush && !closed.get()) listener.onComplete();
                    return;
                }
                var operation = tail.handle((ignored, previousError) -> null)
                        .thenCompose(ignored -> synthesize(providerVoiceId, text, PCM_16K_FORMAT))
                        .thenAccept(listener::onPcmAudio);
                tail = operation;
                operation.whenComplete((ignored, error) -> {
                    if (error != null) {
                        listener.onError(unwrap(error));
                    } else if (flush && !closed.get()) {
                        listener.onComplete();
                    }
                });
            }

            @Override
            public void close() {
                closed.set(true);
            }
        });
    }

    public byte[] preview(String voiceId, String text) {
        requireConfigured();
        return synthesize(providerVoiceId(voiceId), text, MP3_PREVIEW_FORMAT).join();
    }

    private CompletableFuture<byte[]> synthesize(String providerVoiceId, String text, String outputFormat) {
        var request = HttpRequest.newBuilder(endpoint())
                .timeout(Duration.ofSeconds(20))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Content-Type", "application/ssml+xml")
                .header("X-Microsoft-OutputFormat", outputFormat)
                .header("User-Agent", "Sauti")
                .POST(HttpRequest.BodyPublishers.ofString(ssml(providerVoiceId, text)))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Azure Speech synthesis failed with status " + response.statusCode());
                    }
                    return response.body();
                });
    }

    private URI endpoint() {
        return URI.create("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1");
    }

    String ssml(String voiceId, String text) {
        var locale = voiceId.length() >= 5 ? voiceId.substring(0, 5) : "en-US";
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\""
                + " xml:lang=\"" + escapeXml(locale) + "\">"
                + "<voice name=\"" + escapeXml(voiceId) + "\">"
                + "<prosody rate=\"" + speakingRate + "\" pitch=\"" + pitch + "\">"
                + escapeXml(text.trim())
                + "</prosody>"
                + "</voice></speak>";
    }

    private String validatedProsody(String value, String fallback) {
        var resolved = value == null ? "" : value.trim();
        return resolved.matches("[+-]?\\d{1,2}%") ? resolved : fallback;
    }

    private String providerVoiceId(String voiceId) {
        if (voiceId == null || !voiceId.startsWith(VOICE_PREFIX)) {
            throw new IllegalArgumentException("Azure voice ID is invalid");
        }
        var resolved = voiceId.substring(VOICE_PREFIX.length()).trim();
        if (!resolved.matches("[A-Za-z0-9:-]+")) {
            throw new IllegalArgumentException("Azure voice ID is invalid");
        }
        return resolved;
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Azure Speech is not configured");
        }
    }
}
