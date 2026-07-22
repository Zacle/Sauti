package com.sauti.call;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Keeps provider/tool protocol payloads out of caller-facing speech and transcripts. */
public final class VoiceOutputGuard {
    private static final Set<String> SPOKEN_ROLE_LABELS = Set.of(
            "assistant", "agent", "ai", "ai assistant", "virtual assistant", "answer", "response", "final"
    );
    private static final Set<String> PRIVATE_ROLE_LABELS = Set.of(
            "analysis", "reasoning", "commentary", "tool", "tools", "function", "functions",
            "system", "developer", "user", "caller", "recipient"
    );
    private static final Pattern ROLE_LABEL = Pattern.compile(
            "(?iu)^(?:\\*\\*|__)?([\\p{L}][\\p{L}\\p{N}_ ]{0,39})(?:\\*\\*|__)?"
                    + "(?:\\s*:\\s*|\\s+[\\-\\u2013\\u2014]\\s+)"
    );
    private static final Pattern PRIVATE_ROLE_LINE = Pattern.compile(
            "(?imu)(?:^|\\R)\\s*(?:\\*\\*|__)?"
                    + "(?:analysis|reasoning|commentary|tool|tools|function|functions|system|developer|user|caller|recipient)"
                    + "(?:\\*\\*|__)?\\s*(?::|[\\-\\u2013\\u2014])"
    );
    private static final Pattern PRIVATE_SECTION_HEADING = Pattern.compile(
            "(?imu)(?:^|\\R)\\s*(?:#{1,6}\\s*)?(?:\\*\\*|__)?"
                    + "(?:analysis|reasoning|commentary|tool(?:\\s+calls?|\\s+results?)?|"
                    + "function(?:\\s+calls?|\\s+results?)?|system|developer|user|caller|recipient)"
                    + "(?:\\*\\*|__)?\\s*(?:\\R\\s*(?:-{3,}|={3,})\\s*)?(?=\\R|$)"
    );
    private static final Pattern SPOKEN_SECTION_HEADING = Pattern.compile(
            "(?iu)^\\s*(?:#{1,6}\\s*)?(?:\\*\\*|__)?"
                    + "(?:assistant|agent|ai|ai assistant|virtual assistant|answer|final answer|"
                    + "response|final response|assistant answer|assistant response|final)"
                    + "(?:\\*\\*|__)?\\s*(?:\\R\\s*(?:-{3,}|={3,})\\s*)?(?:\\R|$)"
    );
    private static final Pattern MARKUP_ONLY = Pattern.compile("(?s)^\\s*(?:[-=_*#]{3,}\\s*)+$");
    private static final Pattern ROUTED_CHANNEL = Pattern.compile(
            "(?imu)(?:^|\\R)\\s*[\\p{L}_][\\p{L}\\p{N}_-]{0,39}\\s+(?:to|recipient)\\s*="
    );
    private static final Pattern PRIVATE_ROUTING = Pattern.compile(
            "(?iu)\\b(?:analysis|reasoning|commentary|tool|tools|function|functions|assistant|final)"
                    + "\\s+(?:to|recipient)\\s*="
    );
    private static final Pattern NAMESPACE_CALL = Pattern.compile(
            "(?iu)\\b(?:functions|tools)\\.[A-Za-z_][A-Za-z0-9_.-]*(?:\\s|\\(|$)"
    );
    private static final Pattern STRUCTURED_LINE = Pattern.compile(
            "(?mu)(?:^|\\R)\\s*(?:\\{|\\[|```|<\\||<tool_call|<function(?:=|\\s))"
    );
    private static final Pattern INLINE_STRUCTURED = Pattern.compile(
            "(?u)(?:\\{|\\[)\\s*[\"']?[\\p{L}_][\\p{L}\\p{N}_-]{0,63}[\"']?\\s*:"
    );

    private VoiceOutputGuard() {
    }

    public static boolean isProtocolPayload(String text) {
        return text != null && !text.isBlank() && speechText(text).isBlank();
    }

    /**
     * Returns caller-safe speech from one complete provider message. Realtime
     * deltas must not be passed individually: a later delta can turn an
     * apparently natural prefix into private protocol output.
     */
    public static String speechText(String text) {
        return speechText(text, 0);
    }

    private static String speechText(String text, int depth) {
        if (text == null || text.isBlank() || depth > 3) return "";
        var candidate = text.stripLeading();
        var normalized = candidate.toLowerCase(Locale.ROOT);

        // Real tools are provider function_call items. Structured content or
        // private routing syntax inside a message is therefore never inferred
        // as a tool and never allowed to become speech.
        if (STRUCTURED_LINE.matcher(candidate).find()
                || INLINE_STRUCTURED.matcher(candidate).find()
                || PRIVATE_ROLE_LINE.matcher(candidate).find()
                || PRIVATE_SECTION_HEADING.matcher(candidate).find()
                || PRIVATE_ROUTING.matcher(candidate).find()
                || ROUTED_CHANNEL.matcher(candidate).find()
                || NAMESPACE_CALL.matcher(candidate).find()
                || MARKUP_ONLY.matcher(candidate).matches()
                || normalized.startsWith("to=")
                || normalized.startsWith("recipient=")) return "";

        // Models sometimes format a normal reply as an ANSWER/RESPONSE
        // section. Strip the presentation wrapper only when it is the complete
        // first line; words such as "The answer is..." remain natural speech.
        var section = SPOKEN_SECTION_HEADING.matcher(candidate);
        if (section.find()) {
            return speechText(candidate.substring(section.end()).stripLeading(), depth + 1);
        }

        var role = ROLE_LABEL.matcher(candidate);
        if (role.find()) {
            var label = role.group(1).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (PRIVATE_ROLE_LABELS.contains(label)) return "";
            if (SPOKEN_ROLE_LABELS.contains(label)) {
                return speechText(candidate.substring(role.end()).stripLeading(), depth + 1);
            }
        }
        return candidate.trim();
    }

    public static String safeAvailabilityClarification(String language) {
        return switch (language == null ? "en" : language.toLowerCase(Locale.ROOT)) {
            case "fr" -> "Je n’ai pas pu vérifier ce créneau. Pouvez-vous répéter la date et l’heure, s’il vous plaît ?";
            case "ar" -> "لم أتمكن من التحقق من هذا الموعد. هل يمكنك تكرار التاريخ والوقت من فضلك؟";
            case "sw" -> "Sikuweza kuthibitisha muda huo. Tafadhali rudia tarehe na saa.";
            default -> "I couldn’t verify that time. Could you repeat the date and time, please?";
        };
    }

    public static String safeAvailabilityFailure(String language) {
        return switch (language == null ? "en" : language.toLowerCase(Locale.ROOT)) {
            case "fr" -> "Je ne peux pas confirmer le calendrier en direct pour le moment. Le creneau demande n'est pas reserve.";
            case "ar" -> "تعذر تأكيد التقويم المباشر الآن. الموعد المطلوب غير محجوز.";
            case "sw" -> "Siwezi kuthibitisha kalenda kwa sasa. Muda ulioomba haujawekwa nafasi.";
            default -> "I cannot confirm the live calendar right now. Your requested time is not booked.";
        };
    }

    public static String safeBookingFailure(String language) {
        return switch (language == null ? "en" : language.toLowerCase(Locale.ROOT)) {
            case "fr" -> "Je n’ai pas pu enregistrer le rendez-vous dans le calendrier. Il n’est pas réservé. Souhaitez-vous réessayer ?";
            case "ar" -> "لم أتمكن من حفظ الموعد في التقويم، لذلك لم يتم حجزه. هل تريد المحاولة مرة أخرى؟";
            case "sw" -> "Sikuweza kuhifadhi miadi kwenye kalenda, kwa hiyo haijawekwa. Ungependa kujaribu tena?";
            default -> "I couldn’t complete the booking, so it is not saved. I still have the details. Would you like me to try once more?";
        };
    }

    public static String safeBookingMutationFailure(String language, String toolName) {
        var cancellation = "cancel_booking".equals(toolName);
        if ("ar".equalsIgnoreCase(language)) {
            return cancellation
                    ? "\u062A\u0639\u0630\u0631 \u0625\u0644\u063A\u0627\u0621 \u0627\u0644\u0645\u0648\u0639\u062F. \u0644\u0645 \u064A\u062A\u063A\u064A\u0631 \u0627\u0644\u062D\u062C\u0632."
                    : "\u062A\u0639\u0630\u0631 \u062A\u063A\u064A\u064A\u0631 \u0645\u0648\u0639\u062F \u0627\u0644\u062D\u062C\u0632. \u0644\u0645 \u064A\u062A\u063A\u064A\u0631 \u0627\u0644\u062D\u062C\u0632.";
        }
        return switch (language == null ? "en" : language.toLowerCase(Locale.ROOT)) {
            case "fr" -> cancellation
                    ? "Je n'ai pas pu annuler le rendez-vous. La reservation reste inchangee."
                    : "Je n'ai pas pu deplacer le rendez-vous. La reservation reste inchangee.";
            case "ar" -> cancellation
                    ? "Ù„Ù… Ø£ØªÙ…ÙƒÙ† Ù…Ù† Ø¥Ù„ØºØ§Ø¡ Ø§Ù„Ù…ÙˆØ¹Ø¯. Ù…Ø§ Ø²Ø§Ù„ Ø§Ù„Ø­Ø¬Ø² Ø¯ÙˆÙ† ØªØºÙŠÙŠØ±."
                    : "Ù„Ù… Ø£ØªÙ…ÙƒÙ† Ù…Ù† ØªØºÙŠÙŠØ± Ù…ÙˆØ¹Ø¯ Ø§Ù„Ø­Ø¬Ø². Ù…Ø§ Ø²Ø§Ù„ Ø§Ù„Ø­Ø¬Ø² Ø¯ÙˆÙ† ØªØºÙŠÙŠØ±.";
            case "sw" -> cancellation
                    ? "Sikuweza kughairi miadi. Nafasi hiyo haijabadilishwa."
                    : "Sikuweza kubadilisha muda wa miadi. Nafasi hiyo haijabadilishwa.";
            default -> cancellation
                    ? "I couldn't cancel the appointment, so the booking remains unchanged."
                    : "I couldn't reschedule the appointment, so the booking remains unchanged.";
        };
    }

    public static String safeResponseFailure(String language) {
        return switch (language == null ? "en" : language.toLowerCase(Locale.ROOT)) {
            case "fr" -> "Desole, je n'ai pas pu terminer ma reponse. Pouvez-vous repeter votre question, s'il vous plait ?";
            case "ar" -> "عذرا، لم أتمكن من إكمال إجابتي. هل يمكنك تكرار سؤالك من فضلك؟";
            case "sw" -> "Samahani, sikuweza kukamilisha jibu langu. Tafadhali rudia swali lako.";
            default -> "Sorry, I couldn't complete that answer. Could you repeat your question, please?";
        };
    }
}
