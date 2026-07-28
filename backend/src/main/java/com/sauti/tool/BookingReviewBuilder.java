package com.sauti.tool;

import com.sauti.call.Call;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Builds a stable, language-neutral accuracy review before a booking is saved. */
final class BookingReviewBuilder {
    private BookingReviewBuilder() {
    }

    static Review build(
            Call call,
            Map<String, Object> arguments,
            Map<String, Object> customerDetails,
            String previousToken
    ) {
        var appointmentAt = OffsetDateTime.parse(text(arguments, "appointment_at"));
        var timezone = bookingTimezone(call);
        var localAppointment = appointmentAt.atZoneSameInstant(timezone);

        var fields = new LinkedHashMap<String, Object>();
        fields.put("callerName", text(arguments, "caller_name"));
        var phone = text(arguments, "caller_phone");
        if (!phone.isBlank()) {
            fields.put("callerPhone", phone);
            fields.put("callerPhoneDigits", digits(phone));
        }
        var email = text(arguments, "caller_email");
        if (!email.isBlank()) fields.put("callerEmail", email);
        fields.put("service", text(arguments, "service_type"));
        fields.put("appointmentAt", appointmentAt.toString());
        fields.put("appointmentLocalDate", localAppointment.toLocalDate().toString());
        fields.put("appointmentLocalTime", localAppointment.toLocalTime().toString());
        fields.put("timezone", timezone.getId());
        fields.put("utcOffset", localAppointment.getOffset().toString());
        fields.put(
                "durationMinutes",
                positiveInteger(
                        arguments.get("duration_minutes"),
                        call.getAgent().getDefaultBookingDurationMinutes()
                )
        );
        if (!customerDetails.isEmpty()) fields.put("customerDetails", Map.copyOf(customerDetails));

        var canonical = canonical(call, arguments, customerDetails);
        var changedFields = previousSnapshot(call, previousToken)
                .map(previous -> changedFields(previous, snapshotValues(arguments, customerDetails)))
                .orElse(List.of());
        return new Review(
                token(canonical),
                Map.copyOf(fields),
                !changedFields.isEmpty(),
                List.copyOf(changedFields)
        );
    }

    private static ZoneId bookingTimezone(Call call) {
        try {
            return ZoneId.of(call.getAgent().getTimezone());
        } catch (RuntimeException exception) {
            return ZoneId.of("UTC");
        }
    }

    private static String token(String canonical) {
        try {
            var payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(canonical.getBytes(StandardCharsets.UTF_8));
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare booking review", exception);
        }
    }

    private static String canonical(Call call, Map<String, Object> arguments, Map<String, Object> details) {
        var canonical = new StringBuilder(String.valueOf(call.getId())).append('|');
        snapshotValues(arguments, details).forEach((key, value) -> append(canonical, key, value));
        return canonical.toString();
    }

    private static Map<String, String> snapshotValues(Map<String, Object> arguments, Map<String, Object> details) {
        var values = new TreeMap<String, String>();
        arguments.forEach((key, value) -> {
            if (!"review_token".equals(key) && !"customer_details".equals(key)) {
                values.put(key, normalized(value));
            }
        });
        details.forEach((key, value) -> values.put("detail." + key, normalized(value)));
        return values;
    }

    private static Optional<Map<String, String>> previousSnapshot(Call call, String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            var separator = token.lastIndexOf('.');
            if (separator <= 0 || separator == token.length() - 1) return Optional.empty();
            var canonical = new String(
                    Base64.getUrlDecoder().decode(token.substring(0, separator)),
                    StandardCharsets.UTF_8
            );
            if (!secureEquals(token(canonical), token)) return Optional.empty();
            var firstSeparator = canonical.indexOf('|');
            if (firstSeparator < 0
                    || !canonical.substring(0, firstSeparator).equals(String.valueOf(call.getId()))) {
                return Optional.empty();
            }
            return Optional.of(parseEntries(canonical, firstSeparator + 1));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static Optional<String> reviewedValue(Call call, String token, String key) {
        return previousSnapshot(call, token)
                .map(values -> values.get(key))
                .filter(value -> value != null);
    }

    private static Map<String, String> parseEntries(String canonical, int cursor) {
        var values = new LinkedHashMap<String, String>();
        while (cursor < canonical.length()) {
            var keyLengthEnd = canonical.indexOf(':', cursor);
            if (keyLengthEnd < 0) throw new IllegalArgumentException("Invalid review token");
            var keyLength = Integer.parseInt(canonical.substring(cursor, keyLengthEnd));
            var keyStart = keyLengthEnd + 1;
            var keyEnd = keyStart + keyLength;
            if (keyEnd >= canonical.length() || canonical.charAt(keyEnd) != '=') {
                throw new IllegalArgumentException("Invalid review token");
            }
            var valueLengthStart = keyEnd + 1;
            var valueLengthEnd = canonical.indexOf(':', valueLengthStart);
            if (valueLengthEnd < 0) throw new IllegalArgumentException("Invalid review token");
            var valueLength = Integer.parseInt(canonical.substring(valueLengthStart, valueLengthEnd));
            var valueStart = valueLengthEnd + 1;
            var valueEnd = valueStart + valueLength;
            if (valueEnd >= canonical.length() || canonical.charAt(valueEnd) != '|') {
                throw new IllegalArgumentException("Invalid review token");
            }
            values.put(canonical.substring(keyStart, keyEnd), canonical.substring(valueStart, valueEnd));
            cursor = valueEnd + 1;
        }
        return Map.copyOf(values);
    }

    private static List<String> changedFields(Map<String, String> previous, Map<String, String> current) {
        var keys = new ArrayList<String>();
        current.forEach((key, value) -> {
            if (!value.equals(previous.get(key))) keys.add(key);
        });
        previous.keySet().stream()
                .filter(key -> !current.containsKey(key))
                .forEach(keys::add);
        return keys;
    }

    private static boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void append(StringBuilder target, String key, Object value) {
        var normalized = normalized(value);
        target.append(key.length()).append(':').append(key)
                .append('=').append(normalized.length()).append(':').append(normalized).append('|');
    }

    private static String normalized(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String text(Map<String, Object> arguments, String key) {
        return normalized(arguments.get(key));
    }

    private static int positiveInteger(Object value, int fallback) {
        try {
            var parsed = value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static List<String> digits(String value) {
        return value.codePoints()
                .filter(Character::isDigit)
                .mapToObj(Character::toString)
                .toList();
    }

    record Review(
            String token,
            Map<String, Object> fields,
            boolean correction,
            List<String> changedFields
    ) {
    }
}
