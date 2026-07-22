package com.sauti.tool;

import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.calendar.BookingService;
import com.sauti.agent.OperatingHoursSchedule;
import com.sauti.call.Call;
import com.sauti.call.CallIntakeNoteService;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.session.BookingDraft;
import com.sauti.session.CallSessionStore;
import com.sauti.session.ConversationState;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SautiCalendarFulfillment implements ToolFulfillment {
    private static final Logger LOGGER = LoggerFactory.getLogger(SautiCalendarFulfillment.class);
    private static final String BOOKING_REFERENCE_GUIDANCE = "Give one brief, natural, professional sentence "
            + "in the caller's current language telling them to keep the booking number just provided and to give "
            + "that number when calling back to change, reschedule, or cancel the booking. Refer to it as the booking "
            + "number; do not repeat, alter, or invent the number, add new booking facts, ask a question, or call a tool.";
    private final CalendarProviderFactory calendarProviderFactory;
    private final BookingService bookingService;
    private final CallSessionStore callSessionStore;
    private final CallIntakeNoteService intakeNotes;
    private static final Pattern SPOKEN_DIGIT_SEQUENCE = Pattern.compile(
            "(?<!\\d)(\\+?\\d(?:[\\s().-]*\\d){3,14})(?!\\d)"
    );

    public SautiCalendarFulfillment(
            CalendarProviderFactory calendarProviderFactory,
            BookingService bookingService,
            CallSessionStore callSessionStore,
            CallIntakeNoteService intakeNotes
    ) {
        this.calendarProviderFactory = calendarProviderFactory;
        this.bookingService = bookingService;
        this.callSessionStore = callSessionStore;
        this.intakeNotes = intakeNotes;
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
                availableSlots = bookingService.excludeLocalConflicts(
                        call.getTenant().getId(), call.getAgent().getId(), date, timezone, availableSlots
                );
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
        var status = !businessOpen
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
                            : "slots_available";
        result.put("status", status);
        var bookingArguments = "requested_time_available".equals(status)
                ? rememberVerifiedSlot(call, date, requestedTime.orElseThrow(), matchingSlot.orElseThrow(), duration)
                : clearInvalidVerifiedSlot(call, date, requestedTime);
        if (bookingArguments.isPresent()) {
            result.put("nextTool", "book_slot");
            result.put("nextToolAuthorized", true);
            result.put("nextToolArguments", bookingArguments.get());
            result.put("instruction", "The requested time is available and the caller has an active booking intake. "
                    + "Call book_slot immediately without speaking, asking permission, or asking the caller to wait. "
                    + "The booking tool will validate missing fields and produce the exact review.");
        } else {
            // Availability is already an authoritative calendar decision. Return
            // caller-ready speech with the tool result so Realtime does not need
            // a second model response merely to paraphrase that decision.
            result.put("spokenResponse", AvailabilitySpeechRenderer.render(call, result));
        }
        return Map.copyOf(result);
    }

    private Optional<Map<String, Object>> rememberVerifiedSlot(
            Call call,
            LocalDate date,
            LocalTime requestedTime,
            com.sauti.calendar.CalendarAvailabilitySlot slot,
            int durationMinutes
    ) {
        var latestCaller = latestCallerTranscript(call);
        var notes = intakeNotes.notes(call, latestCaller);
        if (!ConversationState.INTENT_ACTIVE.equals(notes.get("booking_intent"))
                || !stateStillMatchesRequest(notes, date, Optional.of(requestedTime))) {
            return Optional.empty();
        }
        var draft = new BookingDraft(
                notes.getOrDefault("appointment_name", notes.getOrDefault("caller_name", "")),
                notes.getOrDefault("service_type", ""),
                date.toString(),
                slot.start().toString(),
                notes.getOrDefault("caller_phone", ""),
                true,
                "",
                durationMinutes
        );
        if (call.getTwilioCallSid() != null && !call.getTwilioCallSid().isBlank()) {
            callSessionStore.updatePendingBooking(call.getTwilioCallSid(), draft);
        }
        var verifiedNotes = new LinkedHashMap<>(notes);
        verifiedNotes.put("preferred_day", date.toString());
        verifiedNotes.put("preferred_time", requestedTime.toString());
        return BookingToolArgumentResolver.resolve(call, Map.copyOf(verifiedNotes), draft);
    }

    private Optional<Map<String, Object>> clearInvalidVerifiedSlot(
            Call call,
            LocalDate date,
            Optional<LocalTime> requestedTime
    ) {
        var latestCaller = latestCallerTranscript(call);
        var notes = intakeNotes.notes(call, latestCaller);
        if (ConversationState.INTENT_ACTIVE.equals(notes.get("booking_intent"))
                && stateStillMatchesRequest(notes, date, requestedTime)
                && call.getTwilioCallSid() != null && !call.getTwilioCallSid().isBlank()) {
            callSessionStore.updatePendingBooking(call.getTwilioCallSid(), null);
        }
        return Optional.empty();
    }

    private boolean stateStillMatchesRequest(
            Map<String, String> notes,
            LocalDate date,
            Optional<LocalTime> requestedTime
    ) {
        // The semantic state may advance while a slow calendar call is still in
        // flight. A stale result must never verify or clear the newer turn's slot.
        if (!notes.containsKey("conversation_state_revision")) return true;
        if (!date.toString().equals(notes.get("preferred_day"))) return false;
        return requestedTime
                .map(time -> time.toString().equals(notes.get("preferred_time")))
                .orElse(true);
    }

    private Map<String, Object> missingDate(Call call, ZoneId timezone) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "needs_date");
        result.put("timezone", timezone.toString());
        result.put("calendarLive", true);
        result.put("instruction", "Ask the caller for a specific preferred date in their current language.");
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
        try {
            return Optional.of(LocalTime.parse(raw.trim()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> bookSlot(Call call, LlmToolCall toolCall, AgentTool toolConfig) {
        var modelReviewToken = stringArg(toolCall.arguments(), "review_token", "");
        var storedReviewToken = pendingReviewToken(call).orElse("");
        // The review token is private server workflow state, not customer data.
        // Realtime models can omit or replay an older copy after an interruption;
        // never let that turn an already-spoken review into a brand-new review.
        var suppliedReviewToken = storedReviewToken.isBlank() ? modelReviewToken : storedReviewToken;
        var latestCaller = latestCallerTranscript(call);
        var currentState = intakeNotes.notes(call, latestCaller);
        if ("paused".equals(currentState.get("booking_intent"))) {
            return Map.of(
                    "status", "booking_paused_by_caller",
                    "bookingCreated", false,
                    "instruction", "Do not save anything. Briefly confirm in the caller's current language that no booking was made, then close warmly."
            );
        }
        var semanticState = currentState.containsKey("conversation_state_revision");
        var reviewDecision = currentState.getOrDefault("review_decision", "");
        var callerApprovedReview = semanticState
                ? "approved".equals(reviewDecision)
                : callerApprovedLatestReview(latestCaller);
        if (!suppliedReviewToken.isBlank() && semanticState && reviewDecision.isBlank()) {
            return Map.of(
                    "status", "booking_review_decision_required",
                    "bookingCreated", false,
                    "nextAction", "use_business_tool",
                    "nextTool", ConversationStateTool.NAME,
                    "nextToolAuthorized", true,
                    "instruction", "The server restored the private token for the review already spoken. "
                            + "Interpret the caller's latest response with update_conversation_state before any booking action. "
                            + "Do not repeat the review or speak while that internal step runs."
            );
        }
        var arguments = new LinkedHashMap<>(normalizeBookingArgumentsFromConversation(
                call, toolCall.arguments(), suppliedReviewToken, latestCaller
        ));
        if (!suppliedReviewToken.isBlank() && callerApprovedReview) {
            restoreReviewedValues(call, suppliedReviewToken, arguments);
        }
        var customerDetails = customerDetails(arguments);
        var missingFields = missingRequiredBookingFields(call, arguments, customerDetails);
        if (!missingFields.isEmpty()) {
            return Map.of(
                    "status", "missing_required_information",
                    "bookingCreated", false,
                    "nextMissingField", exposedBookingField(missingFields.get(0)),
                    "remainingMissingFieldCount", missingFields.size(),
                    "instruction", "Ask for exactly nextMissingField in the caller's language. Do not mention, list, or request any other missing field in the same reply."
            );
        }
        var appointmentAt = normalizedAppointmentAt(call, stringArg(arguments, "appointment_at", ""));
        if (appointmentAt.isEmpty()) {
            return Map.of(
                    "status", "invalid_booking_information",
                    "bookingCreated", false,
                    "nextInvalidField", "appointment_at",
                    "instruction", "The appointment date or time was not a valid calendar value. Ask only for the date and time again. Do not claim that the calendar or booking provider failed."
            );
        }
        arguments.put("appointment_at", appointmentAt.get().toString());
        var review = BookingReviewRenderer.render(call, arguments, customerDetails, suppliedReviewToken);
        if (!secureEquals(review.token(), suppliedReviewToken)) {
            rememberBookingReview(call, arguments, review.token());
            var result = new LinkedHashMap<String, Object>();
            result.put("status", "booking_review_required");
            result.put("bookingCreated", false);
            result.put("reviewToken", review.token());
            result.put("bookingReview", review.fields());
            result.put("spokenResponse", review.spokenResponse());
            result.put("correctionReview", review.correction());
            result.put("changedFields", review.changedFields());
            result.put("instruction", "Speak spokenResponse exactly once, then stop and wait for the caller. "
                    + "Never expose reviewToken. On a correction, keep the preceding reviewToken, change only the corrected value, "
                    + "and call book_slot with that preceding token so the server confirms only the changed field. "
                    + "After the caller approves the latest review, call book_slot with unchanged values and the latest exact reviewToken.");
            return Map.copyOf(result);
        }
        if (!callerApprovedReview) {
            return Map.of(
                    "status", "booking_confirmation_required",
                    "bookingCreated", false,
                    "instruction", "Do not save or repeat the full review. Answer the caller's question if they asked one; otherwise ask them briefly to correct any wrong detail or say that the review is correct. Call book_slot again only after their next clear approval or correction."
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
                        requiredStringArg(arguments, "caller_name"),
                        stringArg(arguments, "caller_phone", call.getCallerNumber()),
                        stringArg(arguments, "caller_email", ""),
                        requiredStringArg(arguments, "service_type"),
                        OffsetDateTime.parse(requiredStringArg(arguments, "appointment_at")),
                        intArg(arguments, "duration_minutes", 60),
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
        result.put("spokenResponse", BookingSpeechRenderer.render(
                call, booking, booking.getBookingReference()
        ));
        result.put("callerGuidanceInstruction", BOOKING_REFERENCE_GUIDANCE);
        result.put("instruction", localOnly
                ? "Tell the caller the booking was saved in Sauti and provide the booking number. Do not claim an external calendar was updated."
                : "Tell the caller whether the external calendar was confirmed. Always provide the booking number. If calendarSynced is false, say the booking was saved in Sauti for owner follow-up.");
        return Map.copyOf(result);
    }

    private void rememberBookingReview(Call call, Map<String, Object> arguments, String reviewToken) {
        if (call.getTwilioCallSid() == null || call.getTwilioCallSid().isBlank()) return;
        callSessionStore.updatePendingBooking(call.getTwilioCallSid(), new BookingDraft(
                stringArg(arguments, "caller_name", ""),
                stringArg(arguments, "service_type", ""),
                "",
                stringArg(arguments, "appointment_at", ""),
                stringArg(arguments, "caller_phone", ""),
                true,
                reviewToken,
                intArg(arguments, "duration_minutes", 60)
        ));
    }

    private Optional<String> pendingReviewToken(Call call) {
        if (call.getTwilioCallSid() == null || call.getTwilioCallSid().isBlank()) return Optional.empty();
        try {
            return callSessionStore.pendingBooking(call.getTwilioCallSid())
                    .map(BookingDraft::reviewToken)
                    .map(String::trim)
                    .filter(token -> !token.isBlank());
        } catch (RuntimeException exception) {
            LOGGER.debug("Pending booking review unavailable for callId={}", call.getId());
            return Optional.empty();
        }
    }

    private Map<String, Object> normalizeBookingArgumentsFromConversation(
            Call call,
            Map<String, Object> originalArguments,
            String suppliedReviewToken,
            String latest
    ) {
        var normalized = new LinkedHashMap<String, Object>(originalArguments);
        var suppliedAppointmentName = normalized.remove("appointment_name");
        if (suppliedAppointmentName != null && !suppliedAppointmentName.toString().isBlank()) {
            normalized.put("caller_name", suppliedAppointmentName.toString());
        }
        BookingReviewRenderer.reviewedValue(call, suppliedReviewToken, "caller_phone")
                .ifPresent(value -> normalized.put("caller_phone", value));
        try {
            var notes = intakeNotes.notes(call, latest);
            var appointmentName = notes.get("appointment_name");
            var otherPerson = "other".equals(notes.get("booking_subject"))
                    ? notes.getOrDefault("recipient_relation", "other person")
                    : notes.get("booking_for_relation");
            if (appointmentName != null && !appointmentName.isBlank()) {
                normalized.put("caller_name", appointmentName);
            } else if (otherPerson != null && !otherPerson.isBlank()) {
                // A model must not silently put the person speaking on an
                // appointment that the conversation says is for someone else.
                normalized.remove("caller_name");
            } else if (suppliedReviewToken.isBlank()) {
                copyAuthoritativeNote(notes, normalized, "caller_name");
            }
            if (suppliedReviewToken.isBlank()) {
                copyAuthoritativeNote(notes, normalized, "caller_email");
                copyAuthoritativeNote(notes, normalized, "caller_phone");
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Authoritative intake notes unavailable for callId={}", call.getId());
        }
        if (latest.isBlank()) return Map.copyOf(normalized);
        var matcher = SPOKEN_DIGIT_SEQUENCE.matcher(latest);
        String candidate = null;
        while (matcher.find()) {
            var digits = matcher.group(1).replaceAll("\\D", "");
            var minimumLength = suppliedReviewToken.isBlank() ? 9 : 4;
            if (digits.length() >= minimumLength && digits.length() <= 15) {
                candidate = matcher.group(1).trim().startsWith("+") ? "+" + digits : digits;
            }
        }
        if (candidate == null) return Map.copyOf(normalized);
        normalized.put("caller_phone", candidate);
        return Map.copyOf(normalized);
    }

    private Optional<OffsetDateTime> normalizedAppointmentAt(Call call, String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(raw.trim()));
        } catch (RuntimeException ignored) {
            try {
                var timezone = ZoneId.of(call.getAgent().getTimezone());
                return Optional.of(LocalDateTime.parse(raw.trim()).atZone(timezone).toOffsetDateTime());
            } catch (RuntimeException invalidLocalDateTime) {
                return Optional.empty();
            }
        }
    }

    private void restoreReviewedValues(
            Call call,
            String reviewToken,
            Map<String, Object> arguments
    ) {
        for (var key : List.of(
                "caller_name", "caller_phone", "caller_email", "service_type", "appointment_at", "duration_minutes"
        )) {
            BookingReviewRenderer.reviewedValue(call, reviewToken, key)
                    .ifPresent(value -> arguments.put(key, value));
        }
        var details = new LinkedHashMap<>(customerDetails(arguments));
        var topLevel = java.util.Set.of(
                "caller_name", "caller_phone", "caller_email", "service_type", "appointment_at"
        );
        call.getAgent().getBookingRequiredFields().stream()
                .filter(field -> !topLevel.contains(field))
                .forEach(field -> BookingReviewRenderer.reviewedValue(call, reviewToken, "detail." + field)
                        .ifPresent(value -> details.put(field, value)));
        if (!details.isEmpty()) arguments.put("customer_details", Map.copyOf(details));
    }

    private boolean callerApprovedLatestReview(String transcript) {
        var normalized = Normalizer.normalize(transcript == null ? "" : transcript, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("[^\\p{L}\\p{N}' ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) return false;
        if (normalized.matches(".*\\b(no corrections?|nothing (?:is )?wrong|everything is (?:right|correct)|"
                + "hakuna marekebisho|kila kitu kiko sawa)\\b.*")) {
            return true;
        }
        if (normalized.matches(".*\\b(but|actually|instead|change|correct that|correction|wrong|incorrect|not right|"
                + "pas correct|modifier|changer|lakini|badilisha|si sahihi|لكن|خطأ|غير صحيح|غيره)\\b.*")) {
            return false;
        }
        if (normalized.matches("^(?:yes|yeah|yep|oui|ndiyo|ndio|نعم)\\b.*")) return true;
        return normalized.matches(
                "(?:(?:yes|yeah|yep)(?: it is| (?:that is|that's|everything is) (?:right|correct|fine))?|"
                        + "correct|confirmed|(?:that is|that's|it is) (?:right|correct|fine|okay)|right|sounds (?:right|good)|"
                        + "looks good|all good|okay|ok|go ahead|save it|book it|please (?:save|book) it|"
                        + "oui|c'est correct|c'est exact|exact|d'accord|parfait|"
                        + "ndiyo|ndio|sahihi|sawa|iko sawa|endelea|hifadhi|weka miadi|"
                        + "نعم|صحيح|تمام|موافق|احجز|احفظ)"
        );
    }

    private void copyAuthoritativeNote(
            Map<String, String> notes,
            Map<String, Object> arguments,
            String key
    ) {
        var value = notes.get(key);
        if (value != null && !value.isBlank()) arguments.put(key, value);
    }

    private String exposedBookingField(String field) {
        return "caller_name".equals(field) ? "appointment_name" : field;
    }

    private String latestCallerTranscript(Call call) {
        try {
            var history = callSessionStore.conversationHistory(call.getTwilioCallSid());
            if (history == null) return "";
            for (int index = history.size() - 1; index >= 0; index--) {
                var message = history.get(index);
                if ("user".equals(message.role()) && message.content() != null && !message.content().isBlank()) {
                    return message.content();
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("Caller transcript unavailable while normalizing booking details callId={}", call.getId());
        }
        return "";
    }

    private boolean secureEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private Map<String, Object> reschedule(Call call, LlmToolCall toolCall) {
        var bookingNumber = requiredStringArg(toolCall.arguments(), "booking_number");
        var existing = bookingService.resolve(call.getTenant().getId(), bookingNumber);
        var booking = bookingService.reschedule(call.getTenant().getId(), existing.getId(),
                new com.sauti.calendar.BookingDtos.RescheduleBookingRequest(
                        OffsetDateTime.parse(requiredStringArg(toolCall.arguments(), "appointment_at")),
                        intArg(toolCall.arguments(), "duration_minutes", 60)));
        return Map.of(
                "status", "booking_rescheduled",
                "bookingId", booking.getId(),
                "bookingNumber", booking.getBookingReference() == null
                        ? bookingNumber : booking.getBookingReference(),
                "appointmentAt", booking.getAppointmentAt().toString(),
                "updated", true,
                "instruction", "Tell the caller in their current language that the booking was rescheduled, "
                        + "using only bookingNumber and appointmentAt from this result. Do not invent another reference or time."
        );
    }

    private Map<String, Object> cancel(Call call, LlmToolCall toolCall) {
        var bookingNumber = requiredStringArg(toolCall.arguments(), "booking_number");
        var existing = bookingService.resolve(call.getTenant().getId(), bookingNumber);
        var booking = bookingService.cancel(call.getTenant().getId(), existing.getId());
        return Map.of(
                "status", "booking_cancelled",
                "bookingId", booking.getId(),
                "bookingNumber", booking.getBookingReference() == null
                        ? bookingNumber : booking.getBookingReference(),
                "cancelled", true,
                "instruction", "Tell the caller in their current language that bookingNumber was cancelled. "
                        + "Do not claim that any other booking was changed."
        );
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
