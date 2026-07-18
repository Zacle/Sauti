package com.sauti.tool;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Converts machine calendar values into phrases suitable for TTS. */
final class SpokenDateTimeFormatter {
    private SpokenDateTimeFormatter() {
    }

    static String appointment(OffsetDateTime value, String language) {
        return date(value.toLocalDate(), language) + " " + at(language) + " " + time(value.toLocalTime(), language);
    }

    static String slot(String isoValue, String fallback, String language) {
        try {
            return time(OffsetDateTime.parse(isoValue).toLocalTime(), language);
        } catch (RuntimeException ignored) {
            try {
                return time(LocalTime.parse(fallback), language);
            } catch (RuntimeException secondIgnored) {
                return fallback;
            }
        }
    }

    static String opening(String isoDate, String opens, String closes, String language) {
        try {
            return date(LocalDate.parse(isoDate), language) + " " + from(language) + " "
                    + time(LocalTime.parse(opens), language) + " " + to(language) + " "
                    + time(LocalTime.parse(closes), language);
        } catch (RuntimeException ignored) {
            return (isoDate + " " + opens + "-" + closes).trim();
        }
    }

    static String date(LocalDate date, String language) {
        var locale = "fr".equals(language) ? Locale.FRENCH : Locale.ENGLISH;
        return date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale));
    }

    static String time(LocalTime time, String language) {
        var hour = time.getHour();
        var minute = time.getMinute();
        if ("fr".equals(language)) {
            if (hour == 12 && minute == 0) return "midi";
            if (hour == 0 && minute == 0) return "minuit";
            return minute == 0 ? hour + " heures" : hour + " heures " + minute;
        }
        if ("ar".equals(language)) {
            return minute == 0 ? "الساعة " + hour : "الساعة " + hour + " و " + minute + " دقيقة";
        }
        if ("sw".equals(language)) {
            return minute == 0 ? "saa " + hour : "saa " + hour + " na dakika " + minute;
        }
        if (hour == 0 && minute == 0) return "midnight";
        if (hour == 12 && minute == 0) return "noon";
        var twelveHour = hour % 12 == 0 ? 12 : hour % 12;
        var period = hour < 12 ? "in the morning" : hour < 17 ? "in the afternoon" : "in the evening";
        return minute == 0
                ? twelveHour + " " + period
                : twelveHour + ":" + "%02d".formatted(minute) + " " + period;
    }

    static String language(com.sauti.call.Call call) {
        var detected = call.getLanguageDetected();
        var configured = call.getAgent() == null ? null : call.getAgent().getDefaultLanguage();
        var value = detected == null || detected.isBlank() ? configured : detected;
        if (value == null) return "en";
        var normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("fr")) return "fr";
        if (normalized.startsWith("ar")) return "ar";
        if (normalized.startsWith("sw")) return "sw";
        return "en";
    }

    private static String at(String language) {
        return switch (language) {
            case "fr" -> "à";
            case "ar" -> "في";
            case "sw" -> "wakati wa";
            default -> "at";
        };
    }

    private static String from(String language) {
        return switch (language) {
            case "fr" -> "de";
            case "ar" -> "من";
            case "sw" -> "kuanzia";
            default -> "from";
        };
    }

    private static String to(String language) {
        return switch (language) {
            case "fr" -> "à";
            case "ar" -> "إلى";
            case "sw" -> "hadi";
            default -> "to";
        };
    }
}
