package com.sauti.tool;

import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.calendar.BookingService;
import com.sauti.agent.OperatingHoursSchedule;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SautiCalendarFulfillment implements ToolFulfillment {
    private static final Logger LOGGER = LoggerFactory.getLogger(SautiCalendarFulfillment.class);
    private static final Pattern SPOKEN_TIME = Pattern.compile(
            "(?<!\\d)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?!\\d)",
            Pattern.CASE_INSENSITIVE
    );
    private final CalendarProviderFactory calendarProviderFactory;
    private final BookingService bookingService;

    public SautiCalendarFulfillment(CalendarProviderFactory calendarProviderFactory, BookingService bookingService) {
        this.calendarProviderFactory = calendarProviderFactory;
        this.bookingService = bookingService;
    }

    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        try {
            return switch (toolCall.name()) {
                case "check_availability" -> LlmToolResult.success(toolCall, checkAvailability(call, toolCall.arguments(), toolConfig));
                case "book_slot" -> LlmToolResult.success(toolCall, bookSlot(call, toolCall, toolConfig));
                case "reschedule_booking" -> LlmToolResult.success(toolCall, reschedule(call, toolCall));
                case "cancel_booking" -> LlmToolResult.success(toolCall, cancel(call, toolCall));
                default -> LlmToolResult.error(toolCall, "Unrecognised calendar tool: " + toolCall.name());
            };
        } catch (RuntimeException exception) {
            return LlmToolResult.error(toolCall, exception.getMessage());
        }
    }

    private Map<String, Object> checkAvailability(Call call, Map<String, Object> arguments, AgentTool toolConfig) {
        var timezone = ZoneId.of(stringArg(arguments, "timezone", call.getAgent().getTimezone()));
        var rawDate = stringArg(arguments, "date", "");
        final LocalDate date;
        try {
            date = rawDate.isBlank() ? null : LocalDate.parse(rawDate);
        } catch (java.time.format.DateTimeParseException exception) {
            return missingDate(call, timezone);
        }
        if (date == null) return missingDate(call, timezone);
        var duration = intArg(arguments, "duration_minutes", 60);
        var effectiveHours = OperatingHoursSchedule.effective(call.getAgent());
        var operatingRanges = OperatingHoursSchedule.rangesFor(
                effectiveHours, date, timezone
        );
        var requestedTimeText = stringArg(arguments, "time_preference", "");
        var requestedTime = parseRequestedTime(requestedTimeText);
        var requestedStart = requestedTime.map(time -> date.atTime(time).atZone(timezone).toOffsetDateTime());
        var withinOperatingHours = requestedStart.map(start -> operatingRanges.stream().anyMatch(range ->
                !start.isBefore(range.start()) && !start.plusMinutes(duration).isAfter(range.end())
        )).orElse(null);
        var businessOpen = !operatingRanges.isEmpty();
        var calendarLive = true;
        List<com.sauti.calendar.CalendarAvailabilitySlot> availableSlots = List.of();
        if (businessOpen && !Boolean.FALSE.equals(withinOperatingHours)) {
            try {
                var provider = calendarProviderFactory.forTool(toolConfig, call.getTenant().getId());
                availableSlots = provider.availability(call.getAgent(), date, duration, timezone);
            } catch (RuntimeException exception) {
                calendarLive = false;
                LOGGER.warn("Live calendar availability failed for call {} and agent {}: {}",
                        call.getId(), call.getAgent().getId(), exception.getMessage());
            }
        }
        var slots = availableSlots;
        var matchingSlot = requestedStart.flatMap(start -> slots.stream()
                .filter(slot -> slot.start().isEqual(start))
                .findFirst());
        var result = new LinkedHashMap<String, Object>();
        result.put("date", date.toString());
        result.put("timezone", timezone.toString());
        result.put("durationMinutes", duration);
        result.put("businessHoursSummary", OperatingHoursSchedule.describe(effectiveHours));
        result.put("businessOpenOnRequestedDate", businessOpen);
        result.put("operatingWindows", operatingRanges.stream().map(range -> Map.of(
                "start", range.start().toString(),
                "end", range.end().toString()
        )).toList());
        result.put("requestedTime", requestedTime.map(LocalTime::toString).orElse(requestedTimeText));
        if (withinOperatingHours != null) result.put("requestedTimeWithinOperatingHours", withinOperatingHours);
        if (requestedTime.isPresent()) result.put("requestedTimeAvailable", matchingSlot.isPresent());
        matchingSlot.ifPresent(slot -> result.put("matchingSlot", slotMap(slot)));
        result.put("calendarLive", calendarLive);
        result.put("totalAvailableSlots", slots.size());
        result.put("slots", relevantSlots(slots, requestedTime));
        result.put("nextOpenBusinessWindows", nextOpenBusinessWindows(effectiveHours, date, timezone));
        result.put("status", !businessOpen
                ? "closed_by_business_hours"
                : Boolean.FALSE.equals(withinOperatingHours)
                    ? "outside_business_hours"
                    : !calendarLive
                        ? "calendar_temporarily_unavailable"
                : slots.isEmpty()
                    ? "calendar_fully_booked"
                    : requestedTime.isPresent() && matchingSlot.isEmpty()
                        ? "requested_time_unavailable"
                        : requestedTime.isPresent()
                            ? "requested_time_available"
                            : "slots_available");
        result.put("spokenResponse", AvailabilitySpeechRenderer.render(call, result));
        return Map.copyOf(result);
    }

    private Map<String, Object> missingDate(Call call, ZoneId timezone) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "needs_date");
        result.put("timezone", timezone.toString());
        result.put("calendarLive", true);
        result.put("spokenResponse", AvailabilitySpeechRenderer.render(call, result));
        return Map.copyOf(result);
    }

    private List<Map<String, String>> relevantSlots(
            List<com.sauti.calendar.CalendarAvailabilitySlot> slots,
            Optional<LocalTime> requestedTime
    ) {
        var ordered = slots.stream();
        if (requestedTime.isPresent()) {
            var preferred = requestedTime.get();
            ordered = ordered.sorted(Comparator.comparingLong(slot -> Math.abs(
                    ChronoUnit.MINUTES.between(preferred, slot.start().toLocalTime())
            )));
        }
        return ordered.limit(12).map(this::slotMap).toList();
    }

    private Map<String, String> slotMap(com.sauti.calendar.CalendarAvailabilitySlot slot) {
        return Map.of(
                "start", slot.start().toString(),
                "end", slot.end().toString(),
                "displayString", slot.displayString()
        );
    }

    private List<Map<String, String>> nextOpenBusinessWindows(
            String effectiveHours,
            LocalDate requestedDate,
            ZoneId timezone
    ) {
        var windows = new java.util.ArrayList<Map<String, String>>();
        for (int offset = 1; offset <= 14 && windows.size() < 3; offset++) {
            var date = requestedDate.plusDays(offset);
            for (var range : OperatingHoursSchedule.rangesFor(effectiveHours, date, timezone)) {
                windows.add(Map.of(
                        "date", date.toString(),
                        "opens", range.start().toLocalTime().toString(),
                        "closes", range.end().toLocalTime().toString()
                ));
                if (windows.size() == 3) break;
            }
        }
        return List.copyOf(windows);
    }

    private Optional<LocalTime> parseRequestedTime(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        var normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replace(".", "")
                .trim();
        if (normalized.matches(".*\\b(midi|noon)\\b.*")) return Optional.of(LocalTime.NOON);
        if (normalized.matches(".*\\b(minuit|midnight)\\b.*")) return Optional.of(LocalTime.MIDNIGHT);
        var matcher = SPOKEN_TIME.matcher(normalized);
        if (!matcher.find()) return Optional.empty();
        try {
            var hour = Integer.parseInt(matcher.group(1));
            var minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            var meridiem = matcher.group(3);
            if (meridiem != null) {
                if (hour < 1 || hour > 12) return Optional.empty();
                if ("pm".equalsIgnoreCase(meridiem) && hour < 12) hour += 12;
                if ("am".equalsIgnoreCase(meridiem) && hour == 12) hour = 0;
            }
            return Optional.of(LocalTime.of(hour, minute));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> bookSlot(Call call, LlmToolCall toolCall, AgentTool toolConfig) {
        var selectedProvider = call.getAgent().getCalendarProvider();
        if ("Google Calendar".equalsIgnoreCase(selectedProvider)
                && (!"google".equalsIgnoreCase(toolConfig.getCalendarType())
                    || toolConfig.getCalendarCredentialId() == null)) {
            throw new IllegalStateException("Google Calendar is selected but the booking tool is not connected");
        }
        var provider = calendarProviderFactory.forTool(toolConfig, call.getTenant().getId());
        var booking = bookingService.create(
                call.getTenant().getId(),
                new CreateBookingRequest(
                        call.getAgent().getId(),
                        call.getId(),
                        requiredStringArg(toolCall.arguments(), "caller_name"),
                        stringArg(toolCall.arguments(), "caller_phone", call.getCallerNumber()),
                        requiredStringArg(toolCall.arguments(), "service_type"),
                        OffsetDateTime.parse(requiredStringArg(toolCall.arguments(), "appointment_at")),
                        intArg(toolCall.arguments(), "duration_minutes", 60)
                ),
                provider
        );
        var externalEventId = booking.getExternalEventId() == null ? "" : booking.getExternalEventId();
        if ("Google Calendar".equalsIgnoreCase(selectedProvider)
                && (externalEventId.isBlank() || externalEventId.startsWith("local-"))) {
            throw new IllegalStateException("Google Calendar did not confirm the event");
        }
        var confirmationCode = confirmationCode(booking.getId());
        return Map.of(
                "bookingId", booking.getId().toString(),
                "externalEventId", externalEventId,
                "confirmationCode", confirmationCode,
                "spokenResponse", BookingSpeechRenderer.render(call, booking, confirmationCode)
        );
    }

    private Map<String, Object> reschedule(Call call, LlmToolCall toolCall) {
        var booking = bookingService.reschedule(call.getTenant().getId(),
                UUID.fromString(requiredStringArg(toolCall.arguments(), "booking_id")),
                new com.sauti.calendar.BookingDtos.RescheduleBookingRequest(
                        OffsetDateTime.parse(requiredStringArg(toolCall.arguments(), "appointment_at")),
                        intArg(toolCall.arguments(), "duration_minutes", 60)));
        return Map.of("bookingId", booking.getId(), "appointmentAt", booking.getAppointmentAt().toString(),
                "updated", true);
    }

    private Map<String, Object> cancel(Call call, LlmToolCall toolCall) {
        var booking = bookingService.cancel(call.getTenant().getId(),
                UUID.fromString(requiredStringArg(toolCall.arguments(), "booking_id")));
        return Map.of("bookingId", booking.getId(), "cancelled", true);
    }

    private String stringArg(Map<String, Object> arguments, String name, String defaultValue) {
        var value = arguments.get(name);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private String requiredStringArg(Map<String, Object> arguments, String name) {
        var value = arguments.get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required tool argument: " + name);
        }
        return value.toString();
    }

    private int intArg(Map<String, Object> arguments, String name, int defaultValue) {
        var value = arguments.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String confirmationCode(UUID bookingId) {
        var value = Long.toUnsignedString(bookingId.getMostSignificantBits() ^ bookingId.getLeastSignificantBits(), 36).toUpperCase();
        var normalized = value.length() >= 8 ? value.substring(0, 8) : "00000000".substring(value.length()) + value;
        return "SAT-" + normalized;
    }
}
