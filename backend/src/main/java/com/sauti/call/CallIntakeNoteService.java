package com.sauti.call;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CallIntakeNoteService {
    private static final Pattern NAME = Pattern.compile(
            "(?iu)(?:mon nom(?: complet)? (?:c'est|est)|je m'appelle|my (?:full )?name is)\\s+([\\p{L}' -]{2,60})"
    );
    private static final Pattern APPOINTMENT_NAME = Pattern.compile(
            "(?iu)(?:(?:her|his|their|the (?:patient|client|guest)(?:'s)?|my (?:wife|husband|partner|daughter|son|mother|father)(?:'s)?)"
                    + " (?:full )?name (?:is|is going to be)|(?:the )?(?:appointment|booking|reservation) (?:is )?for|"
                    + "(?:book|schedule|reserve)(?:\\s+\\p{L}+){0,4}\\s+for\\s+my\\s+"
                    + "(?:wife|husband|partner|daughter|son|mother|father)\\s*[,;:-]?)"
                    + "\\s+([\\p{L}'’ -]{2,60})"
    );
    private static final Pattern PHONE_CONTEXT = Pattern.compile(
            "(?iu)(?:num[eé]ro|t[eé]l[eé]phone|phone|contact|joindre)"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"
    );
    private static final Map<String, String> DIGITS = Map.ofEntries(
            Map.entry("zero", "0"), Map.entry("oh", "0"),
            Map.entry("un", "1"), Map.entry("une", "1"), Map.entry("one", "1"),
            Map.entry("deux", "2"), Map.entry("two", "2"),
            Map.entry("trois", "3"), Map.entry("three", "3"),
            Map.entry("quatre", "4"), Map.entry("four", "4"),
            Map.entry("cinq", "5"), Map.entry("five", "5"),
            Map.entry("six", "6"), Map.entry("seven", "7"), Map.entry("sept", "7"), Map.entry("septante", "7"),
            Map.entry("huit", "8"), Map.entry("eight", "8"),
            Map.entry("neuf", "9"), Map.entry("nine", "9")
    );

    private final CallTurnRepository turns;

    public CallIntakeNoteService(CallTurnRepository turns) {
        this.turns = turns;
    }

    public Map<String, String> notes(Call call, String currentCallerTranscript) {
        return snapshot(call, currentCallerTranscript).notes();
    }

    private IntakeSnapshot snapshot(Call call, String currentCallerTranscript) {
        var notes = new LinkedHashMap<String, String>();
        var phoneCandidate = new StringBuilder();
        String previousAgent = "";
        for (var turn : turns.findByCall_IdOrderByTurnIndexAsc(call.getId())) {
            collect(notes, phoneCandidate, turn.getCallerTranscript(), previousAgent);
            previousAgent = turn.getAgentResponse() == null ? "" : turn.getAgentResponse();
        }
        collect(notes, phoneCandidate, currentCallerTranscript, previousAgent);
        if (phoneCandidate.length() >= 7) notes.put("caller_phone", phoneCandidate.toString());
        return new IntakeSnapshot(Map.copyOf(notes), previousAgent);
    }

    public String promptBlock(Call call, String currentCallerTranscript) {
        var snapshot = snapshot(call, currentCallerTranscript);
        var notes = snapshot.notes();
        var phoneStillPending = asksForPhone(snapshot.previousAgent()) && !notes.containsKey("caller_phone");
        if (notes.isEmpty() && !phoneStillPending) return "AUTHORITATIVE CALL NOTES: none collected yet.";
        var result = new StringBuilder("AUTHORITATIVE CURRENT CALL STATE (derived from the complete call; retain these values and do not ask for filled fields again):\n");
        if (notes.isEmpty()) result.append("- no booking values have been collected yet.\n");
        notes.forEach((key, value) -> result.append("- ")
                .append(switch (key) {
                    case "caller_name" -> "caller_name (person speaking)";
                    case "appointment_name" -> "appointment_name (person receiving the booked service)";
                    case "booking_for_relation" -> "booking_for_relation (appointment is for this other person)";
                    case "service_type" -> "service_type selected for this booking";
                    default -> key;
                })
                .append(": ").append(value).append('\n'));
        if (notes.containsKey("booking_for_relation") && !notes.containsKey("appointment_name")) {
            result.append("- booking subject name is still missing; never substitute caller_name for it.\n");
        }
        if (phoneStillPending) {
            result.append("- CURRENT PENDING FIELD: caller_phone. The latest caller turn did not provide a usable complete phone number. "
                    + "Do not say it was captured and do not advance to another booking field. Answer a question if they asked one; "
                    + "otherwise ask them to repeat or continue the phone number naturally.\n");
        }
        return result.toString().trim();
    }

    private void collect(Map<String, String> notes, StringBuilder phoneCandidate, String callerText, String previousAgent) {
        if (callerText == null || callerText.isBlank()) return;
        var normalized = normalize(callerText);
        var previous = normalize(previousAgent);
        var name = NAME.matcher(callerText.trim());
        if (name.find()) {
            var callerName = cleanName(name.group(1));
            notes.put("caller_name", callerName);
            if (!notes.containsKey("booking_for_relation")) notes.putIfAbsent("appointment_name", callerName);
        }

        var relationship = bookingRelationship(normalized);
        if (!relationship.isBlank()) {
            notes.put("booking_for_relation", relationship);
            if (notes.getOrDefault("appointment_name", "").equals(notes.getOrDefault("caller_name", ""))) {
                notes.remove("appointment_name");
            }
        }

        var appointmentName = APPOINTMENT_NAME.matcher(callerText.trim());
        if (appointmentName.find()) {
            putAppointmentName(notes, appointmentName.group(1));
        } else if (asksForName(previous) && plausibleNameOnly(callerText)) {
            putAppointmentName(notes, callerText);
        }

        if (asksForService(previous) && plausibleServiceAnswer(callerText)) {
            notes.put("service_type", cleanServiceAnswer(callerText));
        }

        var email = EMAIL.matcher(callerText);
        if (email.find()) notes.put("caller_email", email.group());
        if (previous.contains("date de naissance") || previous.contains("birth date") || previous.contains("date of birth")) {
            notes.put("date_of_birth_spoken", callerText.trim());
        }
        if (previous.matches(".*\\b(adresse|address)\\b.*") && !normalized.contains("email")) {
            notes.put("caller_address", callerText.trim());
        }

        var repetitionInstruction = normalized.matches(".*\\b(?:il y a )?trois (?:fois )?(?:un|a)\\b.*");
        if (repetitionInstruction) {
            notes.put("phone_repetition_hint", "the number contains three consecutive 1 digits");
            phoneCandidate.setLength(0);
        }
        var digitSource = normalized;
        if (repetitionInstruction && normalized.contains("cest zero")) {
            digitSource = normalized.substring(normalized.indexOf("cest zero") + "cest ".length());
        }
        var digits = repetitionInstruction && !normalized.contains("cest zero") ? "" : spokenDigits(digitSource);
        var phoneContext = PHONE_CONTEXT.matcher(callerText).find()
                || previous.matches(".*(numero|telephone|phone|contact).*")
                || (phoneCandidate.length() > 0 && digitOnlyFragment(normalized));
        if (phoneContext && !digits.isBlank()) {
            var restart = normalized.matches(".*\\b(no|non|restart|recommencer|correction|commence par|depuis le debut|a zero)\\b.*");
            var requestedCompleteNumber = previous.matches(
                    ".*(numero complet|redonner le numero|donner a nouveau (?:tout )?le numero|"
                            + "repeter (?:tout )?le numero|repeat the (?:complete |full )?number).*"
            );
            if (restart || requestedCompleteNumber || (PHONE_CONTEXT.matcher(callerText).find() && digits.length() >= 7)) {
                phoneCandidate.setLength(0);
            }
            phoneCandidate.append(digits);
        }
        if (normalized.contains("consultation")) notes.put("service_or_reason", "consultation");
        else if (normalized.contains("visite de suivi") || normalized.contains("follow-up")) notes.put("service_or_reason", "follow-up visit");
        else if (normalized.contains("rappel") || normalized.contains("callback")) notes.put("service_or_reason", "callback");

        var day = weekday(normalized);
        if (!day.isBlank()) notes.put("preferred_day", day);
        var time = spokenTime(normalized);
        if (!time.isBlank()) notes.put("preferred_time", time);

        if (affirmative(normalized)) {
            var proposedDay = weekday(previous);
            var proposedTime = spokenTime(previous);
            if (!proposedDay.isBlank()) notes.put("preferred_day", proposedDay);
            if (!proposedTime.isBlank()) notes.put("preferred_time", proposedTime);
        }
    }

    private String spokenDigits(String normalized) {
        normalized = normalized
                .replaceAll("\\ba\\s+a\\s+a\\b", "un un un")
                .replaceAll("\\ba\\s+a\\b", "un un")
                .replace("cinq cent septante cinq", "cinq sept cinq")
                .replace("cent onze", "un un un")
                .replace("onze", "un un");
        var result = new StringBuilder();
        for (var token : normalized.split("[^a-z0-9]+")) {
            if (token.matches("\\d+")) result.append(token);
            else if (DIGITS.containsKey(token)) result.append(DIGITS.get(token));
        }
        return result.toString();
    }

    private boolean digitOnlyFragment(String normalized) {
        var expanded = normalized
                .replaceAll("\\ba\\s+a\\s+a\\b", "un un un")
                .replaceAll("\\ba\\s+a\\b", "un un")
                .replace("cent onze", "un un un")
                .replace("onze", "un un");
        for (var token : expanded.split("[^a-z0-9]+")) {
            if (token.isBlank() || token.matches("\\d+") || DIGITS.containsKey(token)
                    || java.util.Set.of("et", "oui", "non", "cest", "ca", "bien", "cela", "correct").contains(token)) {
                continue;
            }
            return false;
        }
        return !spokenDigits(normalized).isBlank();
    }

    private String weekday(String text) {
        for (var relative : new String[]{"aujourd'hui", "demain", "today", "tomorrow"}) {
            if (text.contains(relative)) return relative;
        }
        for (var day : new String[]{
                "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche",
                "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
        }) {
            if (text.contains(day)) return day;
        }
        return "";
    }

    private String spokenTime(String text) {
        var clock = Pattern.compile(
                "(?<!\\d)([01]?\\d|2[0-3])(?::([0-5]\\d))\\s*(a\\.?m\\.?|p\\.?m\\.?)?",
                Pattern.CASE_INSENSITIVE
        ).matcher(text);
        if (clock.find()) {
            var hour = Integer.parseInt(clock.group(1));
            var minute = Integer.parseInt(clock.group(2));
            var meridiem = clock.group(3) == null ? "" : clock.group(3).replace(".", "");
            if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
            if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
            return "%02d:%02d".formatted(hour, minute);
        }
        var meridiemHour = Pattern.compile(
                "(?<!\\d)(1[0-2]|0?[1-9])\\s*(a\\.?m\\.?|p\\.?m\\.?)",
                Pattern.CASE_INSENSITIVE
        ).matcher(text);
        if (meridiemHour.find()) {
            var hour = Integer.parseInt(meridiemHour.group(1));
            var meridiem = meridiemHour.group(2).replace(".", "");
            if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
            if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
            return "%02d:00".formatted(hour);
        }
        var numeric = Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])\\s*(?:h|heures?)").matcher(text);
        if (numeric.find()) return "%02d:00".formatted(Integer.parseInt(numeric.group(1)));
        var words = Map.of("seize", 16, "dix sept", 17, "dix-sept", 17, "fifteen", 15, "sixteen", 16, "seventeen", 17);
        for (var entry : words.entrySet()) if (text.contains(entry.getKey() + " heure")) return "%02d:00".formatted(entry.getValue());
        return "";
    }

    private boolean affirmative(String text) {
        return text.matches(".*\\b(oui|yes|correct|exact|parfait|d'accord|pas de probleme|c'est bien|ca marche)\\b.*");
    }

    private String bookingRelationship(String normalized) {
        var patterns = Map.ofEntries(
                Map.entry("wife", "(?:for|pour) (?:my |ma )?(?:wife|femme)"),
                Map.entry("husband", "(?:for|pour) (?:my |mon )?(?:husband|mari)"),
                Map.entry("partner", "(?:for|pour) (?:my |mon |ma )?partner"),
                Map.entry("daughter", "(?:for|pour) (?:my |ma )?(?:daughter|fille)"),
                Map.entry("son", "(?:for|pour) (?:my |mon )?(?:son|fils)"),
                Map.entry("mother", "(?:for|pour) (?:my |ma )?(?:mother|mere)"),
                Map.entry("father", "(?:for|pour) (?:my |mon )?(?:father|pere)"),
                Map.entry("someone else", "(?:for|pour) (?:someone else|quelqu'un d'autre)"),
                Map.entry("patient", "(?:for|pour) (?:the |a |le |un )?(?:patient|client|guest)"));
        return patterns.entrySet().stream()
                .filter(entry -> normalized.matches(".*\\b" + entry.getValue() + "\\b.*"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    private boolean asksForName(String previous) {
        return previous.matches(".*\\b(name|nom|jina)\\b.*")
                && previous.matches(".*\\b(what|who|have|share|tell|give|could|may|quel|comment|donner|dire|appelle)\\b.*");
    }

    private boolean asksForService(String previous) {
        return previous.matches(".*\\b(service|hairstyle|haircut|prestation|soin|appointment for|booking for)\\b.*")
                && previous.matches(".*\\b(what|which|would|want|like|quel|quelle|souhaite|voudrait)\\b.*");
    }

    private boolean asksForPhone(String previous) {
        var normalized = normalize(previous);
        return PHONE_CONTEXT.matcher(normalized).find()
                && normalized.matches(".*\\b(what|which|have|share|tell|give|could|may|use|quel|donner|dire|utiliser)\\b.*");
    }

    private boolean plausibleNameOnly(String value) {
        var candidate = cleanAnswer(value);
        var normalized = normalize(candidate);
        if (!candidate.matches("[\\p{L}'’ -]{2,60}") || candidate.trim().split("\\s+").length > 5) return false;
        return !normalized.matches("(?:yes|no|okay|ok|sure|correct|right|ready|oui|non|d'accord|merci|thank you|thanks)");
    }

    private boolean plausibleServiceAnswer(String value) {
        var candidate = cleanServiceAnswer(value);
        var normalized = normalize(candidate);
        return candidate.matches("[\\p{L}0-9'’ &-]{2,80}")
                && candidate.trim().split("\\s+").length <= 10
                && !normalized.matches("(?:yes|no|okay|ok|sure|correct|right|ready|oui|non|d'accord|merci|thank you|thanks)");
    }

    private void putAppointmentName(Map<String, String> notes, String value) {
        var candidate = cleanName(cleanAnswer(value));
        var normalized = normalize(candidate);
        if (candidate.isBlank() || normalized.matches("(?:my wife|my husband|my partner|ma femme|mon mari)")) return;
        notes.put("appointment_name", candidate);
    }

    private String cleanAnswer(String value) {
        return value == null ? "" : value.trim()
                .replaceAll("^[\\s,;:]+|[\\s.!?,;:]+$", "")
                .replaceAll("\\s+", " ");
    }

    private String cleanServiceAnswer(String value) {
        return cleanAnswer(value)
                .replaceFirst("(?iu)\\s*[,;]?\\s+(?:but|however|mais|cependant)\\b.*$", "")
                .replaceAll("[\\s.!?,;:]+$", "")
                .trim();
    }

    private String cleanName(String value) {
        return value.trim()
                .replaceFirst("(?iu)\\s+(?:and\\s+i\\b|et\\s+je\\b|et\\s+j['’]|na\\s+(?:nina|nataka)\\b|و\\s*أنا\\b).*$", "")
                .replaceAll("[.!?]+$", "")
                .replaceAll("\\s+", " ");
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('’', '\'');
    }
    private record IntakeSnapshot(Map<String, String> notes, String previousAgent) { }
}
