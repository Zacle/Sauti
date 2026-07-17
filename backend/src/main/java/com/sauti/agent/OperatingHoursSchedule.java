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
import java.util.Locale;
import java.util.Map;

public final class OperatingHoursSchedule {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, DaySchedule>> TYPE = new TypeReference<>() {
    };

    private OperatingHoursSchedule() {
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

    private static TimeRange range(LocalDate date, LocalTime startTime, LocalTime endTime, ZoneId timezone) {
        var start = date.atTime(startTime).atZone(timezone).toOffsetDateTime();
        var endDate = endTime.isAfter(startTime) ? date : date.plusDays(1);
        return new TimeRange(start, endDate.atTime(endTime).atZone(timezone).toOffsetDateTime());
    }

    private static Map<String, DaySchedule> parse(String value) {
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

    public record DaySchedule(boolean enabled, String start, String end) {
    }

    public record TimeRange(OffsetDateTime start, OffsetDateTime end) {
    }
}
