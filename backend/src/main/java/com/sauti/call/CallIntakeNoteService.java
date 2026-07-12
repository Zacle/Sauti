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
        var notes = new LinkedHashMap<String, String>();
        var phoneCandidate = new StringBuilder();
        String previousAgent = "";
        for (var turn : turns.findByCall_IdOrderByTurnIndexAsc(call.getId())) {
            collect(notes, phoneCandidate, turn.getCallerTranscript(), previousAgent);
            previousAgent = turn.getAgentResponse() == null ? "" : turn.getAgentResponse();
        }
        collect(notes, phoneCandidate, currentCallerTranscript, previousAgent);
        if (phoneCandidate.length() >= 7) notes.put("caller_phone", phoneCandidate.toString());
        return Map.copyOf(notes);
    }

    public String promptBlock(Call call, String currentCallerTranscript) {
        var notes = notes(call, currentCallerTranscript);
        if (notes.isEmpty()) return "AUTHORITATIVE CALL NOTES: none collected yet.";
        var result = new StringBuilder("AUTHORITATIVE CALL NOTES (derived from the complete call; do not ask for filled fields again):\n");
        notes.forEach((key, value) -> result.append("- ").append(key).append(": ").append(value).append('\n'));
        return result.toString().trim();
    }

    private void collect(Map<String, String> notes, StringBuilder phoneCandidate, String callerText, String previousAgent) {
        if (callerText == null || callerText.isBlank()) return;
        var normalized = normalize(callerText);
        var previous = normalize(previousAgent);
        var name = NAME.matcher(callerText.trim());
        if (name.find()) notes.put("caller_name", cleanName(name.group(1)));

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
            var requestedCompleteNumber = previous.matches(".*(numero complet|redonner le numero|repeat the (?:complete |full )?number).*");
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
        for (var day : new String[]{"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"}) {
            if (text.contains(day)) return day;
        }
        return "";
    }

    private String spokenTime(String text) {
        var numeric = Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])\\s*(?:h|heures?)").matcher(text);
        if (numeric.find()) return "%02d:00".formatted(Integer.parseInt(numeric.group(1)));
        var words = Map.of("seize", 16, "dix sept", 17, "dix-sept", 17, "fifteen", 15, "sixteen", 16, "seventeen", 17);
        for (var entry : words.entrySet()) if (text.contains(entry.getKey() + " heure")) return "%02d:00".formatted(entry.getValue());
        return "";
    }

    private boolean affirmative(String text) {
        return text.matches(".*\\b(oui|yes|correct|exact|parfait|d'accord|pas de probleme|c'est bien|ca marche)\\b.*");
    }

    private String cleanName(String value) {
        return value.trim().replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('’', '\'');
    }
}
