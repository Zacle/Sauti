package com.sauti.llm;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Detects caller turns that require a live availability lookup before speaking. */
public final class AvailabilityIntentDetector {
    private static final Pattern DATE_OR_TIME = Pattern.compile(
            "(?iu)(?:\\b(?:today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|"
                    + "aujourd'hui|demain|lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche|"
                    + "january|february|march|april|may|june|july|august|september|october|november|december|"
                    + "janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre)\\b|"
                    + "(?:\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b)|"
                    + "(?:\\b(?:1[0-2]|0?[1-9])\\s*(?:a\\.?m\\.?|p\\.?m\\.?))|"
                    + "(?:\\b\\d{4}-\\d{2}-\\d{2}\\b)|"
                    + "(?:اليوم|غد[ًا]?|الاثنين|الثلاثاء|الأربعاء|الخميس|الجمعة|السبت|الأحد))"
    );

    private AvailabilityIntentDetector() {
    }

    public static boolean requiresAvailabilityCheck(String transcript) {
        if (transcript == null || transcript.isBlank()) return false;
        if (asksBusinessHours(transcript)) return false;
        var normalized = transcript.toLowerCase(Locale.ROOT);
        return normalized.contains("availab")
                || normalized.contains("disponib")
                || normalized.contains("créneau")
                || normalized.contains("creneau")
                || normalized.contains("موعد")
                || DATE_OR_TIME.matcher(normalized).find();
    }

    public static boolean asksBusinessHours(String transcript) {
        if (transcript == null || transcript.isBlank()) return false;
        var normalized = Normalizer.normalize(transcript, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        var bookingContext = normalized.matches(".*\\b(book|booking|appointment|trial|class|session|slot|rendez-vous|reserver|consultation|cours|seance|creneau)\\b.*");
        if (bookingContext) return false;
        return normalized.matches(".*\\b(opening hours?|business hours?|what are your hours|when are you open|when are you available|what time do you (?:open|close)|horaires?|heures? d'ouverture|quand (?:etes|est)[^?]*(?:ouvert|disponible)|quelles? heures?)\\b.*");
    }
}
