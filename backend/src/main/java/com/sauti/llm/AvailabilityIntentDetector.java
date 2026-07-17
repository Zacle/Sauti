package com.sauti.llm;

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
        var normalized = transcript.toLowerCase(Locale.ROOT);
        return normalized.contains("availab")
                || normalized.contains("disponib")
                || normalized.contains("créneau")
                || normalized.contains("creneau")
                || normalized.contains("موعد")
                || DATE_OR_TIME.matcher(normalized).find();
    }
}
