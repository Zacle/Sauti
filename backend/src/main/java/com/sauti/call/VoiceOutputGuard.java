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

    public static String safeAvailabilityFailure(String language) {
        return switch (language == null ? "en" : language.toLowerCase(java.util.Locale.ROOT)) {
            case "fr" -> "Je ne peux pas confirmer le calendrier en direct pour le moment. Le creneau demande n'est pas reserve.";
            case "ar" -> "تعذر تأكيد التقويم المباشر الآن. الموعد المطلوب غير محجوز.";
            case "sw" -> "Siwezi kuthibitisha kalenda kwa sasa. Muda ulioomba haujawekwa nafasi.";
            default -> "I cannot confirm the live calendar right now. Your requested time is not booked.";
        };
    }

    /** OpenAI Realtime limits function call IDs to 32 characters. */
    public static String realtimeCallId(String prefix) {
        var normalizedPrefix = prefix == null ? "call" : prefix.replaceAll("[^A-Za-z0-9_-]", "");
        if (normalizedPrefix.isBlank()) normalizedPrefix = "call";
        normalizedPrefix = normalizedPrefix.substring(0, Math.min(normalizedPrefix.length(), 8));
        var random = java.util.UUID.randomUUID().toString().replace("-", "");
        return normalizedPrefix + "_" + random.substring(0, 31 - normalizedPrefix.length());
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
