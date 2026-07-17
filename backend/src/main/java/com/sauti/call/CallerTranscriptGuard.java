package com.sauti.call;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Rejects empty/non-speech captions before they can create an AI turn. */
final class CallerTranscriptGuard {
    private static final Set<String> NON_SPEECH_CAPTIONS = Set.of(
            "silence", "no speech", "inaudible", "unintelligible", "background noise",
            "music", "blank audio", "audio unclear"
    );

    private CallerTranscriptGuard() {
    }

    static boolean accepts(String transcript) {
        if (transcript == null || transcript.isBlank()) return false;
        var normalized = Normalizer.normalize(transcript, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[\\s\\p{Punct}]+|[\\s\\p{Punct}]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) return false;
        var caption = normalized.replaceAll("^[\\[(<{]+|[\\])> }]+$", "").trim();
        if (NON_SPEECH_CAPTIONS.contains(caption)) return false;
        return normalized.codePoints().anyMatch(Character::isLetterOrDigit);
    }
}
