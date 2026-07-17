package com.sauti.tool;

import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.calendar.BookingService;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SautiCalendarFulfillment implements ToolFulfillment {
    private final CalendarProviderFactory calendarProviderFactory;
    private final BookingService bookingService;

    public SautiCalendarFulfillment(CalendarProviderFactory calendarProviderFactory, BookingService bookingService) {
        this.calendarProviderFactory = calendarProviderFactory;
        this.bookingService = bookingService;
    }

    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        try {
            var provider = calendarProviderFactory.forTool(toolConfig);
            return switch (toolCall.name()) {
                case "check_availability" -> LlmToolResult.success(toolCall, checkAvailability(call, toolCall.arguments(), provider));
                case "book_slot" -> LlmToolResult.success(toolCall, bookSlot(call, toolCall, provider));
                case "reschedule_booking" -> LlmToolResult.success(toolCall, reschedule(call, toolCall));
                case "cancel_booking" -> LlmToolResult.success(toolCall, cancel(call, toolCall));
                default -> LlmToolResult.error(toolCall, "Unrecognised calendar tool: " + toolCall.name());
            };
        } catch (RuntimeException exception) {
            return LlmToolResult.error(toolCall, exception.getMessage());
        }
    }

    private Map<String, Object> checkAvailability(Call call, Map<String, Object> arguments, com.sauti.calendar.CalendarProvider provider) {
        var timezone = ZoneId.of(stringArg(arguments, "timezone", call.getAgent().getTimezone()));
        var date = LocalDate.parse(requiredStringArg(arguments, "date"));
        var duration = intArg(arguments, "duration_minutes", 60);
        var slots = provider.availability(call.getAgent(), date, duration, timezone)
                .stream()
                .map(slot -> Map.of(
                        "start", slot.start().toString(),
                        "end", slot.end().toString(),
                        "displayString", slot.displayString()
                ))
                .toList();
        return Map.of(
                "slots", slots,
                "timezone", timezone.toString(),
                "durationMinutes", duration
        );
    }

    private Map<String, Object> bookSlot(Call call, LlmToolCall toolCall, com.sauti.calendar.CalendarProvider provider) {
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
        return Map.of(
                "bookingId", booking.getId().toString(),
                "externalEventId", booking.getExternalEventId() == null ? "" : booking.getExternalEventId(),
                "confirmationCode", confirmationCode(booking.getId())
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
