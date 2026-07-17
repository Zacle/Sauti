package com.sauti.call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;

/** Keeps provider/tool protocol payloads out of caller-facing speech and transcripts. */
public final class VoiceOutputGuard {
    private VoiceOutputGuard() {
    }

    public static boolean isStructuredPayload(String text) {
        var normalized = unwrapCodeFence(text);
        if (normalized.isBlank()) return false;
        return (normalized.startsWith("{") && normalized.endsWith("}"))
                || (normalized.startsWith("[") && normalized.endsWith("]"));
    }

    public static Optional<Map<String, Object>> parseObject(ObjectMapper objectMapper, String text) {
        var normalized = unwrapCodeFence(text);
        if (!normalized.startsWith("{") || !normalized.endsWith("}")) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(normalized, new TypeReference<>() { }));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static String safeAvailabilityClarification(String language) {
        return switch (language == null ? "en" : language.toLowerCase(java.util.Locale.ROOT)) {
            case "fr" -> "Je n’ai pas pu vérifier ce créneau. Pouvez-vous répéter la date et l’heure, s’il vous plaît ?";
            case "ar" -> "لم أتمكن من التحقق من هذا الموعد. هل يمكنك تكرار التاريخ والوقت من فضلك؟";
            case "sw" -> "Sikuweza kuthibitisha muda huo. Tafadhali rudia tarehe na saa.";
            default -> "I couldn’t verify that time. Could you repeat the date and time, please?";
        };
    }

    public static String unwrapCodeFence(String text) {
        if (text == null) return "";
        var normalized = text.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("(?is)^```(?:json)?\\s*", "")
                    .replaceFirst("(?is)\\s*```$", "")
                    .trim();
        }
        return normalized;
    }
}
