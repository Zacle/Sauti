package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BrowserSpeechToTextService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String listenUrl;
    private final String defaultModel;

    public BrowserSpeechToTextService(
            ObjectMapper objectMapper,
            @Value("${sauti.stt.deepgram.api-key:}") String apiKey,
            @Value("${sauti.stt.deepgram.prerecorded-url:https://api.deepgram.com/v1/listen}") String listenUrl,
            @Value("${sauti.stt.deepgram.model:nova-3}") String defaultModel
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.listenUrl = listenUrl;
        this.defaultModel = defaultModel;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String transcribe(Agent agent, byte[] webmAudio) {
        return transcribe(agent, webmAudio, "audio/webm");
    }

    public String transcribe(Agent agent, byte[] audio, String contentType) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("DEEPGRAM_API_KEY is required for prerecorded speech recognition");
        }
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("No audio was received");
        }
        var language = agent.getDefaultLanguage();
        // Nova-3 has poor accuracy for Swahili and Arabic. Whisper models are
        // multilingual-first and produce far better transcripts for these languages.
        var model = switch (language) {
            case "sw" -> "whisper-small";
            case "ar" -> "whisper-medium";
            default -> defaultModel;
        };
        var uri = URI.create(listenUrl
                + "?model=" + model
                + "&language=" + language
                + "&smart_format=true"
                + "&punctuate=true");
        try {
            var request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Token " + apiKey)
                    .header("Content-Type", normalizeContentType(contentType))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audio))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Speech recognition failed with HTTP " + response.statusCode());
            }
            var root = objectMapper.readTree(response.body());
            var transcript = root.path("results")
                    .path("channels").path(0)
                    .path("alternatives").path(0)
                    .path("transcript").asText("").trim();
            if (transcript.isBlank()) {
                throw new IllegalArgumentException("No speech was detected. Hold the microphone button, speak clearly, then stop.");
            }
            return transcript;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Speech recognition was interrupted", exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to transcribe microphone audio", exception);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        var normalized = contentType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        return java.util.Set.of("audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav")
                .contains(normalized)
                ? normalized
                : "application/octet-stream";
    }
}
