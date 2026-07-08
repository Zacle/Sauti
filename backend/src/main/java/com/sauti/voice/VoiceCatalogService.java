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
import java.util.LinkedHashSet;
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
    private final String elevenLabsApiKey;
    private final String elevenLabsVoicesUrl;
    private final String elevenLabsPreviewUrl;
    private final String elevenLabsModelId;
    private final String elevenLabsEnglishModelId;
    private final String elevenLabsFrenchModelId;
    private final String elevenLabsArabicModelId;
    private final String cartesiaApiKey;
    private final String cartesiaVersion;
    private final String cartesiaVoicesUrl;
    private final Map<PreviewKey, byte[]> previewCache = new ConcurrentHashMap<>();
    private volatile VoiceCatalogResponse cached;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public VoiceCatalogService(
            ObjectMapper objectMapper,
            CartesiaRealtimeTextToSpeechClient cartesiaClient,
            @Value("${sauti.tts.elevenlabs.api-key:}") String elevenLabsApiKey,
            @Value("${sauti.tts.elevenlabs.voices-url:https://api.elevenlabs.io/v2/voices?page_size=100}") String elevenLabsVoicesUrl,
            @Value("${sauti.tts.elevenlabs.preview-url:https://api.elevenlabs.io/v1/text-to-speech}") String elevenLabsPreviewUrl,
            @Value("${sauti.tts.elevenlabs.model-id:eleven_flash_v2_5}") String elevenLabsModelId,
            @Value("${sauti.tts.elevenlabs.model-id-en:}") String elevenLabsEnglishModelId,
            @Value("${sauti.tts.elevenlabs.model-id-fr:}") String elevenLabsFrenchModelId,
            @Value("${sauti.tts.elevenlabs.model-id-ar:}") String elevenLabsArabicModelId,
            @Value("${sauti.tts.cartesia.api-key:}") String cartesiaApiKey,
            @Value("${sauti.tts.cartesia.version:2026-03-01}") String cartesiaVersion,
            @Value("${sauti.tts.cartesia.voices-url:https://api.cartesia.ai/voices?limit=100}") String cartesiaVoicesUrl
    ) {
        this.objectMapper = objectMapper;
        this.cartesiaClient = cartesiaClient;
        this.elevenLabsApiKey = elevenLabsApiKey == null ? "" : elevenLabsApiKey.trim();
        this.elevenLabsVoicesUrl = elevenLabsVoicesUrl;
        this.elevenLabsPreviewUrl = elevenLabsPreviewUrl;
        this.elevenLabsModelId = elevenLabsModelId;
        this.elevenLabsEnglishModelId = blankToDefault(elevenLabsEnglishModelId, elevenLabsModelId);
        this.elevenLabsFrenchModelId = blankToDefault(elevenLabsFrenchModelId, elevenLabsModelId);
        this.elevenLabsArabicModelId = blankToDefault(elevenLabsArabicModelId, elevenLabsModelId);
        this.cartesiaApiKey = cartesiaApiKey == null ? "" : cartesiaApiKey.trim();
        this.cartesiaVersion = cartesiaVersion;
        this.cartesiaVoicesUrl = cartesiaVoicesUrl;
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
        if (!elevenLabsApiKey.isBlank()) {
            voices.addAll(loadElevenLabsVoices());
            providers.add("elevenlabs");
        }
        if (!cartesiaApiKey.isBlank()) {
            voices.addAll(loadCartesiaVoices());
            providers.add("cartesia");
        }
        voices.removeIf(voice -> voice.languages().stream().noneMatch(SUPPORTED_LANGUAGES::contains));
        voices.sort(Comparator
                .comparingInt((VoiceOption voice) -> providerRank(voice.provider()))
                .thenComparingInt(voice -> professionalRank(voice.name() + " " + nullSafe(voice.description())))
                .thenComparing(VoiceOption::name));
        return new VoiceCatalogResponse(List.copyOf(providers), List.copyOf(voices));
    }

    private List<VoiceOption> loadElevenLabsVoices() {
        try {
            var request = HttpRequest.newBuilder(URI.create(elevenLabsVoicesUrl))
                    .header("Accept", "application/json")
                    .header("xi-api-key", elevenLabsApiKey)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("ElevenLabs voice catalog failed with status " + response.statusCode());
            }
            var root = objectMapper.readTree(response.body());
            var voices = new java.util.ArrayList<VoiceOption>();
            for (var voice : root.withArray("voices")) {
                voices.add(mapElevenLabsVoice(voice));
            }
            return voices;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Voice catalog request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the ElevenLabs voice catalog", exception);
        }
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

    private VoiceOption mapElevenLabsVoice(JsonNode voice) {
        var traits = new LinkedHashMap<String, String>();
        voice.path("labels").fields().forEachRemaining(entry -> traits.put(entry.getKey(), entry.getValue().asText()));
        var languages = new LinkedHashSet<String>();
        var nativeLanguage = normalizeLanguageCode(traits.getOrDefault("language", ""));
        if (!nativeLanguage.isBlank()) {
            languages.add(nativeLanguage);
        }
        voice.withArray("verified_languages").forEach(language -> {
            var value = normalizeLanguageCode(language.path("language").asText(""));
            if (!value.isBlank()) {
                languages.add(value);
            }
        });
        languages.removeIf(language -> !SUPPORTED_LANGUAGES.contains(language));
        return new VoiceOption(
                "elevenlabs",
                voice.path("voice_id").asText(),
                voice.path("name").asText("Unnamed voice"),
                textOrNull(voice, "description"),
                voice.path("category").asText("voice"),
                textOrNull(voice, "preview_url"),
                List.copyOf(languages),
                Map.copyOf(traits),
                voice.path("is_owner").asBoolean(false)
        );
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
        if (voiceId.startsWith(CartesiaRealtimeTextToSpeechClient.VOICE_PREFIX)) {
            LOGGER.info(
                    "Generating catalog TTS audio provider=voice-catalog engine=cartesia language={} voiceId={} modelId=sonic-3.5",
                    safe(language),
                    safe(voiceId)
            );
            return cartesiaClient.preview(voiceId, language, text);
        }
        var resolvedModelId = elevenLabsModelId(language);
        LOGGER.info(
                "Generating catalog TTS audio provider=voice-catalog engine=elevenlabs language={} voiceId={} modelId={}",
                safe(language),
                safe(voiceId),
                safe(resolvedModelId)
        );
        try {
            var body = objectMapper.createObjectNode()
                    .put("text", text)
                    .put("model_id", resolvedModelId)
                    .put("language_code", language);
            var request = HttpRequest.newBuilder(URI.create(
                            elevenLabsPreviewUrl + "/" + voiceId + "?output_format=mp3_22050_32"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "audio/mpeg")
                    .header("Content-Type", "application/json")
                    .header("xi-api-key", elevenLabsApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("ElevenLabs preview failed with status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Voice preview request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate the voice preview", exception);
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

    private String elevenLabsModelId(String language) {
        return switch (language == null ? "" : language.trim().toLowerCase()) {
            case "en" -> elevenLabsEnglishModelId;
            case "fr" -> elevenLabsFrenchModelId;
            case "ar" -> elevenLabsArabicModelId;
            default -> elevenLabsModelId;
        };
    }

    private int providerRank(String provider) {
        return "elevenlabs".equals(provider) ? 0 : "cartesia".equals(provider) ? 1 : 2;
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

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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
