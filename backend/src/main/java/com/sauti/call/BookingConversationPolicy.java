package com.sauti.call;

import java.text.Normalizer;
import java.util.Locale;

/** Deterministic caller-facing policy for stopping an unconfirmed booking flow. */
public final class BookingConversationPolicy {
    private BookingConversationPolicy() {
    }

    public static boolean pausesBooking(String transcript) {
        var normalized = normalize(transcript);
        if (normalized.isBlank()) return false;
        var explicitStop = normalized.matches(
                ".*\\b(?:do not|dont|don't)\\s+(?:book|schedule|save|confirm)\\b.*"
        );
        var deferred = normalized.matches(".*\\b(?:yet|later|another time|not ready)\\b.*")
                || normalized.matches(".*\\bcall (?:you )?back\\b.*");
        return explicitStop && deferred;
    }

    public static String pausedResponse(String language) {
        return switch (language == null ? "en" : language.toLowerCase(Locale.ROOT)) {
            case "fr" -> "Bien sûr. Je ne réserverai rien. Merci de votre appel, et nous serons heureux de vous aider quand vous serez prêt. Au revoir.";
            case "ar" -> "بالتأكيد. لن أحجز أي شيء. شكرًا لاتصالك، وسنسعد بمساعدتك عندما تكون مستعدًا. إلى اللقاء.";
            case "sw" -> "Bila shaka. Sitaweka nafasi yoyote. Asante kwa kupiga simu, na tutafurahi kukusaidia ukiwa tayari. Kwaheri.";
            default -> "Of course. I won’t book anything. Thank you for calling, and we’ll be happy to help when you’re ready. Goodbye.";
        };
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^\\p{L}\\p{N}' ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
