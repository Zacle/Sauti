package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BrowserSpeechToTextService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserSpeechToTextService.class);
    private static final Set<String> OPENAI_FIRST_LANGUAGES = Set.of("fr", "sw", "ar");
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String deepgramApiKey;
    private final String deepgramListenUrl;
    private final String deepgramDefaultModel;
    private final String openAiApiKey;
    private final String openAiTranscriptionUrl;
    private final String openAiModel;

    public BrowserSpeechToTextService(
            ObjectMapper objectMapper,
            @Value("${sauti.stt.deepgram.api-key:}") String apiKey,
            @Value("${sauti.stt.deepgram.prerecorded-url:https://api.deepgram.com/v1/listen}") String listenUrl,
            @Value("${sauti.stt.deepgram.model:nova-3}") String defaultModel,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${sauti.stt.openai.transcription-url:https://api.openai.com/v1/audio/transcriptions}") String openAiTranscriptionUrl,
            @Value("${sauti.stt.openai.model:gpt-4o-transcribe}") String openAiModel
    ) {
        this.objectMapper = objectMapper;
        this.deepgramApiKey = apiKey;
        this.deepgramListenUrl = listenUrl;
        this.deepgramDefaultModel = defaultModel;
        this.openAiApiKey = openAiApiKey;
        this.openAiTranscriptionUrl = openAiTranscriptionUrl;
        this.openAiModel = openAiModel;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String transcribe(Agent agent, byte[] webmAudio) {
        return transcribe(agent, webmAudio, "audio/webm");
    }

    public String transcribe(Agent agent, byte[] audio, String contentType) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("No audio was received");
        }
        if (deepgramApiKey.isBlank() && openAiApiKey.isBlank()) {
            throw new IllegalStateException("DEEPGRAM_API_KEY or OPENAI_API_KEY is required for prerecorded speech recognition");
        }
        var language = normalizedLanguage(agent);
        var openAiFirst = shouldUseOpenAiFirst(agent);
        if (openAiFirst && !openAiApiKey.isBlank()) {
            try {
                return transcribeWithOpenAi(agent, audio, contentType);
            } catch (IllegalArgumentException exception) {
                LOGGER.info("OpenAI speech recognition returned no transcript for language={}; trying Deepgram fallback", language);
            } catch (IllegalStateException exception) {
                LOGGER.warn("OpenAI speech recognition failed for language={}; trying Deepgram fallback", language, exception);
            }
        }
        try {
            return transcribeWithDeepgram(agent, audio, contentType);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            if (!openAiFirst && !openAiApiKey.isBlank()) {
                LOGGER.warn("Deepgram speech recognition failed for language={}; trying OpenAI fallback", language, exception);
                return transcribeWithOpenAi(agent, audio, contentType);
            }
            throw exception;
        }
    }

    private String transcribeWithDeepgram(Agent agent, byte[] audio, String contentType) {
        if (deepgramApiKey.isBlank()) {
            throw new IllegalStateException("DEEPGRAM_API_KEY is required for prerecorded Deepgram speech recognition");
        }
        var language = agent.getDefaultLanguage();
        var multilingual = agent.getSupportedLanguages().size() > 1;
        // detect_language=true only works with nova models, not Whisper.
        // For multilingual agents, use nova-3 so Deepgram can auto-detect.
        // For single-language non-English agents, use Whisper for better accuracy.
        var useWhisper = !multilingual && List.of("sw", "fr", "ar").contains(language);
        var model = useWhisper ? modelFor(language) : deepgramDefaultModel;
        var uri = URI.create(deepgramListenUrl
                + "?model=" + encode(model)
                + (multilingual ? "&detect_language=true" : "&language=" + encode(language))
                + "&smart_format=true"
                + "&punctuate=true");
        try {
            var request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Token " + deepgramApiKey)
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
                throw new IllegalArgumentException("No speech was detected. Speak clearly for a moment, then pause.");
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

    private String transcribeWithOpenAi(Agent agent, byte[] audio, String contentType) {
        if (openAiApiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required for prerecorded OpenAI speech recognition");
        }
        var boundary = "sauti-" + java.util.UUID.randomUUID();
        var requestBody = multipartBody(boundary, agent, audio, normalizeContentType(contentType));
        try {
            var request = HttpRequest.newBuilder(URI.create(openAiTranscriptionUrl))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Speech recognition failed with HTTP " + response.statusCode());
            }
            var transcript = objectMapper.readTree(response.body()).path("text").asText("").trim();
            if (transcript.isBlank()) {
                throw new IllegalArgumentException("No speech was detected. Speak clearly for a moment, then pause.");
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

    private byte[] multipartBody(String boundary, Agent agent, byte[] audio, String contentType) {
        try {
            var output = new ByteArrayOutputStream();
            writeField(output, boundary, "model", openAiModel);
            writeField(output, boundary, "response_format", "json");
            var language = openAiLanguageHint(agent);
            if (language != null) {
                writeField(output, boundary, "language", language);
            }
            // OpenAI infers format from filename extension, NOT Content-Type header.
            var filename = "audio." + extensionFor(contentType);
            writeAscii(output, "--" + boundary + "\r\n");
            writeAscii(output, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
            writeAscii(output, "Content-Type: " + contentType + "\r\n\r\n");
            output.write(audio);
            writeAscii(output, "\r\n--" + boundary + "--\r\n");
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare audio transcription request", exception);
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "audio/mp4", "audio/m4a" -> "mp4";
            case "audio/ogg" -> "ogg";
            case "audio/mpeg" -> "mp3";
            case "audio/wav" -> "wav";
            default -> "webm";
        };
    }

    private void writeField(ByteArrayOutputStream output, String boundary, String name, String value) {
        writeAscii(output, "--" + boundary + "\r\n");
        writeAscii(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeAscii(output, value + "\r\n");
    }

    private void writeAscii(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean shouldUseOpenAiFirst(Agent agent) {
        if (agent == null) return false;
        return OPENAI_FIRST_LANGUAGES.contains(normalizedLanguage(agent))
                || agent.getSupportedLanguages().stream()
                .map(language -> language.toLowerCase(Locale.ROOT))
                .anyMatch(OPENAI_FIRST_LANGUAGES::contains);
    }

    private String openAiLanguageHint(Agent agent) {
        if (agent == null) return null;
        // For single-language agents, always hint the language.
        // For multilingual agents, hint the default language so OpenAI has a starting
        // point — it still auto-detects and overrides if the audio is clearly different.
        var language = normalizedLanguage(agent);
        return language.isBlank() ? null : language;
    }

    private String normalizedLanguage(Agent agent) {
        return agent == null || agent.getDefaultLanguage() == null
                ? ""
                : agent.getDefaultLanguage().toLowerCase(Locale.ROOT);
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

    private String modelFor(String language) {
        return switch (language) {
            case "ar" -> "whisper-medium";
            default -> "whisper-small";
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
