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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SautiCalendarFulfillment implements ToolFulfillment {
    private static final Logger LOGGER = LoggerFactory.getLogger(SautiCalendarFulfillment.class);
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
                case "get_business_hours" -> LlmToolResult.success(toolCall, businessHours(call));
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

    private Map<String, Object> businessHours(Call call) {
        var effectiveHours = OperatingHoursSchedule.effective(call.getAgent());
        return Map.of(
                "status", "business_hours",
                "timezone", call.getAgent().getTimezone(),
                "schedule", OperatingHoursSchedule.describe(effectiveHours),
                "instruction", "Answer the caller in their language using only this configured schedule."
        );
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
        return Map.copyOf(result);
    }

    private Map<String, Object> missingDate(Call call, ZoneId timezone) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "needs_date");
        result.put("timezone", timezone.toString());
        result.put("calendarLive", true);
        result.put("instruction", "Ask the caller for a specific preferred date in their current language.");
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
        try {
            return Optional.of(LocalTime.parse(raw.trim()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> bookSlot(Call call, LlmToolCall toolCall, AgentTool toolConfig) {
        var customerDetails = customerDetails(toolCall.arguments());
        var missingFields = missingRequiredBookingFields(call, toolCall.arguments(), customerDetails);
        if (!missingFields.isEmpty()) {
            return Map.of(
                    "status", "missing_required_information",
                    "bookingCreated", false,
                    "nextMissingField", missingFields.get(0),
                    "remainingMissingFieldCount", missingFields.size(),
                    "instruction", "Ask for exactly nextMissingField in the caller's language. Do not mention, list, or request any other missing field in the same reply."
            );
        }
        var selectedProvider = call.getAgent().getCalendarProvider();
        com.sauti.calendar.CalendarProvider provider = null;
        try {
            if (!("Google Calendar".equalsIgnoreCase(selectedProvider)
                    && (!"google".equalsIgnoreCase(toolConfig.getCalendarType())
                        || toolConfig.getCalendarCredentialId() == null))) {
                provider = calendarProviderFactory.forTool(toolConfig, call.getTenant().getId());
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Booking calendar resolution failed callId={} agentId={}: {}",
                    call.getId(), call.getAgent().getId(), exception.getMessage());
        }
        var booking = bookingService.create(
                call.getTenant().getId(),
                new CreateBookingRequest(
                        call.getAgent().getId(),
                        call.getId(),
                        requiredStringArg(toolCall.arguments(), "caller_name"),
                        stringArg(toolCall.arguments(), "caller_phone", call.getCallerNumber()),
                        stringArg(toolCall.arguments(), "caller_email", ""),
                        requiredStringArg(toolCall.arguments(), "service_type"),
                        OffsetDateTime.parse(requiredStringArg(toolCall.arguments(), "appointment_at")),
                        intArg(toolCall.arguments(), "duration_minutes", 60),
                        customerDetails
                ),
                provider
        );
        var externalEventId = booking.getExternalEventId() == null ? "" : booking.getExternalEventId();
        var calendarStatus = booking.getCalendarSyncStatus();
        var calendarSynced = "synced".equals(calendarStatus);
        var localOnly = "not_configured".equals(calendarStatus);
        var result = new LinkedHashMap<String, Object>();
        result.put("status", calendarSynced
                ? "booking_confirmed"
                : localOnly ? "booking_saved_locally" : "booking_saved_pending_calendar");
        result.put("bookingCreated", true);
        result.put("bookingId", booking.getId().toString());
        result.put("bookingNumber", booking.getBookingReference());
        result.put("appointmentAt", booking.getAppointmentAt().toString());
        result.put("externalEventId", externalEventId);
        result.put("calendarSynced", calendarSynced);
        result.put("externalCalendarConfigured", !localOnly);
        result.put("ownerNotified", true);
        result.put("instruction", localOnly
                ? "Tell the caller the booking was saved in Sauti and provide the booking number. Do not claim an external calendar was updated."
                : "Tell the caller whether the external calendar was confirmed. Always provide the booking number. If calendarSynced is false, say the booking was saved in Sauti for owner follow-up.");
        return Map.copyOf(result);
    }

    private Map<String, Object> reschedule(Call call, LlmToolCall toolCall) {
        var existing = bookingService.resolve(call.getTenant().getId(), requiredStringArg(toolCall.arguments(), "booking_number"));
        var booking = bookingService.reschedule(call.getTenant().getId(), existing.getId(),
                new com.sauti.calendar.BookingDtos.RescheduleBookingRequest(
                        OffsetDateTime.parse(requiredStringArg(toolCall.arguments(), "appointment_at")),
                        intArg(toolCall.arguments(), "duration_minutes", 60)));
        return Map.of("bookingId", booking.getId(), "appointmentAt", booking.getAppointmentAt().toString(),
                "updated", true);
    }

    private Map<String, Object> cancel(Call call, LlmToolCall toolCall) {
        var existing = bookingService.resolve(call.getTenant().getId(), requiredStringArg(toolCall.arguments(), "booking_number"));
        var booking = bookingService.cancel(call.getTenant().getId(), existing.getId());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> customerDetails(Map<String, Object> arguments) {
        var value = arguments.get("customer_details");
        return value instanceof Map<?, ?> map
                ? map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(), Map.Entry::getValue,
                        (first, ignored) -> first, LinkedHashMap::new
                ))
                : Map.of();
    }

    private List<String> missingRequiredBookingFields(
            Call call,
            Map<String, Object> arguments,
            Map<String, Object> customerDetails
    ) {
        return call.getAgent().getBookingRequiredFields().stream()
                .filter(field -> {
                    var value = switch (field) {
                        case "caller_name", "caller_phone", "caller_email", "service_type", "appointment_at" -> arguments.get(field);
                        default -> customerDetails.get(field);
                    };
                    return value == null || value.toString().isBlank();
                })
                .toList();
    }
}
