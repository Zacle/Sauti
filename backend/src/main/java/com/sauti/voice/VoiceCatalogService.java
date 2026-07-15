package com.sauti.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.CartesiaRealtimeTextToSpeechClient;
import com.sauti.voice.VoiceCatalogDtos.VoiceCatalogResponse;
import com.sauti.voice.VoiceCatalogDtos.VoiceOption;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VoiceCatalogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceCatalogService.class);
    private static final int MAX_PREVIEW_TEXT_LENGTH = 240;
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "fr", "ar");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper;
    private final CartesiaRealtimeTextToSpeechClient cartesiaClient;
    private final String cartesiaApiKey;
    private final String cartesiaVersion;
    private final String cartesiaVoicesUrl;
    private final String openAiApiKey;
    private final String openAiSpeechUrl;
    private final String openAiSpeechModel;
    private final Map<PreviewKey, byte[]> previewCache = new ConcurrentHashMap<>();
    private volatile VoiceCatalogResponse cached;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public VoiceCatalogService(
            ObjectMapper objectMapper,
            CartesiaRealtimeTextToSpeechClient cartesiaClient,
            @Value("${sauti.tts.cartesia.api-key:}") String cartesiaApiKey,
            @Value("${sauti.tts.cartesia.version:2026-03-01}") String cartesiaVersion,
            @Value("${sauti.tts.cartesia.voices-url:https://api.cartesia.ai/voices?limit=100}") String cartesiaVoicesUrl,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey,
            @Value("${sauti.realtime.openai.speech-url:https://api.openai.com/v1/audio/speech}") String openAiSpeechUrl,
            @Value("${sauti.realtime.openai.preview-model:gpt-4o-mini-tts}") String openAiSpeechModel
    ) {
        this.objectMapper = objectMapper;
        this.cartesiaClient = cartesiaClient;
        this.cartesiaApiKey = cartesiaApiKey == null ? "" : cartesiaApiKey.trim();
        this.cartesiaVersion = cartesiaVersion;
        this.cartesiaVoicesUrl = cartesiaVoicesUrl;
        this.openAiApiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
        this.openAiSpeechUrl = openAiSpeechUrl;
        this.openAiSpeechModel = openAiSpeechModel;
    }

    public byte[] preview(String voiceId, String language) {
        return preview(voiceId, language, null);
    }

    public byte[] preview(String voiceId, String language, String text) {
        var normalizedLanguage = normalizedSupportedLanguage(language);
        var voice = list().voices().stream()
                .filter(candidate -> candidate.id().equals(voiceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Voice is not available"));
        if (!voice.languages().contains(normalizedLanguage)) {
            throw new IllegalArgumentException("Voice is not verified for " + normalizedLanguage);
        }
        var previewText = normalizePreviewText(text, normalizedLanguage);
        return previewCache.computeIfAbsent(
                new PreviewKey(voiceId, normalizedLanguage, previewText),
                key -> generateAudio(key.voiceId(), key.language(), key.text())
        );
    }

    public byte[] synthesize(String voiceId, String language, String text) {
        var normalizedLanguage = normalizedSupportedLanguage(language);
        var voice = list().voices().stream()
                .filter(candidate -> candidate.id().equals(voiceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Voice is not available"));
        if (!voice.languages().contains(normalizedLanguage)) {
            throw new IllegalArgumentException("Voice is not available for " + normalizedLanguage);
        }
        return generateAudio(voiceId, normalizedLanguage, text);
    }

    public VoiceCatalogResponse list() {
        var snapshot = cached;
        if (snapshot != null && Instant.now().isBefore(cacheExpiresAt)) {
            return snapshot;
        }
        synchronized (this) {
            if (cached != null && Instant.now().isBefore(cacheExpiresAt)) {
                return cached;
            }
            cached = load();
            cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(10));
            return cached;
        }
    }

    private VoiceCatalogResponse load() {
        var providers = new java.util.ArrayList<String>();
        var voices = new java.util.ArrayList<VoiceOption>();
        if (!openAiApiKey.isBlank()) {
            voices.addAll(openAiVoices());
            providers.add("openai");
        }
        if (!cartesiaApiKey.isBlank()) {
            voices.addAll(loadCartesiaVoices());
            providers.add("cartesia");
        }
        voices.removeIf(voice -> voice.languages().stream().noneMatch(SUPPORTED_LANGUAGES::contains));
        voices.sort(Comparator
                .comparingInt((VoiceOption voice) -> professionalRank(voice.name() + " " + nullSafe(voice.description())))
                .thenComparing(VoiceOption::name));
        return new VoiceCatalogResponse(List.copyOf(providers), List.copyOf(voices));
    }

    private List<VoiceOption> openAiVoices() {
        var descriptions = Map.of(
                "marin", "Natural, expressive voice recommended for high-quality conversations",
                "cedar", "Warm, grounded voice recommended for high-quality conversations",
                "coral", "Clear, friendly and conversational",
                "sage", "Calm, measured and reassuring",
                "verse", "Confident, versatile and engaging",
                "alloy", "Balanced, neutral and professional",
                "ash", "Direct, composed and articulate",
                "ballad", "Warm, expressive and story-driven",
                "echo", "Smooth, steady and conversational",
                "shimmer", "Bright, upbeat and approachable"
        );
        return List.of("marin", "cedar", "coral", "sage", "verse", "alloy", "ash", "ballad", "echo", "shimmer")
                .stream()
                .map(voice -> new VoiceOption(
                        "openai",
                        "openai:" + voice,
                        Character.toUpperCase(voice.charAt(0)) + voice.substring(1),
                        descriptions.get(voice),
                        Set.of("marin", "cedar").contains(voice) ? "professional" : "realtime",
                        null,
                        List.of("en", "fr", "ar"),
                        Map.of(
                                "engine", "OpenAI Realtime",
                                "latency", "lowest",
                                "description", Set.of("marin", "cedar").contains(voice) ? "recommended" : "realtime"
                        ),
                        false
                ))
                .toList();
    }

    private List<VoiceOption> loadCartesiaVoices() {
        try {
            var voices = new java.util.ArrayList<VoiceOption>();
            for (String language : List.of("en", "fr", "ar")) {
                voices.addAll(loadCartesiaVoicesForLanguage(language));
            }
            voices.sort(Comparator
                    .comparingInt((VoiceOption voice) -> professionalRank(voice.name() + " " + nullSafe(voice.description())))
                    .thenComparing(VoiceOption::name));
            return voices.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            VoiceOption::id,
                            voice -> voice,
                            (first, ignored) -> first,
                            LinkedHashMap::new
                    ))
                    .values()
                    .stream()
                    .toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cartesia voice catalog request was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unable to load the Cartesia voice catalog", exception);
        }
    }

    private List<VoiceOption> loadCartesiaVoicesForLanguage(String language) throws Exception {
        var voices = new java.util.ArrayList<VoiceOption>();
        String nextPage = "";
        var languageUrl = appendQuery(cartesiaVoicesUrl, "language=" + URLEncoder.encode(language, StandardCharsets.UTF_8));
        for (int page = 0; page < 3; page++) {
            var url = nextPage.isBlank()
                    ? languageUrl
                    : appendQuery(languageUrl, "starting_after=" + URLEncoder.encode(nextPage, StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + cartesiaApiKey)
                    .header("Cartesia-Version", cartesiaVersion)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Cartesia voice catalog failed with status " + response.statusCode());
            }
            var root = objectMapper.readTree(response.body());
            for (var voice : root.withArray("data")) {
                var mapped = mapCartesiaVoice(voice);
                if (mapped != null) {
                    voices.add(mapped);
                }
            }
            if (!root.path("has_more").asBoolean(false)) {
                break;
            }
            nextPage = root.path("next_page").asText("");
            if (nextPage.isBlank()) {
                break;
            }
        }
        return voices;
    }

    private VoiceOption mapCartesiaVoice(JsonNode voice) {
        var language = normalizeLanguageCode(voice.path("language").asText(""));
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            return null;
        }
        var name = voice.path("name").asText("Unnamed voice");
        var description = textOrNull(voice, "description");
        var country = voice.path("country").asText("");
        var gender = voice.path("gender").asText("");
        var traits = new LinkedHashMap<String, String>();
        traits.put("language", language);
        if (!country.isBlank()) traits.put("accent", country);
        if (!gender.isBlank()) traits.put("gender", gender);
        traits.put("description", "professional");
        return new VoiceOption(
                "cartesia",
                CartesiaRealtimeTextToSpeechClient.VOICE_PREFIX + voice.path("id").asText(),
                name,
                description,
                professionalRank(name + " " + nullSafe(description)) <= 1 ? "professional" : "voice",
                null,
                List.of(language),
                Map.copyOf(traits),
                voice.path("is_owner").asBoolean(false)
        );
    }

    private byte[] generateAudio(String voiceId, String language, String text) {
        if (voiceId != null && voiceId.startsWith("openai:")) {
            return generateOpenAiAudio(voiceId.substring("openai:".length()), language, text);
        }
        LOGGER.info(
                "Generating catalog TTS audio provider=cartesia language={} voiceId={} modelId=sonic-3.5",
                safe(language),
                safe(voiceId)
        );
        return cartesiaClient.preview(voiceId, language, text);
    }

    private byte[] generateOpenAiAudio(String voice, String language, String text) {
        if (openAiApiKey.isBlank()) {
            throw new IllegalStateException("OpenAI voice previews are not configured");
        }
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "model", openAiSpeechModel,
                    "voice", voice,
                    "input", text,
                    "response_format", "mp3",
                    "instructions", "Speak naturally in " + language + " as a concise, warm business voice agent."
            ));
            var request = HttpRequest.newBuilder(URI.create(openAiSpeechUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI voice preview failed with status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI voice preview was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to generate OpenAI voice preview", exception);
        }
    }

    private String previewText(String language) {
        return switch (language) {
            case "fr" -> "Bonjour, vous etes bien avec Sauti. Comment puis-je vous aider aujourd'hui ?";
            case "ar" -> "Marhaban, this is a short Sauti voice preview in Arabic.";
            default -> "Hi, this is Sauti. How can I help today?";
        };
    }

    private String normalizedSupportedLanguage(String language) {
        var normalized = normalizeLanguageCode(language);
        if (!SUPPORTED_LANGUAGES.contains(normalized)) {
            throw new IllegalArgumentException("Preview language must be en, fr, or ar");
        }
        return normalized;
    }

    private String normalizeLanguageCode(String value) {
        var normalized = value == null ? "" : value.trim().toLowerCase();
        return switch (normalized) {
            case "eng", "en-us", "en-gb", "en-au", "en-ca" -> "en";
            case "fra", "fre", "fr-fr", "fr-ca" -> "fr";
            case "ara", "ar-eg", "ar-sa", "ar-ma", "ar-ae" -> "ar";
            default -> normalized;
        };
    }

    private String normalizePreviewText(String text, String language) {
        if (text == null || text.isBlank()) {
            return previewText(language);
        }
        var normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() > MAX_PREVIEW_TEXT_LENGTH) {
            return normalized.substring(0, MAX_PREVIEW_TEXT_LENGTH).trim();
        }
        return normalized;
    }

    private int professionalRank(String value) {
        var text = value == null ? "" : value.toLowerCase();
        if (text.contains("customer care") || text.contains("support") || text.contains("assistant")
                || text.contains("reception") || text.contains("guide")) {
            return 0;
        }
        if (text.contains("professional") || text.contains("friendly") || text.contains("warm")
                || text.contains("approachable") || text.contains("calm")) {
            return 1;
        }
        return 2;
    }

    private String appendQuery(String url, String query) {
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private record PreviewKey(String voiceId, String language, String text) {
    }
}
