package com.sauti.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class OperatingHoursSchedule {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, DaySchedule>> TYPE = new TypeReference<>() {
    };

    private OperatingHoursSchedule() {
    }

    /**
     * Uses the structured schedule when one was configured. Older/template
     * agents often kept explicit hours only in their saved prompt while the
     * structured field remained "always"; recover that schedule so calendar
     * availability cannot escape the business's declared opening hours.
     */
    public static String effective(Agent agent) {
        return effective(agent, agent == null ? null : agent.getSystemPrompt());
    }

    public static String effective(Agent agent, String resolvedPrompt) {
        if (agent == null) return "always";
        var configured = agent.getOperatingHours();
        if (configured != null && !configured.isBlank()
                && !"always".equalsIgnoreCase(configured)
                && !"workspace".equalsIgnoreCase(configured)) {
            return configured;
        }
        return promptSchedule(resolvedPrompt).orElse(configured == null ? "always" : configured);
    }

    static Optional<String> promptSchedule(String prompt) {
        if (prompt == null || prompt.isBlank()) return Optional.empty();
        var matcher = Pattern.compile(
                "(?im)^\\s*-?\\s*(?:hours?(?:\\s+and\\s+exceptions)?|opening hours?|horaires?)\\s*:\\s*([^\\r\\n#]+)$"
        ).matcher(prompt);
        if (!matcher.find()) return Optional.empty();
        var hoursLine = matcher.group(1).replaceFirst("\\s*\\([^)]*\\)\\s*$", "");
        return compactSchedule(hoursLine).flatMap(OperatingHoursSchedule::writeSchedule);
    }

    private static Optional<Map<String, DaySchedule>> compactSchedule(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        var schedule = new LinkedHashMap<String, DaySchedule>();
        java.util.Arrays.stream(DayOfWeek.values()).forEach(day ->
                schedule.put(day.name().toLowerCase(Locale.ROOT), new DaySchedule(false, "09:00", "17:00"))
        );
        var found = false;
        for (var segment : value.split(";")) {
            var entry = Pattern.compile(
                    "(?iu)^\\s*([\\p{L}]{2,10})(?:\\s*-\\s*([\\p{L}]{2,10}))?\\s+"
                            + "([0-2]?\\d(?::[0-5]\\d)?)\\s*-\\s*([0-2]?\\d(?::[0-5]\\d)?)\\s*$"
            ).matcher(segment);
            if (!entry.matches()) continue;
            var first = day(entry.group(1));
            var last = entry.group(2) == null ? first : day(entry.group(2));
            if (first == null || last == null) continue;
            var start = promptTime(entry.group(3));
            var end = promptTime(entry.group(4));
            var cursor = first;
            for (int count = 0; count < 7; count++) {
                schedule.put(cursor.name().toLowerCase(Locale.ROOT), new DaySchedule(true, start, end));
                if (cursor == last) break;
                cursor = cursor.plus(1);
            }
            found = true;
        }
        return found ? Optional.of(schedule) : Optional.empty();
    }

    private static Optional<String> writeSchedule(Map<String, DaySchedule> schedule) {
        try {
            return Optional.of(OBJECT_MAPPER.writeValueAsString(schedule));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public static boolean isOpen(String value, ZonedDateTime localTime) {
        if (value == null || value.isBlank() || "always".equals(value) || "workspace".equals(value)) {
            return true;
        }
        if ("weekdays".equals(value)) {
            var day = localTime.getDayOfWeek();
            return day != DayOfWeek.SATURDAY
                    && day != DayOfWeek.SUNDAY
                    && !localTime.toLocalTime().isBefore(LocalTime.of(9, 0))
                    && localTime.toLocalTime().isBefore(LocalTime.of(17, 0));
        }
        var schedule = parse(value);
        var day = schedule.get(localTime.getDayOfWeek().name().toLowerCase(Locale.ROOT));
        var current = localTime.toLocalTime();
        if (day != null && day.enabled()) {
            var start = LocalTime.parse(day.start());
            var end = LocalTime.parse(day.end());
            if (end.isAfter(start) && !current.isBefore(start) && current.isBefore(end)) return true;
            if (!end.isAfter(start) && !current.isBefore(start)) return true;
        }
        var previousKey = localTime.getDayOfWeek().minus(1).name().toLowerCase(Locale.ROOT);
        var previous = schedule.get(previousKey);
        if (previous == null || !previous.enabled()) return false;
        var previousStart = LocalTime.parse(previous.start());
        var previousEnd = LocalTime.parse(previous.end());
        return !previousEnd.isAfter(previousStart) && current.isBefore(previousEnd);
    }

    public static void validate(String value) {
        if (value == null || value.isBlank()
                || "always".equals(value) || "workspace".equals(value) || "weekdays".equals(value)) {
            return;
        }
        var schedule = parse(value);
        if (schedule.isEmpty()) throw new IllegalArgumentException("Weekly operating hours cannot be empty");
        for (var entry : schedule.entrySet()) {
            try {
                DayOfWeek.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported operating-hours day: " + entry.getKey());
            }
            var day = entry.getValue();
            if (day == null || !day.enabled()) continue;
            var start = parseTime(day.start(), entry.getKey(), "start");
            var end = parseTime(day.end(), entry.getKey(), "end");
            if (start.equals(end)) {
                throw new IllegalArgumentException("Opening and closing times must differ for " + entry.getKey());
            }
        }
    }

    public static List<TimeRange> rangesFor(String value, LocalDate date, ZoneId timezone) {
        if (value == null || value.isBlank() || "always".equals(value) || "workspace".equals(value)) {
            var start = date.atStartOfDay(timezone).toOffsetDateTime();
            return List.of(new TimeRange(start, start.plusDays(1)));
        }
        if ("weekdays".equals(value)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) return List.of();
            return List.of(range(date, LocalTime.of(9, 0), LocalTime.of(17, 0), timezone));
        }
        var day = parse(value).get(date.getDayOfWeek().name().toLowerCase(Locale.ROOT));
        if (day == null || !day.enabled()) return List.of();
        return List.of(range(date, LocalTime.parse(day.start()), LocalTime.parse(day.end()), timezone));
    }

    public static String describe(String value) {
        if (value == null || value.isBlank() || "always".equals(value) || "workspace".equals(value)) {
            return "No restricted business hours are configured.";
        }
        if ("weekdays".equals(value)) {
            return "Monday-Friday 09:00-17:00; Saturday-Sunday closed.";
        }
        var schedule = parse(value);
        return java.util.Arrays.stream(DayOfWeek.values())
                .map(day -> {
                    var configured = schedule.get(day.name().toLowerCase(Locale.ROOT));
                    var label = day.name().substring(0, 1) + day.name().substring(1).toLowerCase(Locale.ROOT);
                    if (configured == null || !configured.enabled()) return label + " closed";
                    return label + " " + configured.start() + "-" + configured.end();
                })
                .collect(Collectors.joining("; ")) + ".";
    }

    public static String describeForSpeech(String value, String language) {
        var normalizedLanguage = language == null ? "en" : language.toLowerCase(Locale.ROOT);
        if (value == null || value.isBlank() || "always".equals(value) || "workspace".equals(value)) {
            return normalizedLanguage.startsWith("fr")
                    ? "Les heures d’ouverture exactes ne sont pas configurées."
                    : "The exact opening hours are not configured.";
        }
        if ("weekdays".equals(value)) {
            return normalizedLanguage.startsWith("fr")
                    ? "Nous sommes ouverts du lundi au vendredi de 9 heures a 17 heures, et fermes le samedi et le dimanche."
                    : "We are open Monday through Friday from 9 in the morning to 5 in the evening, and closed Saturday and Sunday.";
        }
        var schedule = parse(value);
        if (!normalizedLanguage.startsWith("fr")) return describeEnglishScheduleForSpeech(schedule);
        var openings = new java.util.ArrayList<String>();
        var closed = new java.util.ArrayList<String>();
        for (var day : DayOfWeek.values()) {
            var configured = schedule.get(day.name().toLowerCase(Locale.ROOT));
            var label = spokenDay(day, normalizedLanguage);
            if (configured == null || !configured.enabled()) {
                closed.add(label);
            } else {
                openings.add(label + (normalizedLanguage.startsWith("fr") ? " de " : " from ")
                        + spokenTime(configured.start(), normalizedLanguage)
                        + (normalizedLanguage.startsWith("fr") ? " à " : " to ")
                        + spokenTime(configured.end(), normalizedLanguage));
            }
        }
        return "Nous sommes ouverts " + String.join(", ", openings) + ". Nous sommes fermés "
                + String.join(", ", closed) + ".";
    }

    private static String describeEnglishScheduleForSpeech(Map<String, DaySchedule> schedule) {
        var runs = new java.util.ArrayList<ScheduleRun>();
        ScheduleRun current = null;
        for (var day : DayOfWeek.values()) {
            var configured = schedule.get(day.name().toLowerCase(Locale.ROOT));
            if (configured == null) configured = new DaySchedule(false, "09:00", "17:00");
            if (current != null && sameSpeechSchedule(current.schedule(), configured)) {
                current = new ScheduleRun(current.first(), day, current.schedule());
                runs.set(runs.size() - 1, current);
            } else {
                current = new ScheduleRun(day, day, configured);
                runs.add(current);
            }
        }
        var openings = runs.stream()
                .filter(run -> run.schedule().enabled())
                .map(run -> dayRange(run.first(), run.last()) + " from "
                        + spokenTime(run.schedule().start(), "en") + " to "
                        + spokenTime(run.schedule().end(), "en"))
                .toList();
        var closed = runs.stream()
                .filter(run -> !run.schedule().enabled())
                .map(run -> dayRange(run.first(), run.last()))
                .toList();
        if (openings.isEmpty()) return "We are closed every day.";
        var response = "We are open " + String.join(", ", openings) + ".";
        return closed.isEmpty() ? response : response + " We are closed " + String.join(", ", closed) + ".";
    }

    private static boolean sameSpeechSchedule(DaySchedule first, DaySchedule second) {
        if (!first.enabled() && !second.enabled()) return true;
        return first.enabled() && second.enabled()
                && first.start().equals(second.start())
                && first.end().equals(second.end());
    }

    private static String dayRange(DayOfWeek first, DayOfWeek last) {
        var firstLabel = spokenDay(first, "en");
        if (first == last) return firstLabel;
        var lastLabel = spokenDay(last, "en");
        return last.getValue() - first.getValue() == 1
                ? firstLabel + " and " + lastLabel
                : firstLabel + " through " + lastLabel;
    }

    private static TimeRange range(LocalDate date, LocalTime startTime, LocalTime endTime, ZoneId timezone) {
        var start = date.atTime(startTime).atZone(timezone).toOffsetDateTime();
        var endDate = endTime.isAfter(startTime) ? date : date.plusDays(1);
        return new TimeRange(start, endDate.atTime(endTime).atZone(timezone).toOffsetDateTime());
    }

    private static Map<String, DaySchedule> parse(String value) {
        var compact = compactSchedule(value);
        if (compact.isPresent()) return compact.get();
        try {
            return OBJECT_MAPPER.readValue(value, TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Operating hours must be a valid weekly schedule", exception);
        }
    }

    private static LocalTime parseTime(String value, String day, String field) {
        try {
            return LocalTime.parse(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid " + field + " time for " + day);
        }
    }

    private static DayOfWeek day(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "mon", "monday", "lun", "lundi" -> DayOfWeek.MONDAY;
            case "tue", "tues", "tuesday", "mar", "mardi" -> DayOfWeek.TUESDAY;
            case "wed", "wednesday", "mer", "mercredi" -> DayOfWeek.WEDNESDAY;
            case "thu", "thur", "thurs", "thursday", "jeu", "jeudi" -> DayOfWeek.THURSDAY;
            case "fri", "friday", "ven", "vendredi" -> DayOfWeek.FRIDAY;
            case "sat", "saturday", "sam", "samedi" -> DayOfWeek.SATURDAY;
            case "sun", "sunday", "dim", "dimanche" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static String promptTime(String value) {
        return LocalTime.parse(
                value.contains(":") ? value : value + ":00",
                java.time.format.DateTimeFormatter.ofPattern("H:mm")
        ).toString();
    }

    private static Map<String, DaySchedule> weekdaySchedule() {
        var schedule = new LinkedHashMap<String, DaySchedule>();
        for (var day : DayOfWeek.values()) {
            schedule.put(day.name().toLowerCase(Locale.ROOT), new DaySchedule(
                    day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY,
                    "09:00",
                    "17:00"
            ));
        }
        return schedule;
    }

    private static String spokenDay(DayOfWeek day, String language) {
        if (language.startsWith("fr")) {
            return switch (day) {
                case MONDAY -> "le lundi";
                case TUESDAY -> "le mardi";
                case WEDNESDAY -> "le mercredi";
                case THURSDAY -> "le jeudi";
                case FRIDAY -> "le vendredi";
                case SATURDAY -> "le samedi";
                case SUNDAY -> "le dimanche";
            };
        }
        return day.name().substring(0, 1) + day.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private static String spokenTime(String value, String language) {
        var time = LocalTime.parse(value);
        if (language.startsWith("fr")) {
            if (time.equals(LocalTime.NOON)) return "midi";
            if (time.equals(LocalTime.MIDNIGHT)) return "minuit";
            return time.getMinute() == 0
                    ? time.getHour() + " heures"
                    : time.getHour() + " heures " + time.getMinute();
        }
        if (time.equals(LocalTime.NOON)) return "noon";
        if (time.equals(LocalTime.MIDNIGHT)) return "midnight";
        var hour = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
        var period = time.getHour() < 12 ? "in the morning"
                : time.getHour() < 17 ? "in the afternoon" : "in the evening";
        return time.getMinute() == 0
                ? hour + " " + period
                : hour + ":" + "%02d".formatted(time.getMinute()) + " " + period;
    }

    public record DaySchedule(boolean enabled, String start, String end) {
    }

    private record ScheduleRun(DayOfWeek first, DayOfWeek last, DaySchedule schedule) {
    }

    public record TimeRange(OffsetDateTime start, OffsetDateTime end) {
    }
}
