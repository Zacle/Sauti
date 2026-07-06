package com.sauti.nlp;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SimpleLanguageDetector implements LanguageDetector {
    private static final Set<String> SWAHILI_MARKERS = Set.of(
            "habari", "asante", "tafadhali", "ndiyo", "hapana", "nataka",
            "nina", "msaada", "miadi", "kesho", "karibu", "sawa", "samahani",
            "naomba", "ningependa", "leo", "jana", "mimi", "wewe", "yangu",
            "wangu", "gani", "lini", "wapi", "kwa", "lakini", "pia", "sana"
    );
    private static final Set<String> ENGLISH_MARKERS = Set.of(
            "hello", "please", "thanks", "appointment", "tomorrow", "help", "yes", "no",
            "want", "speak", "human", "book", "schedule", "exactly", "absolutely",
            "today", "yesterday", "when", "where", "would", "could", "need", "my",
            "your", "the", "and", "but", "also"
    );
    private static final Set<String> FRENCH_MARKERS = Set.of(
            "bonjour", "merci", "rendez", "oui", "non", "exactement", "absolument",
            "demain", "aujourd", "aide", "voudrais", "souhaite", "besoin", "parler",
            "avec", "quand", "ou", "mon", "ma", "mes", "votre", "pour", "mais",
            "aussi", "bien", "sur", "pouvez", "pourriez"
    );

    @Override
    public String detect(String transcript, String defaultLanguage, List<String> supportedLanguages) {
        var supported = supportedLanguages == null
                ? List.<String>of()
                : supportedLanguages.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
        var safeDefault = defaultLanguage == null ? "" : defaultLanguage.toLowerCase(Locale.ROOT);
        if (transcript == null || transcript.isBlank()) {
            return fallback(safeDefault, supported);
        }
        var lower = transcript.toLowerCase(Locale.ROOT);
        var normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        if (containsArabic(lower) && supported.contains("ar")) {
            return "ar";
        }
        var words = words(normalized);
        var frenchScore = supported.contains("fr") ? score(words, FRENCH_MARKERS) : 0;
        var swahiliScore = supported.contains("sw") ? score(words, SWAHILI_MARKERS) : 0;
        var englishScore = supported.contains("en") ? score(words, ENGLISH_MARKERS) : 0;
        var highest = Math.max(frenchScore, Math.max(swahiliScore, englishScore));
        if (highest > 0) {
            if (frenchScore == highest) return "fr";
            if (swahiliScore == highest) return "sw";
            return "en";
        }
        return fallback(safeDefault, supported);
    }

    private List<String> words(String value) {
        var cleaned = value.replaceAll("[^\\p{L}]+", " ").trim();
        return cleaned.isBlank() ? List.of() : List.of(cleaned.split("\\s+"));
    }

    private int score(List<String> words, Set<String> markers) {
        return (int) words.stream().filter(markers::contains).distinct().count();
    }

    private boolean containsArabic(String value) {
        return value.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x0600 && codePoint <= 0x06FF)
                        || (codePoint >= 0x0750 && codePoint <= 0x077F)
                        || (codePoint >= 0x08A0 && codePoint <= 0x08FF)
        );
    }

    private String fallback(String defaultLanguage, List<String> supportedLanguages) {
        if (!defaultLanguage.isBlank()) {
            return defaultLanguage;
        }
        return supportedLanguages.isEmpty() ? "en" : supportedLanguages.get(0);
    }
}
