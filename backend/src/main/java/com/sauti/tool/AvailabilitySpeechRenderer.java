package com.sauti.tool;

import com.sauti.call.Call;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Produces caller-safe, deterministic wording for calendar decisions. */
public final class AvailabilitySpeechRenderer {
    private AvailabilitySpeechRenderer() {
    }

    public static String render(Call call, Map<String, Object> result) {
        var language = language(call);
        var status = Objects.toString(result.get("status"), "calendar_temporarily_unavailable");
        var alternatives = alternatives(result, language);
        var nextOpening = nextOpening(result, language);
        return switch (language) {
            case "fr" -> french(status, alternatives, nextOpening);
            case "ar" -> arabic(status, alternatives, nextOpening);
            case "sw" -> swahili(status, alternatives, nextOpening);
            default -> english(status, alternatives, nextOpening);
        };
    }

    private static String english(String status, String alternatives, String nextOpening) {
        return switch (status) {
            case "requested_time_available" -> "That time is available. Would you like me to continue with the booking?";
            case "requested_time_unavailable" -> alternatives.isBlank()
                    ? "That time is not available. Would you like to choose another time?"
                    : "That time is not available. The closest available times are " + alternatives + ". Which one works for you?";
            case "outside_business_hours" -> "That time is outside the business opening hours. Would you like to choose an earlier time?";
            case "closed_by_business_hours" -> nextOpening.isBlank()
                    ? "The business is closed that day. Would you like to choose another day?"
                    : "The business is closed that day. The next opening is " + nextOpening + ". Would you like that day instead?";
            case "calendar_fully_booked" -> "The business is open that day, but there are no appointment times available. Would you like another day?";
            case "slots_available" -> alternatives.isBlank()
                    ? "There are appointment times available that day. What time would you prefer?"
                    : "The available times include " + alternatives + ". Which one works for you?";
            case "needs_date" -> "Which day would you like me to check?";
            default -> "The business is normally open then, but I cannot confirm live availability right now. Your appointment has not been booked. Would you like to try again later?";
        };
    }

    private static String french(String status, String alternatives, String nextOpening) {
        return switch (status) {
            case "requested_time_available" -> "Ce créneau est disponible. Souhaitez-vous que je continue la réservation ?";
            case "requested_time_unavailable" -> alternatives.isBlank()
                    ? "Ce créneau n'est pas disponible. Souhaitez-vous choisir une autre heure ?"
                    : "Ce créneau n'est pas disponible. Les heures les plus proches sont " + alternatives + ". Laquelle vous convient ?";
            case "outside_business_hours" -> "Cette heure est en dehors des horaires d'ouverture. Souhaitez-vous choisir une heure plus tôt ?";
            case "closed_by_business_hours" -> nextOpening.isBlank()
                    ? "L'établissement est fermé ce jour-là. Souhaitez-vous choisir un autre jour ?"
                    : "L'établissement est fermé ce jour-là. La prochaine ouverture est " + nextOpening + ". Ce jour vous conviendrait-il ?";
            case "calendar_fully_booked" -> "L'établissement est ouvert ce jour-là, mais aucun créneau n'est disponible. Souhaitez-vous choisir un autre jour ?";
            case "slots_available" -> alternatives.isBlank()
                    ? "Des créneaux sont disponibles ce jour-là. Quelle heure préférez-vous ?"
                    : "Les créneaux disponibles comprennent " + alternatives + ". Lequel vous convient ?";
            case "needs_date" -> "Quel jour souhaitez-vous que je vérifie ?";
            default -> "L'établissement est normalement ouvert à cette heure, mais je ne peux pas confirmer la disponibilité en direct pour le moment. Le rendez-vous n'est pas réservé. Souhaitez-vous réessayer plus tard ?";
        };
    }

    private static String arabic(String status, String alternatives, String nextOpening) {
        return switch (status) {
            case "requested_time_available" -> "هذا الموعد متاح. هل تريد أن أتابع الحجز؟";
            case "requested_time_unavailable" -> alternatives.isBlank()
                    ? "هذا الموعد غير متاح. هل تريد اختيار وقت آخر؟"
                    : "هذا الموعد غير متاح. أقرب الأوقات المتاحة هي " + alternatives + ". أيها يناسبك؟";
            case "outside_business_hours" -> "هذا الوقت خارج ساعات العمل. هل تريد اختيار وقت أبكر؟";
            case "closed_by_business_hours" -> "المؤسسة مغلقة في ذلك اليوم. هل تريد اختيار يوم آخر؟";
            case "calendar_fully_booked" -> "المؤسسة مفتوحة في ذلك اليوم، ولكن لا توجد مواعيد متاحة. هل تريد يوماً آخر؟";
            case "slots_available" -> alternatives.isBlank()
                    ? "توجد مواعيد متاحة في ذلك اليوم. ما الوقت الذي تفضله؟"
                    : "الأوقات المتاحة تشمل " + alternatives + ". أيها يناسبك؟";
            case "needs_date" -> "ما اليوم الذي تريد أن أتحقق منه؟";
            default -> "المؤسسة مفتوحة عادةً في ذلك الوقت، لكن لا يمكنني تأكيد التوفر الآن. لم يتم حجز الموعد. هل تريد المحاولة لاحقاً؟";
        };
    }

    private static String swahili(String status, String alternatives, String nextOpening) {
        return switch (status) {
            case "requested_time_available" -> "Muda huo unapatikana. Ungependa niendelee na nafasi hiyo?";
            case "requested_time_unavailable" -> alternatives.isBlank()
                    ? "Muda huo haupatikani. Ungependa kuchagua muda mwingine?"
                    : "Muda huo haupatikani. Nyakati za karibu ni " + alternatives + ". Ni upi unaokufaa?";
            case "outside_business_hours" -> "Muda huo uko nje ya saa za kazi. Ungependa kuchagua muda wa mapema?";
            case "closed_by_business_hours" -> "Biashara imefungwa siku hiyo. Ungependa kuchagua siku nyingine?";
            case "calendar_fully_booked" -> "Biashara imefunguliwa siku hiyo, lakini hakuna nafasi inayopatikana. Ungependa siku nyingine?";
            case "slots_available" -> alternatives.isBlank()
                    ? "Kuna nafasi siku hiyo. Unapendelea saa ngapi?"
                    : "Nyakati zinazopatikana ni pamoja na " + alternatives + ". Ni upi unaokufaa?";
            case "needs_date" -> "Ungependa niangalie siku gani?";
            default -> "Biashara kwa kawaida huwa wazi wakati huo, lakini siwezi kuthibitisha nafasi sasa. Miadi haijawekwa. Ungependa kujaribu tena baadaye?";
        };
    }

    private static String language(Call call) {
        return SpokenDateTimeFormatter.language(call);
    }

    @SuppressWarnings("unchecked")
    private static String alternatives(Map<String, Object> result, String language) {
        var slots = result.get("slots");
        if (!(slots instanceof List<?> list)) return "";
        return list.stream().limit(3)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(slot -> SpokenDateTimeFormatter.slot(
                        Objects.toString(slot.get("start"), ""),
                        Objects.toString(slot.get("displayString"), ""),
                        language
                ))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", "));
    }

    @SuppressWarnings("unchecked")
    private static String nextOpening(Map<String, Object> result, String language) {
        var windows = result.get("nextOpenBusinessWindows");
        if (!(windows instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> window)) {
            return "";
        }
        return SpokenDateTimeFormatter.opening(
                Objects.toString(window.get("date"), ""),
                Objects.toString(window.get("opens"), ""),
                Objects.toString(window.get("closes"), ""),
                language
        );
    }
}
