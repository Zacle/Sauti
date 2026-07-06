package com.sauti.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.AzureRealtimeTextToSpeechClient;
import com.sauti.voice.VoiceCatalogDtos.VoiceCatalogResponse;
import com.sauti.voice.VoiceCatalogDtos.VoiceOption;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VoiceCatalogService {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper;
    private final AzureRealtimeTextToSpeechClient azureClient;
    private final String streamingProvider;
    private final String elevenLabsApiKey;
    private final String elevenLabsVoicesUrl;
    private final String elevenLabsPreviewUrl;
    private final String elevenLabsModelId;
    private final java.util.Set<String> curatedVoiceIds;
    private final Map<PreviewKey, byte[]> previewCache = new ConcurrentHashMap<>();
    private volatile VoiceCatalogResponse cached;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public VoiceCatalogService(
            ObjectMapper objectMapper,
            AzureRealtimeTextToSpeechClient azureClient,
            @Value("${sauti.tts.streaming-provider:noop}") String streamingProvider,
            @Value("${sauti.tts.elevenlabs.api-key:}") String elevenLabsApiKey,
            @Value("${sauti.tts.elevenlabs.voices-url:https://api.elevenlabs.io/v2/voices?page_size=100}") String elevenLabsVoicesUrl,
            @Value("${sauti.tts.elevenlabs.preview-url:https://api.elevenlabs.io/v1/text-to-speech}") String elevenLabsPreviewUrl,
            @Value("${sauti.tts.elevenlabs.model-id:eleven_flash_v2_5}") String elevenLabsModelId,
            @Value("${sauti.tts.curated-voice-ids:}") String curatedVoiceIds
    ) {
        this.objectMapper = objectMapper;
        this.azureClient = azureClient;
        this.streamingProvider = streamingProvider;
        this.elevenLabsApiKey = elevenLabsApiKey;
        this.elevenLabsVoicesUrl = elevenLabsVoicesUrl;
        this.elevenLabsPreviewUrl = elevenLabsPreviewUrl;
        this.elevenLabsModelId = elevenLabsModelId;
        this.curatedVoiceIds = java.util.Arrays.stream(curatedVoiceIds.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public byte[] preview(String voiceId, String language) {
        var normalizedLanguage = language == null ? "" : language.trim().toLowerCase();
        if (!Set.of("en", "fr", "ar", "sw").contains(normalizedLanguage)) {
            throw new IllegalArgumentException("Preview language must be en, fr, ar, or sw");
        }
        var voice = list().voices().stream()
                .filter(candidate -> candidate.id().equals(voiceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Voice is not available"));
        if (!voice.languages().contains(normalizedLanguage)) {
            throw new IllegalArgumentException("Voice is not verified for " + normalizedLanguage);
        }
        return previewCache.computeIfAbsent(
                new PreviewKey(voiceId, normalizedLanguage),
                key -> generatePreview(key.voiceId(), key.language())
        );
    }

    public byte[] synthesize(String voiceId, String language, String text) {
        var normalizedLanguage = language == null ? "" : language.trim().toLowerCase();
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
        if (azureClient.isConfigured()) {
            providers.add("azure");
            voices.addAll(azureVoices());
        }
        if (!"elevenlabs".equalsIgnoreCase(streamingProvider) || elevenLabsApiKey.isBlank()) {
            return new VoiceCatalogResponse(List.copyOf(providers), List.copyOf(voices));
        }
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
            for (var voice : root.withArray("voices")) {
                if (curatedVoiceIds.isEmpty() || curatedVoiceIds.contains(voice.path("voice_id").asText())) {
                    voices.add(mapElevenLabsVoice(voice));
                }
            }
            providers.add("elevenlabs");
            return new VoiceCatalogResponse(List.copyOf(providers), List.copyOf(voices));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Voice catalog request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the ElevenLabs voice catalog", exception);
        }
    }

    private VoiceOption mapElevenLabsVoice(JsonNode voice) {
        var traits = new LinkedHashMap<String, String>();
        voice.path("labels").fields().forEachRemaining(entry -> traits.put(entry.getKey(), entry.getValue().asText()));
        var languages = new LinkedHashSet<String>();
        var category = voice.path("category").asText("voice");
        var nativeLanguage = traits.getOrDefault("language", "").trim().toLowerCase();
        if (!nativeLanguage.isBlank()) {
            languages.add(nativeLanguage);
        }
        // Model compatibility is not enough for production voice selection:
        // English-origin premade voices retain a foreign accent in French and
        // Arabic. Only expose their native language. Professional/native voices
        // may expose every provider-verified language.
        if (!"premade".equalsIgnoreCase(category)) {
            voice.withArray("verified_languages").forEach(language -> {
                String value = language.path("language").asText("").trim().toLowerCase();
                if (!value.isBlank()) {
                    languages.add(value);
                }
            });
        }
        return new VoiceOption(
                "elevenlabs",
                voice.path("voice_id").asText(),
                voice.path("name").asText("Unnamed voice"),
                textOrNull(voice, "description"),
                category,
                textOrNull(voice, "preview_url"),
                List.copyOf(languages),
                Map.copyOf(traits),
                voice.path("is_owner").asBoolean(false)
        );
    }

    private List<VoiceOption> azureVoices() {
        return List.of(
                azureVoice("sw-KE-ZuriNeural", "Zuri", "Warm Kenyan Swahili", "sw", "Kenyan", "female"),
                azureVoice("sw-KE-RafikiNeural", "Rafiki", "Clear Kenyan Swahili", "sw", "Kenyan", "male"),
                azureVoice("sw-TZ-RehemaNeural", "Rehema", "Warm Tanzanian Swahili", "sw", "Tanzanian", "female"),
                azureVoice("sw-TZ-DaudiNeural", "Daudi", "Clear Tanzanian Swahili", "sw", "Tanzanian", "male"),
                azureVoice("fr-FR-DeniseNeural", "Denise", "Warm French professional", "fr", "France", "female"),
                azureVoice("fr-FR-HenriNeural", "Henri", "Clear French professional", "fr", "France", "male"),
                azureVoice("fr-CA-SylvieNeural", "Sylvie", "Warm Canadian French", "fr", "Canadian", "female"),
                azureVoice("fr-CA-AntoineNeural", "Antoine", "Clear Canadian French", "fr", "Canadian", "male"),
                azureVoice("ar-EG-SalmaNeural", "Salma", "Warm Egyptian Arabic", "ar", "Egyptian", "female"),
                azureVoice("ar-EG-ShakirNeural", "Shakir", "Clear Egyptian Arabic", "ar", "Egyptian", "male"),
                azureVoice("ar-MA-MounaNeural", "Mouna", "Warm Moroccan Arabic", "ar", "Moroccan", "female"),
                azureVoice("ar-MA-JamalNeural", "Jamal", "Clear Moroccan Arabic", "ar", "Moroccan", "male"),
                azureVoice("ar-SA-ZariyahNeural", "Zariyah", "Warm Saudi Arabic", "ar", "Saudi", "female"),
                azureVoice("ar-SA-HamedNeural", "Hamed", "Clear Saudi Arabic", "ar", "Saudi", "male"),
                azureVoice("en-KE-AsiliaNeural", "Asilia", "Warm Kenyan English", "en", "Kenyan", "female"),
                azureVoice("en-KE-ChilembaNeural", "Chilemba", "Clear Kenyan English", "en", "Kenyan", "male"),
                azureVoice("en-NG-EzinneNeural", "Ezinne", "Warm Nigerian English", "en", "Nigerian", "female"),
                azureVoice("en-NG-AbeoNeural", "Abeo", "Clear Nigerian English", "en", "Nigerian", "male")
        );
    }

    private VoiceOption azureVoice(
            String providerVoiceId,
            String name,
            String description,
            String language,
            String accent,
            String gender
    ) {
        return new VoiceOption(
                "azure",
                AzureRealtimeTextToSpeechClient.VOICE_PREFIX + providerVoiceId,
                name,
                description,
                "native",
                null,
                List.of(language),
                Map.of(
                        "language", language,
                        "accent", accent,
                        "gender", gender,
                        "description", "native"
                ),
                false
        );
    }

    private byte[] generatePreview(String voiceId, String language) {
        return generateAudio(voiceId, language, previewText(language));
    }

    private byte[] generateAudio(String voiceId, String language, String text) {
        if (voiceId.startsWith(AzureRealtimeTextToSpeechClient.VOICE_PREFIX)) {
            return azureClient.preview(voiceId, text);
        }
        try {
            var body = objectMapper.createObjectNode()
                    .put("text", text)
                    .put("model_id", elevenLabsModelId)
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
            case "fr" -> "Bonjour, merci de votre appel. Comment puis-je vous aider aujourd'hui ?";
            case "ar" -> "مرحبًا، شكرًا لاتصالك. كيف يمكنني مساعدتك اليوم؟";
            case "sw" -> "Habari, asante kwa kupiga simu. Ninawezaje kukusaidia leo?";
            default -> "Hello, thank you for calling. How can I help you today?";
        };
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private record PreviewKey(String voiceId, String language) {
    }
}
