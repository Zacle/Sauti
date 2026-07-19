package com.sauti.tool;

import com.sauti.call.Call;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Builds a stable, caller-facing accuracy review before a booking is saved. */
final class BookingReviewRenderer {
    private static final Map<Character, String> NATO = Map.ofEntries(
            Map.entry('A', "Alfa"), Map.entry('B', "Bravo"), Map.entry('C', "Charlie"),
            Map.entry('D', "Delta"), Map.entry('E', "Echo"), Map.entry('F', "Foxtrot"),
            Map.entry('G', "Golf"), Map.entry('H', "Hotel"), Map.entry('I', "India"),
            Map.entry('J', "Juliett"), Map.entry('K', "Kilo"), Map.entry('L', "Lima"),
            Map.entry('M', "Mike"), Map.entry('N', "November"), Map.entry('O', "Oscar"),
            Map.entry('P', "Papa"), Map.entry('Q', "Quebec"), Map.entry('R', "Romeo"),
            Map.entry('S', "Sierra"), Map.entry('T', "Tango"), Map.entry('U', "Uniform"),
            Map.entry('V', "Victor"), Map.entry('W', "Whiskey"), Map.entry('X', "X-ray"),
            Map.entry('Y', "Yankee"), Map.entry('Z', "Zulu")
    );

    private BookingReviewRenderer() {
    }

    static Review render(Call call, Map<String, Object> arguments, Map<String, Object> customerDetails) {
        var name = text(arguments, "caller_name");
        var phone = text(arguments, "caller_phone");
        var email = text(arguments, "caller_email");
        var service = text(arguments, "service_type");
        var appointmentAt = OffsetDateTime.parse(text(arguments, "appointment_at"));
        var language = SpokenDateTimeFormatter.language(call);

        var fields = new LinkedHashMap<String, Object>();
        fields.put("callerName", name);
        fields.put("callerNameReadback", phonetic(name));
        if (!phone.isBlank()) {
            fields.put("callerPhone", phone);
            fields.put("callerPhoneReadback", digits(phone));
        }
        if (!email.isBlank()) {
            fields.put("callerEmail", email);
            fields.put("callerEmailReadback", phonetic(email));
        }
        fields.put("service", service);
        fields.put("appointmentAt", appointmentAt.toString());
        fields.put("appointmentSpoken", SpokenDateTimeFormatter.appointment(appointmentAt, language));
        fields.put("durationMinutes", positiveInteger(arguments.get("duration_minutes"), 60));
        if (!customerDetails.isEmpty()) fields.put("customerDetails", Map.copyOf(customerDetails));

        return new Review(token(call, arguments, customerDetails), Map.copyOf(fields),
                spoken(language, fields, customerDetails));
    }

    private static String spoken(String language, Map<String, Object> fields, Map<String, Object> details) {
        var name = fields.get("callerNameReadback").toString();
        var phone = fields.getOrDefault("callerPhoneReadback", "not provided").toString();
        var email = fields.get("callerEmailReadback");
        var service = fields.get("service").toString();
        var appointment = fields.get("appointmentSpoken").toString();
        var duration = fields.get("durationMinutes").toString();
        var extra = details.entrySet().stream()
                .map(entry -> humanize(entry.getKey()) + ": " + entry.getValue())
                .collect(Collectors.joining("; "));
        var emailPart = email == null ? "" : switch (language) {
            case "fr" -> ", courriel " + email;
            case "sw" -> ", barua pepe " + email;
            case "ar" -> "، البريد الإلكتروني " + email;
            default -> ", email " + email;
        };
        var extraPart = extra.isBlank() ? "" : switch (language) {
            case "fr" -> ", autres informations " + extra;
            case "sw" -> ", maelezo mengine " + extra;
            case "ar" -> "، التفاصيل الأخرى " + extra;
            default -> ", other details " + extra;
        };
        return switch (language) {
            case "fr" -> "Avant d'enregistrer le rendez-vous, j'ai le nom " + name + ", le téléphone " + phone
                    + emailPart + ", pour " + service + " le " + appointment + ", durée " + duration + " minutes" + extraPart
                    + ". Corrigez-moi simplement si quelque chose est inexact.";
            case "sw" -> "Kabla nihifadhi miadi, nina jina " + name + ", simu " + phone
                    + emailPart + ", kwa " + service + " " + appointment + ", dakika " + duration + extraPart
                    + ". Tafadhali nisahihishe ikiwa kuna jambo lisilo sahihi.";
            case "ar" -> "قبل حفظ الموعد، لدي الاسم " + name + "، ورقم الهاتف " + phone
                    + emailPart + "، لخدمة " + service + " في " + appointment + "، لمدة " + duration + " دقيقة" + extraPart
                    + ". صحح لي فقط أي معلومة غير دقيقة.";
            default -> "Before I save the booking, I have the name " + name + ", phone " + phone
                    + emailPart + ", for " + service + " on " + appointment + ", for " + duration + " minutes" + extraPart
                    + ". Please correct anything that is wrong.";
        };
    }

    private static String token(Call call, Map<String, Object> arguments, Map<String, Object> details) {
        try {
            var canonical = new StringBuilder(String.valueOf(call.getId())).append('|');
            new TreeMap<>(arguments).forEach((key, value) -> {
                if (!"review_token".equals(key) && !"customer_details".equals(key)) {
                    append(canonical, key, value);
                }
            });
            new TreeMap<>(details).forEach((key, value) -> append(canonical, "detail." + key, value));
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare booking review", exception);
        }
    }

    private static void append(StringBuilder target, String key, Object value) {
        var normalized = value == null ? "" : value.toString().trim();
        target.append(key.length()).append(':').append(key)
                .append('=').append(normalized.length()).append(':').append(normalized).append('|');
    }

    private static String text(Map<String, Object> arguments, String key) {
        var value = arguments.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static int positiveInteger(Object value, int fallback) {
        try {
            var parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? parsed : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String digits(String value) {
        return value.chars()
                .filter(Character::isDigit)
                .mapToObj(character -> Character.toString((char) character))
                .collect(Collectors.joining(", "));
    }

    private static String phonetic(String value) {
        return value.toUpperCase(Locale.ROOT).chars()
                .mapToObj(character -> spokenCharacter((char) character))
                .filter(word -> !word.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String spokenCharacter(char character) {
        if (NATO.containsKey(character)) return NATO.get(character);
        if (Character.isDigit(character)) return Character.toString(character);
        return switch (character) {
            case '@' -> "at sign";
            case '.' -> "dot";
            case '-' -> "hyphen";
            case '_' -> "underscore";
            case '+' -> "plus";
            case '\'' -> "apostrophe";
            default -> Character.isWhitespace(character) ? "space" : Character.toString(character);
        };
    }

    private static String humanize(String key) {
        return java.util.Arrays.stream(key.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    record Review(String token, Map<String, Object> fields, String spokenResponse) {
    }
}
