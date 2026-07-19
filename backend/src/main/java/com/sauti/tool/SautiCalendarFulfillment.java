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
import java.text.Normalizer;
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
                    "missingFields", missingFields,
                    "instruction", "Ask for only the next missing field in the caller's language before booking."
            );
        }
        var unconfirmedFields = unconfirmedIdentityFields(toolCall.arguments());
        if (!unconfirmedFields.isEmpty()) {
            return Map.of(
                    "status", "identity_confirmation_required",
                    "bookingCreated", false,
                    "unconfirmedFields", unconfirmedFields,
                    "agentReadback", identityReadback(toolCall.arguments(), unconfirmedFields),
                    "bookingReview", bookingReview(toolCall.arguments(), customerDetails),
                    "instruction", "All required booking information is now present. Perform one consolidated final review using agentReadback and bookingReview, then ask whether everything is correct. Do not confirm fields separately or earlier in the call. Never ask the caller to use NATO phonetics, spell character by character, or dictate the phone one digit at a time. The caller only confirms or corrects your complete review."
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

    private boolean booleanArg(Map<String, Object> arguments, String name) {
        var value = arguments.get(name);
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(value.toString());
    }

    private List<String> unconfirmedIdentityFields(Map<String, Object> arguments) {
        var fields = new java.util.ArrayList<String>();
        if (!booleanArg(arguments, "final_booking_review_confirmed")) {
            fields.add("caller_name");
            fields.add("caller_phone");
            var email = stringArg(arguments, "caller_email", "");
            if (!email.isBlank()) fields.add("caller_email");
            return List.copyOf(fields);
        }
        if (!booleanArg(arguments, "caller_name_spelling_confirmed")) {
            fields.add("caller_name");
        }
        if (!booleanArg(arguments, "caller_phone_digits_confirmed")) {
            fields.add("caller_phone");
        }
        var email = stringArg(arguments, "caller_email", "");
        if (!email.isBlank() && !booleanArg(arguments, "caller_email_spelling_confirmed")) {
            fields.add("caller_email");
        }
        return List.copyOf(fields);
    }

    private Map<String, String> identityReadback(Map<String, Object> arguments, List<String> fields) {
        var readback = new LinkedHashMap<String, String>();
        if (fields.contains("caller_name")) {
            readback.put("caller_name", natoReadback(stringArg(arguments, "caller_name", "")));
        }
        if (fields.contains("caller_phone")) {
            readback.put("caller_phone", phoneReadback(stringArg(arguments, "caller_phone", "")));
        }
        if (fields.contains("caller_email")) {
            readback.put("caller_email", natoReadback(stringArg(arguments, "caller_email", "")));
        }
        return Map.copyOf(readback);
    }

    private Map<String, Object> bookingReview(Map<String, Object> arguments, Map<String, Object> customerDetails) {
        var review = new LinkedHashMap<String, Object>();
        review.put("service", stringArg(arguments, "service_type", ""));
        review.put("appointmentAt", stringArg(arguments, "appointment_at", ""));
        review.put("durationMinutes", intArg(arguments, "duration_minutes", 60));
        if (!customerDetails.isEmpty()) review.put("customerDetails", Map.copyOf(customerDetails));
        return Map.copyOf(review);
    }

    private String phoneReadback(String value) {
        return value.codePoints()
                .filter(codePoint -> Character.isDigit(codePoint) || codePoint == '+')
                .mapToObj(codePoint -> codePoint == '+' ? "plus" : new String(Character.toChars(codePoint)))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String natoReadback(String value) {
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        var words = new java.util.ArrayList<String>();
        normalized.codePoints().forEach(codePoint -> {
            if (Character.getType(codePoint) == Character.NON_SPACING_MARK) return;
            var character = Character.toUpperCase((char) codePoint);
            var nato = natoWord(character);
            if (nato != null) words.add(nato);
            else if (Character.isDigit(codePoint)) words.add(new String(Character.toChars(codePoint)));
            else switch (codePoint) {
                case '@' -> words.add("at sign");
                case '.' -> words.add("dot");
                case '-' -> words.add("hyphen");
                case '_' -> words.add("underscore");
                case '\'' -> words.add("apostrophe");
                case ' ' -> words.add("space");
                default -> words.add(new String(Character.toChars(codePoint)));
            }
        });
        return String.join(", ", words);
    }

    private String natoWord(char character) {
        return switch (character) {
            case 'A' -> "Alfa";
            case 'B' -> "Bravo";
            case 'C' -> "Charlie";
            case 'D' -> "Delta";
            case 'E' -> "Echo";
            case 'F' -> "Foxtrot";
            case 'G' -> "Golf";
            case 'H' -> "Hotel";
            case 'I' -> "India";
            case 'J' -> "Juliett";
            case 'K' -> "Kilo";
            case 'L' -> "Lima";
            case 'M' -> "Mike";
            case 'N' -> "November";
            case 'O' -> "Oscar";
            case 'P' -> "Papa";
            case 'Q' -> "Quebec";
            case 'R' -> "Romeo";
            case 'S' -> "Sierra";
            case 'T' -> "Tango";
            case 'U' -> "Uniform";
            case 'V' -> "Victor";
            case 'W' -> "Whiskey";
            case 'X' -> "X-ray";
            case 'Y' -> "Yankee";
            case 'Z' -> "Zulu";
            default -> null;
        };
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
