package com.sauti.voice;

import com.sauti.voice.VoiceCatalogDtos.VoiceCatalogResponse;
import com.sauti.voice.VoiceCatalogDtos.VoiceOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VoiceCatalogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VoiceCatalogService.class);
    private static final int MAX_PREVIEW_TEXT_LENGTH = 240;
    private static final Map<String, String> LANGUAGE_NAME_CODES = Map.ofEntries(
            Map.entry("arabic", "ar"),
            Map.entry("chinese", "zh"),
            Map.entry("dutch", "nl"),
            Map.entry("english", "en"),
            Map.entry("french", "fr"),
            Map.entry("german", "de"),
            Map.entry("greek", "el"),
            Map.entry("hebrew", "he"),
            Map.entry("hindi", "hi"),
            Map.entry("indonesian", "id"),
            Map.entry("italian", "it"),
            Map.entry("japanese", "ja"),
            Map.entry("korean", "ko"),
            Map.entry("polish", "pl"),
            Map.entry("portuguese", "pt"),
            Map.entry("romanian", "ro"),
            Map.entry("russian", "ru"),
            Map.entry("spanish", "es"),
            Map.entry("swahili", "sw"),
            Map.entry("swedish", "sv"),
            Map.entry("tamil", "ta"),
            Map.entry("telugu", "te"),
            Map.entry("thai", "th"),
            Map.entry("turkish", "tr"),
            Map.entry("ukrainian", "uk"),
            Map.entry("vietnamese", "vi")
    );

    private final TelnyxVoiceCatalogClient telnyxClient;
    private final Map<PreviewKey, byte[]> previewCache = new ConcurrentHashMap<>();
    private volatile VoiceCatalogResponse cached;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public VoiceCatalogService(TelnyxVoiceCatalogClient telnyxClient) {
        this.telnyxClient = telnyxClient;
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
            if (!telnyxClient.isConfigured()) {
                cached = new VoiceCatalogResponse(List.of(), List.of());
                cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(1));
                return cached;
            }
            try {
                cached = loadTelnyxVoices();
                cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(10));
                return cached;
            } catch (RuntimeException exception) {
                if (cached != null) {
                    LOGGER.warn("Telnyx voice catalog refresh failed; serving the previous catalog", exception);
                    cacheExpiresAt = Instant.now().plus(Duration.ofMinutes(1));
                    return cached;
                }
                throw exception;
            }
        }
    }

    public byte[] preview(String voiceId, String language) {
        return preview(voiceId, language, null);
    }

    public byte[] preview(String voiceId, String language, String text) {
        var voice = requireVoice(voiceId);
        var normalizedLanguage = normalizeLanguageCode(language);
        if (!voice.languages().contains(normalizedLanguage)) {
            throw new IllegalArgumentException("Voice is not available for " + normalizedLanguage);
        }
        var previewText = normalizePreviewText(text, normalizedLanguage);
        return previewCache.computeIfAbsent(
                new PreviewKey(voice.id(), normalizedLanguage, previewText),
                key -> telnyxClient.synthesize(key.voiceId(), key.language(), key.text())
        );
    }

    public byte[] synthesize(String voiceId, String language, String text) {
        var voice = requireVoice(voiceId);
        var normalizedLanguage = normalizeLanguageCode(language);
        if (!voice.languages().contains(normalizedLanguage)) {
            throw new IllegalArgumentException("Voice is not available for " + normalizedLanguage);
        }
        return telnyxClient.synthesize(voice.id(), normalizedLanguage, text);
    }

    public byte[] cachedGreeting(String voiceId, String language, String text) {
        return preview(voiceId, language, text);
    }

    public boolean isAvailable(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) {
            return false;
        }
        return list().voices().stream().anyMatch(voice -> voice.id().equals(voiceId.trim()));
    }

    private VoiceCatalogResponse loadTelnyxVoices() {
        var byId = new LinkedHashMap<String, MutableVoice>();
        for (var node : telnyxClient.listNativeVoices().withArray("voices")) {
            var voiceId = firstNonBlank(node.path("id").asText(""), node.path("voice_id").asText(""));
            if (voiceId.isBlank()) {
                continue;
            }
            var provider = node.path("provider").asText("telnyx").trim().toLowerCase();
            if (!"telnyx".equals(provider)) {
                continue;
            }
            var language = normalizeLanguageCode(node.path("language").asText(""));
            if (language.isBlank()) {
                continue;
            }
            var name = firstNonBlank(
                    node.path("name").asText(""),
                    node.path("label").asText(""),
                    voiceId
            );
            var gender = node.path("gender").asText("").trim();
            var model = firstNonBlank(node.path("model_id").asText(""), modelFromVoiceId(voiceId));
            var mutable = byId.computeIfAbsent(
                    voiceId,
                    ignored -> new MutableVoice(voiceId, name, model, gender)
            );
            mutable.languages.add(language);
        }
        var voices = byId.values().stream()
                .map(MutableVoice::toOption)
                .sorted(Comparator.comparing(VoiceOption::name).thenComparing(VoiceOption::id))
                .toList();
        return new VoiceCatalogResponse(List.of("telnyx"), voices);
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private VoiceOption requireVoice(String voiceId) {
        var normalized = voiceId == null ? "" : voiceId.trim();
        return list().voices().stream()
                .filter(candidate -> candidate.id().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Voice is not available"));
    }

    private String normalizePreviewText(String text, String language) {
        if (text == null || text.isBlank()) {
            return previewText(language);
        }
        var normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= MAX_PREVIEW_TEXT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_PREVIEW_TEXT_LENGTH).trim();
    }

    private String previewText(String language) {
        return switch (language) {
            case "fr" -> "Bonjour, vous êtes bien avec Sauti. Comment puis-je vous aider aujourd'hui ?";
            case "ar" -> "مرحباً، معك مساعد ساوتي. كيف يمكنني مساعدتك اليوم؟";
            case "es" -> "Hola, estás hablando con Sauti. ¿Cómo puedo ayudarte hoy?";
            case "de" -> "Hallo, hier ist Sauti. Wie kann ich Ihnen heute helfen?";
            default -> "Hello, this is Sauti. How can I help you today?";
        };
    }

    private String normalizeLanguageCode(String value) {
        var normalized = value == null ? "" : value.trim().toLowerCase().replace('_', '-');
        if (normalized.isBlank()) {
            return "";
        }
        var namedLanguage = LANGUAGE_NAME_CODES.entrySet().stream()
                .filter(entry -> containsWord(normalized, entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
        if (namedLanguage.isPresent()) {
            return namedLanguage.get();
        }
        var separator = normalized.indexOf('-');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    private boolean containsWord(String value, String word) {
        var padded = " " + value.replaceAll("[^a-z]+", " ").trim() + " ";
        return padded.contains(" " + word + " ");
    }

    private String modelFromVoiceId(String voiceId) {
        var parts = voiceId.split("\\.");
        return parts.length >= 3 ? parts[1] : "Telnyx";
    }

    private static final class MutableVoice {
        private final String id;
        private final String name;
        private final String model;
        private final String gender;
        private final LinkedHashSet<String> languages = new LinkedHashSet<>();

        private MutableVoice(String id, String name, String model, String gender) {
            this.id = id;
            this.name = name;
            this.model = model;
            this.gender = gender;
        }

        private VoiceOption toOption() {
            var traits = new LinkedHashMap<String, String>();
            traits.put("model", model);
            if (!gender.isBlank()) {
                traits.put("gender", gender);
            }
            traits.put("description", languages.size() > 1 ? "multilingual" : "native");
            return new VoiceOption(
                    "telnyx",
                    id,
                    name,
                    model + " voice",
                    languages.size() > 1 ? "multilingual" : "native",
                    null,
                    List.copyOf(languages),
                    Map.copyOf(traits),
                    false
            );
        }
    }

    private record PreviewKey(String voiceId, String language, String text) {
    }
}
