package com.sauti.call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;

/** Keeps provider/tool protocol payloads out of caller-facing speech and transcripts. */
public final class VoiceOutputGuard {
    private static final java.util.List<String> PROTOCOL_PREFIXES = java.util.List.of(
            "analysis to=",
            "assistant to=",
            "commentary to=",
            "tool to=",
            "final to=",
            "recipient=",
            "to=functions.",
            "to=tools.",
            "<|",
            "<tool_call",
            "<function="
    );

    public enum StreamDisposition {
        UNDECIDED,
        SPEECH,
        PROTOCOL
    }

    private VoiceOutputGuard() {
    }

    public static boolean isStructuredPayload(String text) {
        var normalized = unwrapCodeFence(text);
        if (normalized.isBlank()) return false;
        return (normalized.startsWith("{") && normalized.endsWith("}"))
                || (normalized.startsWith("[") && normalized.endsWith("]"));
    }

    public static boolean isProtocolPayload(String text) {
        if (isStructuredPayload(text)) return true;
        return classifyStreamingPrefix(text) == StreamDisposition.PROTOCOL;
    }

    /**
     * Classifies the beginning of a streamed model response before it is sent to
     * TTS. Partial protocol markers stay undecided until they can be distinguished
     * from normal speech, so even a marker split across provider deltas is held.
     */
    public static StreamDisposition classifyStreamingPrefix(String text) {
        if (text == null || text.isBlank()) return StreamDisposition.UNDECIDED;
        var normalized = text.stripLeading().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("{") || normalized.startsWith("[") || normalized.startsWith("```")) {
            return StreamDisposition.PROTOCOL;
        }
        for (var prefix : PROTOCOL_PREFIXES) {
            if (normalized.startsWith(prefix)) return StreamDisposition.PROTOCOL;
            if (prefix.startsWith(normalized)) return StreamDisposition.UNDECIDED;
        }
        return StreamDisposition.SPEECH;
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

    public static String safeBookingFailure(String language) {
        return switch (language == null ? "en" : language.toLowerCase(java.util.Locale.ROOT)) {
            case "fr" -> "Je n’ai pas pu enregistrer le rendez-vous dans le calendrier. Il n’est pas réservé. Souhaitez-vous réessayer ?";
            case "ar" -> "لم أتمكن من حفظ الموعد في التقويم، لذلك لم يتم حجزه. هل تريد المحاولة مرة أخرى؟";
            case "sw" -> "Sikuweza kuhifadhi miadi kwenye kalenda, kwa hiyo haijawekwa. Ungependa kujaribu tena?";
            default -> "I couldn’t save the appointment to the calendar, so it is not booked. Would you like to try again?";
        };
    }

    public static String safeResponseFailure(String language) {
        return switch (language == null ? "en" : language.toLowerCase(java.util.Locale.ROOT)) {
            case "fr" -> "Desole, je n'ai pas pu terminer ma reponse. Pouvez-vous repeter votre question, s'il vous plait ?";
            case "ar" -> "عذرا، لم أتمكن من إكمال إجابتي. هل يمكنك تكرار سؤالك من فضلك؟";
            case "sw" -> "Samahani, sikuweza kukamilisha jibu langu. Tafadhali rudia swali lako.";
            default -> "Sorry, I couldn't complete that answer. Could you repeat your question, please?";
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
