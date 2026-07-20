package com.sauti.tool;

import com.sauti.call.Call;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    static Review render(
            Call call,
            Map<String, Object> arguments,
            Map<String, Object> customerDetails,
            String previousToken
    ) {
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

        var canonical = canonical(call, arguments, customerDetails);
        var changedFields = previousSnapshot(call, previousToken)
                .map(previous -> changedFields(previous, snapshotValues(arguments, customerDetails)))
                .orElse(List.of());
        var correction = !changedFields.isEmpty();
        var spoken = correction
                ? correctionSpoken(language, changedFields, fields, customerDetails)
                : fullSpoken(language, fields, customerDetails);
        return new Review(token(canonical), Map.copyOf(fields), spoken, correction, List.copyOf(changedFields));
    }

    private static String fullSpoken(String language, Map<String, Object> fields, Map<String, Object> details) {
        var rawName = fields.get("callerName").toString();
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
            case "fr" -> ", courriel épelé " + email;
            case "sw" -> ", barua pepe ikiandikwa " + email;
            case "ar" -> "، والبريد الإلكتروني تهجئته " + email;
            default -> ", email spelled " + email;
        };
        var extraPart = extra.isBlank() ? "" : switch (language) {
            case "fr" -> ", autres informations " + extra;
            case "sw" -> ", maelezo mengine " + extra;
            case "ar" -> "، والتفاصيل الأخرى " + extra;
            default -> ", other details " + extra;
        };
        return switch (language) {
            case "fr" -> "Avant d'enregistrer le rendez-vous, je vais épeler le nom phonétiquement, lettre par lettre. "
                    + rawName + " : " + name + ". J'ai le téléphone " + phone + emailPart + ", pour " + service
                    + " le " + appointment + ", durée " + duration + " minutes" + extraPart
                    + ". Corrigez-moi simplement si quelque chose est inexact.";
            case "sw" -> "Kabla nihifadhi miadi, nitasoma jina kifonetiki, herufi kwa herufi. "
                    + rawName + ": " + name + ". Nina simu " + phone + emailPart + ", kwa " + service + " "
                    + appointment + ", dakika " + duration + extraPart
                    + ". Tafadhali nisahihishe ikiwa kuna jambo lisilo sahihi.";
            case "ar" -> "قبل حفظ الموعد، سأتهجّى الاسم صوتياً حرفاً بحرف. " + rawName + ": " + name
                    + ". لدي رقم الهاتف " + phone + emailPart + "، لخدمة " + service + " في " + appointment
                    + "، لمدة " + duration + " دقيقة" + extraPart + ". صحح لي فقط أي معلومة غير دقيقة.";
            default -> "Before I save the booking, I’m going to spell the name phonetically, letter by letter. "
                    + rawName + ": " + name + ". I have the phone number " + phone + emailPart + ", for " + service
                    + " on " + appointment + ", for " + duration + " minutes" + extraPart
                    + ". Please correct anything that is wrong.";
        };
    }

    private static String correctionSpoken(
            String language,
            List<String> changed,
            Map<String, Object> fields,
            Map<String, Object> details
    ) {
        var descriptions = changed.stream()
                .map(key -> correctedField(language, key, fields, details))
                .filter(value -> !value.isBlank())
                .toList();
        var values = String.join(switch (language) {
            case "fr" -> ", et ";
            case "sw" -> ", na ";
            case "ar" -> "، و";
            default -> ", and ";
        }, descriptions);
        return switch (language) {
            case "fr" -> "Merci pour la correction. J'ai maintenant " + values + ". Est-ce exact ?";
            case "sw" -> "Asante kwa kunisahihisha. Sasa nina " + values + ". Je, ni sahihi?";
            case "ar" -> "شكراً على التصحيح. لدي الآن " + values + ". هل هذا صحيح؟";
            default -> "Thanks for the correction. I now have " + values + ". Is that right?";
        };
    }

    private static String correctedField(
            String language,
            String key,
            Map<String, Object> fields,
            Map<String, Object> details
    ) {
        return switch (key) {
            case "caller_name" -> switch (language) {
                case "fr" -> "le nom " + fields.get("callerName") + ", épelé phonétiquement lettre par lettre : "
                        + fields.get("callerNameReadback");
                case "sw" -> "jina " + fields.get("callerName") + ", kifonetiki herufi kwa herufi: "
                        + fields.get("callerNameReadback");
                case "ar" -> "الاسم " + fields.get("callerName") + "، متهجّى صوتياً حرفاً بحرف: "
                        + fields.get("callerNameReadback");
                default -> "the name " + fields.get("callerName")
                        + ", spelled phonetically letter by letter: " + fields.get("callerNameReadback");
            };
            case "caller_phone" -> switch (language) {
                case "fr" -> "le numéro de téléphone " + fields.getOrDefault("callerPhoneReadback", "non fourni");
                case "sw" -> "nambari ya simu " + fields.getOrDefault("callerPhoneReadback", "haijatolewa");
                case "ar" -> "رقم الهاتف " + fields.getOrDefault("callerPhoneReadback", "غير متوفر");
                default -> "the phone number " + fields.getOrDefault("callerPhoneReadback", "not provided");
            };
            case "caller_email" -> switch (language) {
                case "fr" -> "le courriel épelé " + fields.getOrDefault("callerEmailReadback", "non fourni");
                case "sw" -> "barua pepe ikiandikwa " + fields.getOrDefault("callerEmailReadback", "haijatolewa");
                case "ar" -> "البريد الإلكتروني متهجّى " + fields.getOrDefault("callerEmailReadback", "غير متوفر");
                default -> "the email spelled " + fields.getOrDefault("callerEmailReadback", "not provided");
            };
            case "service_type" -> label(language, "service", fields.get("service"));
            case "appointment_at" -> label(language, "appointment", fields.get("appointmentSpoken"));
            case "duration_minutes" -> label(language, "duration", fields.get("durationMinutes") + " minutes");
            default -> key.startsWith("detail.")
                    ? humanize(key.substring("detail.".length())) + " "
                        + details.getOrDefault(key.substring("detail.".length()), "")
                    : "";
        };
    }

    private static String label(String language, String label, Object value) {
        var localized = switch (language + ":" + label) {
            case "fr:service" -> "le service";
            case "fr:appointment" -> "le rendez-vous";
            case "fr:duration" -> "la durée";
            case "sw:service" -> "huduma";
            case "sw:appointment" -> "miadi";
            case "sw:duration" -> "muda";
            case "ar:service" -> "الخدمة";
            case "ar:appointment" -> "الموعد";
            case "ar:duration" -> "المدة";
            default -> "the " + label;
        };
        return localized + " " + value;
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
            var canonical = new String(Base64.getUrlDecoder().decode(token.substring(0, separator)), StandardCharsets.UTF_8);
            if (!secureEquals(token(canonical), token)) return Optional.empty();
            var firstSeparator = canonical.indexOf('|');
            if (firstSeparator < 0 || !canonical.substring(0, firstSeparator).equals(String.valueOf(call.getId()))) {
                return Optional.empty();
            }
            return Optional.of(parseEntries(canonical, firstSeparator + 1));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static Optional<String> reviewedValue(Call call, String token, String key) {
        return previousSnapshot(call, token).map(values -> values.get(key)).filter(value -> value != null);
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
        previous.keySet().stream().filter(key -> !current.containsKey(key)).forEach(keys::add);
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
        if (NATO.containsKey(character)) return character + " for " + NATO.get(character);
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

    record Review(
            String token,
            Map<String, Object> fields,
            String spokenResponse,
            boolean correction,
            List<String> changedFields
    ) {
    }
}
