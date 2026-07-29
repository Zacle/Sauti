package com.sauti.voice;

import java.util.Locale;
import java.util.Set;

/** Keeps known language-specific Telnyx voices aligned with the agent's primary language. */
public final class TelnyxVoiceCompatibility {
    private static final Set<String> ENGLISH_NATURAL_HD_VOICES = Set.of(
            "telnyx.naturalhd.astra",
            "telnyx.naturalhd.albion",
            "telnyx.naturalhd.luna"
    );

    private TelnyxVoiceCompatibility() {
    }

    public static String select(String configuredVoice, String language, String defaultVoice) {
        var configured = trim(configuredVoice);
        var fallback = trim(defaultVoice);
        var candidate = configured.toLowerCase(Locale.ROOT).startsWith("telnyx.")
                ? configured
                : fallback;
        var primaryLanguage = trim(language).toLowerCase(Locale.ROOT)
                .replace('_', '-').replaceFirst("-.*$", "");
        if ("fr".equals(primaryLanguage)
                && ENGLISH_NATURAL_HD_VOICES.contains(candidate.toLowerCase(Locale.ROOT))) {
            return "Telnyx.NaturalHD.amarante";
        }
        return candidate;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
