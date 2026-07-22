package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.session.BookingDraft;
import com.sauti.session.ConversationState;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a booking tool request exclusively from server-owned conversation state
 * and a slot that the calendar has already verified for this call.
 */
final class BookingToolArgumentResolver {
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "caller_name", "caller_phone", "caller_email", "service_type", "appointment_at"
    );

    private BookingToolArgumentResolver() {
    }

    static Optional<Map<String, Object>> resolve(
            Call call,
            Map<String, String> notes,
            BookingDraft draft
    ) {
        if (call == null || notes == null || draft == null
                || !ConversationState.INTENT_ACTIVE.equals(notes.get("booking_intent"))
                || !value(notes, "booking_number").isBlank()) {
            return Optional.empty();
        }
        var appointmentAt = appointmentAt(call, notes, draft);
        if (appointmentAt.isEmpty()) return Optional.empty();

        var appointmentName = value(notes, "appointment_name");
        if (appointmentName.isBlank()
                && !ConversationState.SUBJECT_OTHER.equals(notes.get("booking_subject"))) {
            appointmentName = value(notes, "caller_name");
        }

        var arguments = new LinkedHashMap<String, Object>();
        put(arguments, "appointment_name", appointmentName);
        put(arguments, "caller_phone", value(notes, "caller_phone"));
        put(arguments, "caller_email", value(notes, "caller_email"));
        put(arguments, "service_type", value(notes, "service_type"));
        arguments.put("appointment_at", appointmentAt.get().toString());
        arguments.put("duration_minutes", draft.durationMinutes() > 0 ? draft.durationMinutes() : 60);
        arguments.put("question_handling", "ready_for_action");

        var details = new LinkedHashMap<String, Object>();
        var requiredFields = call.getAgent().getBookingRequiredFields() == null
                ? java.util.List.<String>of()
                : call.getAgent().getBookingRequiredFields();
        requiredFields.stream()
                .filter(field -> field != null && !TOP_LEVEL_FIELDS.contains(field))
                .forEach(field -> put(details, field, value(notes, field)));
        if (!details.isEmpty()) arguments.put("customer_details", Map.copyOf(details));

        for (var field : requiredFields) {
            var supplied = switch (field) {
                case "caller_name" -> arguments.get("appointment_name");
                case "caller_phone", "caller_email", "service_type", "appointment_at" -> arguments.get(field);
                default -> details.get(field);
            };
            if (supplied == null || supplied.toString().isBlank()) return Optional.empty();
        }
        if (appointmentName.isBlank()
                || clean(arguments.get("service_type")).isBlank()
                || clean(arguments.get("appointment_at")).isBlank()) {
            return Optional.empty();
        }
        var reviewToken = clean(draft.reviewToken());
        if (!reviewToken.isBlank()) arguments.put("review_token", reviewToken);
        return Optional.of(Map.copyOf(arguments));
    }

    static Optional<Map<String, Object>> resolveReschedule(
            Call call,
            Map<String, String> notes,
            BookingDraft draft
    ) {
        if (call == null || notes == null || draft == null
                || !ConversationState.INTENT_ACTIVE.equals(notes.get("booking_intent"))) {
            return Optional.empty();
        }
        var bookingNumber = value(notes, "booking_number");
        var appointmentAt = appointmentAt(call, notes, draft);
        if (bookingNumber.isBlank() || appointmentAt.isEmpty()) return Optional.empty();
        return Optional.of(Map.of(
                "booking_number", bookingNumber,
                "appointment_at", appointmentAt.get().toString(),
                "duration_minutes", draft.durationMinutes() > 0 ? draft.durationMinutes() : 60,
                "question_handling", "ready_for_action",
                "confirmation_state", "confirmed"
        ));
    }

    private static Optional<OffsetDateTime> appointmentAt(
            Call call,
            Map<String, String> notes,
            BookingDraft draft
    ) {
        final OffsetDateTime appointmentAt;
        try {
            appointmentAt = OffsetDateTime.parse(clean(draft.confirmedSlot()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        // A signed review token represents a server-generated review and binds
        // the exact slot. Before review, require the verified slot to still match
        // the normalized date and time in current state.
        if (!clean(draft.reviewToken()).isBlank()) return Optional.of(appointmentAt);
        try {
            var timezone = ZoneId.of(call.getAgent().getTimezone());
            var expectedDate = LocalDate.parse(value(notes, "preferred_day"));
            var expectedTime = LocalTime.parse(value(notes, "preferred_time"));
            var local = appointmentAt.atZoneSameInstant(timezone);
            if (!expectedDate.equals(local.toLocalDate()) || !expectedTime.equals(local.toLocalTime())) {
                return Optional.empty();
            }
            return Optional.of(appointmentAt);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static String value(Map<String, String> values, String key) {
        return clean(values.get(key));
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        var cleaned = clean(value);
        if (!cleaned.isBlank()) target.put(key, cleaned);
    }

    private static String clean(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
