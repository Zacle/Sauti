package com.sauti.tool;

import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.calendar.BookingIdentityService;
import com.sauti.calendar.BookingService;
import com.sauti.calendar.BookingSlotUnavailableException;
import com.sauti.agent.OperatingHoursSchedule;
import com.sauti.call.Call;
import com.sauti.call.CallIntakeNoteService;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.session.BookingDraft;
import com.sauti.session.CallSessionStore;
import com.sauti.session.ConversationState;
import com.sauti.session.VerifiedBookingIdentity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class SautiCalendarFulfillment implements ToolFulfillment {
    private static final Logger LOGGER = LoggerFactory.getLogger(SautiCalendarFulfillment.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper CAPTURED_DATA_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private final CalendarProviderFactory calendarProviderFactory;
    private final BookingService bookingService;
    private final BookingIdentityService bookingIdentityService;
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
        this(calendarProviderFactory, bookingService, callSessionStore, intakeNotes,
                new BookingIdentityService(bookingService));
    }

    @Autowired
    public SautiCalendarFulfillment(
            CalendarProviderFactory calendarProviderFactory,
            BookingService bookingService,
            CallSessionStore callSessionStore,
            CallIntakeNoteService intakeNotes,
            BookingIdentityService bookingIdentityService
    ) {
        this.calendarProviderFactory = calendarProviderFactory;
        this.bookingService = bookingService;
        this.callSessionStore = callSessionStore;
        this.intakeNotes = intakeNotes;
        this.bookingIdentityService = bookingIdentityService;
    }

    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        try {
            return switch (toolCall.name()) {
                case "get_business_hours" -> LlmToolResult.success(toolCall, businessHours(call));
                case "check_availability" -> LlmToolResult.success(toolCall, checkAvailability(call, toolCall.arguments(), toolConfig));
                case "lookup_booking" -> LlmToolResult.success(toolCall, lookupBooking(call, toolCall));
                case "book_slot" -> LlmToolResult.success(toolCall, bookSlot(call, toolCall));
                case "reschedule_booking" -> LlmToolResult.success(toolCall, reschedule(call, toolCall));
                case "cancel_booking" -> LlmToolResult.success(toolCall, cancel(call, toolCall));
                case "update_booking" -> LlmToolResult.success(toolCall, updateBooking(call, toolCall));
                default -> LlmToolResult.error(toolCall, "Unrecognised calendar tool: " + toolCall.name());
            };
        } catch (BookingIdentityMismatchException exception) {
            resetBookingIdentity(call);
            return LlmToolResult.success(toolCall, bookingIdentityMismatch(toolCall));
        } catch (BookingIdentityAmbiguousException exception) {
            return LlmToolResult.success(toolCall, bookingIdentityAmbiguous(toolCall));
        } catch (BookingIdentityReferenceRequiredException exception) {
            return LlmToolResult.success(toolCall, bookingIdentityReferenceRequired(toolCall));
        } catch (BookingIdentitySuffixAmbiguousException exception) {
            return LlmToolResult.success(toolCall, bookingIdentitySuffixAmbiguous(toolCall));
        } catch (BookingSlotUnavailableException exception) {
            return LlmToolResult.success(toolCall, Map.of(
                    "status", "slot_no_longer_available",
                    "bookingCreated", false,
                    "responseMode", "render_slot_no_longer_available",
                    "instruction", "Explain briefly in the caller's current language that the reviewed slot became "
                            + "unavailable before it could be saved. Treat this as an availability change and "
                            + "do not claim a booking exists. Ask for one alternative date or time, then check live "
                            + "availability again before preparing a new review."
            ));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Calendar tool failed callId={} tool={} exception={}",
                    call == null ? "unknown" : call.getId(),
                    toolCall.name(),
                    exception.getClass().getSimpleName(),
                    exception
            );
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
        var duration = intArg(
                arguments,
                "duration_minutes",
                call.getAgent().getDefaultBookingDurationMinutes()
        );
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
            result.put("responseMode", "render_availability_result");
            result.put("instruction", "Explain the authoritative availability result concisely in the caller's "
                    + "current language. Interpret status and the structured date, time, slot, timezone, and business "
                    + "hours fields using the caller's natural locale conventions. Never translate, infer, or alter "
                    + "stored customer values. Ask at most one necessary follow-up question.");
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
        result.put("responseMode", "request_booking_date");
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

    private Map<String, Object> bookSlot(Call call, LlmToolCall toolCall) {
        var modelReviewToken = stringArg(toolCall.arguments(), "review_token", "");
        var reviewAction = stringArg(toolCall.arguments(), "review_action", "");
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
        var explicitReviewAction = java.util.Set.of(
                "prepare_review", "correct_review", "approve_review"
        ).contains(reviewAction);
        // A model-selected review_action is conversational intent, not server
        // authorization. Once a review exists, the latest caller turn must have
        // passed through ConversationStateTool so compound turns (for example
        // approval plus a price question) cannot save by choosing approve_review
        // directly. The state tool still chains a clean approval straight back
        // here with server-owned arguments, so the normal path adds no model turn.
        var callerApprovedReview = semanticState && "approved".equals(reviewDecision);
        var explicitActionMatchesState = switch (reviewAction) {
            case "approve_review" -> callerApprovedReview;
            case "correct_review" -> semanticState && "corrected".equals(reviewDecision);
            case "prepare_review" -> false;
            default -> semanticState && !reviewDecision.isBlank();
        };
        var providerReviewTransition = ToolActionPolicy.verifiedReviewTransition(toolCall);
        callerApprovedReview = callerApprovedReview
                || (providerReviewTransition && "approve_review".equals(reviewAction));
        if (!suppliedReviewToken.isBlank()
                && !providerReviewTransition
                && (!semanticState || reviewDecision.isBlank()
                    || (explicitReviewAction && !explicitActionMatchesState))) {
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
            // Approval normally restores the signed review rather than trusting
            // model-authored arguments. Phone values accepted by the semantic
            // state tool are caller-authoritative, however: that tool only
            // stores a phone after an unambiguous complete capture. Prefer that
            // language-neutral state so a spoken correction cannot be replaced
            // by the preceding reviewed value. Digit transcripts remain a
            // compatibility fallback for calls created before semantic capture.
            var authoritativePhone = currentState.getOrDefault("caller_phone", "");
            if (!authoritativePhone.isBlank()) {
                arguments.put("caller_phone", authoritativePhone);
            } else {
                phoneCandidate(latestCaller, 7).ifPresent(value ->
                        arguments.put("caller_phone", value)
                );
            }
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
        var review = BookingReviewBuilder.build(call, arguments, customerDetails, suppliedReviewToken);
        if (!secureEquals(review.token(), suppliedReviewToken)) {
            rememberBookingReview(call, arguments, review.token());
            var result = new LinkedHashMap<String, Object>();
            result.put("status", "booking_review_required");
            result.put("bookingCreated", false);
            result.put("reviewToken", review.token());
            result.put("bookingReview", review.fields());
            result.put("correctionReview", review.correction());
            result.put("changedFields", review.changedFields());
            result.put("responseMode", review.correction()
                    ? "render_booking_correction_review"
                    : "render_booking_full_review");
            result.put("instruction", "Render bookingReview exactly once as concise natural speech in the caller's "
                    + "current language, then stop and wait. Use the caller's language and locale conventions to express "
                    + "labels, date, time, duration, phone digits, email punctuation, and the confirmation question. "
                    + "Preserve every structured value exactly, read the stored name naturally without a phonetic alphabet, "
                    + "and never expose reviewToken. For a full review, ask whether all details are correct or what must change. "
                    + "For a correction review, mention only changedFields and ask whether the corrected values are now right. "
                    + "On a correction, keep the preceding reviewToken, change only the corrected value, "
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
                        intArg(
                                arguments,
                                "duration_minutes",
                                call.getAgent().getDefaultBookingDurationMinutes()
                        ),
                        customerDetails
                )
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
        result.put("appointmentAt", inBusinessTimezone(call, booking.getAppointmentAt()).toString());
        result.put("externalEventId", externalEventId);
        result.put("calendarSynced", calendarSynced);
        result.put("externalCalendarConfigured", !localOnly);
        result.put("ownerNotified", true);
        result.put("responseMode", "render_booking_success");
        result.put("instruction", localOnly
                ? "In the caller's current language, confirm that the booking was saved in Sauti. State the structured "
                    + "appointment date and time using the caller's locale conventions, provide bookingNumber exactly, "
                    + "and explain briefly that it should be kept for later changes. Do not claim an external calendar was updated."
                : "In the caller's current language, confirm that the booking was saved in Sauti. State the structured "
                    + "appointment date and time using the caller's locale conventions, provide bookingNumber exactly, "
                    + "and explain briefly that it should be kept for later changes. External calendar synchronization "
                    + "happens afterward; never make it a condition of the booking or describe the Sauti booking as failed.");
        if (call.getTwilioCallSid() != null && !call.getTwilioCallSid().isBlank()) {
            callSessionStore.updatePendingBooking(call.getTwilioCallSid(), null);
        }
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
                intArg(
                        arguments,
                        "duration_minutes",
                        call.getAgent().getDefaultBookingDurationMinutes()
                )
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
        // This describes workflow intent and must never become part of the
        // signed booking-data snapshot.
        normalized.remove("review_action");
        normalized.remove("question_handling");
        normalized.remove("confirmation_state");
        normalized.remove(ToolActionPolicy.VERIFIED_REVIEW_TRANSITION);
        var suppliedAppointmentName = normalized.remove("appointment_name");
        if (suppliedAppointmentName != null && !suppliedAppointmentName.toString().isBlank()) {
            normalized.put("caller_name", suppliedAppointmentName.toString());
        }
        BookingReviewBuilder.reviewedValue(call, suppliedReviewToken, "caller_phone")
                .ifPresent(value -> normalized.put("caller_phone", value));
        try {
            var notes = intakeNotes.notes(call, latest);
            var semanticState = notes.containsKey("conversation_state_revision");
            var appointmentName = notes.get("appointment_name");
            var callerName = notes.get("caller_name");
            var otherPerson = "other".equals(notes.get("booking_subject"))
                    ? notes.getOrDefault("recipient_relation", "other person")
                    : notes.get("booking_for_relation");
            if (semanticState && ((appointmentName != null && !appointmentName.isBlank())
                    || (callerName != null && !callerName.isBlank())
                    || (otherPerson != null && !otherPerson.isBlank()))) {
                // Once multilingual semantic state exists, it is the only
                // authority for identity. Never let a later book_slot argument
                // replace the structured entity with the caller's raw utterance.
                normalized.remove("caller_name");
            }
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
        phoneCandidate(latest, suppliedReviewToken.isBlank() ? 9 : 4)
                .ifPresent(value -> normalized.put("caller_phone", value));
        return Map.copyOf(normalized);
    }

    private Optional<String> phoneCandidate(String transcript, int minimumLength) {
        if (transcript == null || transcript.isBlank()) return Optional.empty();
        var matcher = SPOKEN_DIGIT_SEQUENCE.matcher(transcript);
        String candidate = null;
        while (matcher.find()) {
            var digits = matcher.group(1).replaceAll("\\D", "");
            if (digits.length() >= minimumLength && digits.length() <= 15) {
                candidate = matcher.group(1).trim().startsWith("+") ? "+" + digits : digits;
            }
        }
        return Optional.ofNullable(candidate);
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
            BookingReviewBuilder.reviewedValue(call, reviewToken, key)
                    .ifPresent(value -> arguments.put(key, value));
        }
        var details = new LinkedHashMap<>(customerDetails(arguments));
        var topLevel = java.util.Set.of(
                "caller_name", "caller_phone", "caller_email", "service_type", "appointment_at"
        );
        call.getAgent().getBookingRequiredFields().stream()
                .filter(field -> !topLevel.contains(field))
                .forEach(field -> BookingReviewBuilder.reviewedValue(call, reviewToken, "detail." + field)
                        .ifPresent(value -> details.put(field, value)));
        if (!details.isEmpty()) arguments.put("customer_details", Map.copyOf(details));
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
        var existing = verifiedBooking(call, toolCall.arguments());
        var bookingNumber = existing.getBookingReference();
        var booking = bookingService.reschedule(call.getTenant().getId(), existing.getId(),
                new com.sauti.calendar.BookingDtos.RescheduleBookingRequest(
                        OffsetDateTime.parse(requiredStringArg(toolCall.arguments(), "appointment_at")),
                        intArg(
                                toolCall.arguments(),
                                "duration_minutes",
                                existing.getDurationMinutes()
                        )));
        return Map.of(
                "status", "booking_rescheduled",
                "bookingId", booking.getId(),
                "bookingNumber", booking.getBookingReference() == null
                        ? bookingNumber : booking.getBookingReference(),
                "appointmentAt", inBusinessTimezone(call, booking.getAppointmentAt()).toString(),
                "updated", true,
                "instruction", "Tell the caller in their current language that the booking was rescheduled, "
                        + "using only bookingNumber and appointmentAt from this result. Do not invent another reference or time."
        );
    }

    private Map<String, Object> cancel(Call call, LlmToolCall toolCall) {
        var existing = verifiedBooking(call, toolCall.arguments());
        var bookingNumber = existing.getBookingReference();
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

    private Map<String, Object> lookupBooking(Call call, LlmToolCall toolCall) {
        var booking = lookupVerifiedBooking(call, toolCall.arguments());
        rememberVerifiedBookingIdentity(call, booking);
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "booking_found");
        result.put("bookingFound", true);
        result.put("bookingNumber", booking.getBookingReference());
        result.put("bookingStatus", booking.getStatus());
        result.put("appointmentName", booking.getCallerName());
        result.put("serviceType", booking.getServiceType());
        result.put("appointmentAt", inBusinessTimezone(call, booking.getAppointmentAt()).toString());
        result.put("durationMinutes", booking.getDurationMinutes());
        if ("cancel".equals(stringArg(toolCall.arguments(), "requested_action", ""))) {
            result.put("nextTool", "cancel_booking");
            result.put("nextToolAuthorized", true);
            result.put("nextToolArguments", Map.of(
                    "question_handling", "ready_for_action",
                    "confirmation_state", "not_confirmed"
            ));
            result.put(
                    "instruction",
                    "The booking was verified. Sauti will now retain the exact cancellation proposal before any "
                            + "review is spoken. Follow the chained cancellation result; do not ask an additional "
                            + "confirmation from this lookup result."
            );
        } else {
            result.put(
                    "instruction",
                    "Tell the caller in their current language that the booking was found. Do not read the booking "
                            + "number unless the caller explicitly asks for it. Use only bookingNumber, bookingStatus, "
                            + "appointmentName, serviceType, appointmentAt, and durationMinutes from this result. "
                            + "Do not disclose contact details."
            );
        }
        return Map.copyOf(result);
    }

    private Map<String, Object> updateBooking(Call call, LlmToolCall toolCall) {
        var arguments = toolCall.arguments();
        var existing = verifiedBooking(call, arguments);
        var details = new LinkedHashMap<String, Object>(capturedData(existing.getCapturedData()));
        mapArg(arguments, "customer_details").forEach(details::put);
        details.values().removeIf(java.util.Objects::isNull);
        var updated = bookingService.update(
                call.getTenant().getId(),
                existing.getId(),
                new com.sauti.calendar.BookingDtos.UpdateBookingRequest(
                        stringArg(arguments, "appointment_name", existing.getCallerName()),
                        stringArg(arguments, "new_caller_phone", existing.getCallerPhone()),
                        arguments.containsKey("caller_email")
                                ? nullableStringArg(arguments, "caller_email")
                                : existing.getCallerEmail(),
                        stringArg(arguments, "service_type", existing.getServiceType()),
                        existing.getAppointmentAt(),
                        existing.getDurationMinutes(),
                        Map.copyOf(details)
                )
        );
        return Map.of(
                "status", "booking_updated",
                "bookingNumber", updated.getBookingReference(),
                "bookingStatus", updated.getStatus(),
                "appointmentName", updated.getCallerName(),
                "serviceType", updated.getServiceType(),
                "appointmentAt", inBusinessTimezone(call, updated.getAppointmentAt()).toString(),
                "durationMinutes", updated.getDurationMinutes(),
                "updated", true,
                "instruction", "Tell the caller in their current language that the requested booking details "
                        + "were updated. Use only the factual fields in this result. Do not disclose contact details. "
                        + "For date or time changes, use the separate reschedule workflow."
        );
    }

    private com.sauti.calendar.Booking verifiedBooking(
            Call call,
            Map<String, Object> arguments
    ) {
        var retained = callSessionStore.verifiedBookingIdentity(call.getTwilioCallSid());
        if (retained.isPresent()) {
            var identity = retained.orElseThrow();
            if (!call.getTenant().getId().equals(identity.tenantId())) {
                throw new BookingIdentityMismatchException();
            }
            try {
                return bookingService.getForAgent(
                        identity.tenantId(), call.getAgent().getId(), identity.bookingId()
                );
            } catch (RuntimeException exception) {
                callSessionStore.updateVerifiedBookingIdentity(call.getTwilioCallSid(), null);
                throw new BookingIdentityMismatchException();
            }
        }
        // Compatibility for calls that began before private identity retention
        // was introduced. New tool schemas never ask the model for these fields.
        return lookupVerifiedBooking(call, arguments);
    }

    private com.sauti.calendar.Booking lookupVerifiedBooking(
            Call call,
            Map<String, Object> arguments
    ) {
        var suppliedPhone = requiredStringArg(arguments, "caller_phone");
        var bookingNumber = stringArg(arguments, "booking_number", "");
        var bookingReferenceSuffix = stringArg(arguments, "booking_reference_suffix", "");
        final LocalDate date;
        final LocalTime time;
        final ZoneId timezone;
        try {
            date = bookingNumber.isBlank() && bookingReferenceSuffix.isBlank()
                    ? LocalDate.parse(requiredStringArg(arguments, "booking_date")) : null;
            var suppliedTime = stringArg(arguments, "booking_time", "");
            time = suppliedTime.isBlank() ? null : LocalTime.parse(suppliedTime);
            timezone = ZoneId.of(call.getAgent().getTimezone());
        } catch (RuntimeException exception) {
            throw new BookingIdentityMismatchException();
        }
        var result = bookingIdentityService.verify(new BookingIdentityService.Request(
                call.getTenant().getId(),
                call.getAgent().getId(),
                suppliedPhone,
                bookingNumber,
                bookingReferenceSuffix,
                date,
                time,
                timezone
        ));
        return switch (result.status()) {
            case VERIFIED -> result.booking();
            case TIME_REQUIRED -> throw new BookingIdentityAmbiguousException();
            case REFERENCE_REQUIRED -> throw new BookingIdentityReferenceRequiredException();
            case REFERENCE_SUFFIX_AMBIGUOUS -> throw new BookingIdentitySuffixAmbiguousException();
            case MISMATCH -> throw new BookingIdentityMismatchException();
        };
    }

    private Map<String, Object> bookingIdentityMismatch(LlmToolCall toolCall) {
        var bookingNumber = stringArg(toolCall.arguments(), "booking_number", "");
        var bookingReferenceSuffix = stringArg(toolCall.arguments(), "booking_reference_suffix", "");
        if (bookingNumber.isBlank()) {
            var result = new LinkedHashMap<String, Object>();
            result.put("status", "booking_not_found");
            result.put("bookingFound", false);
            result.put("actionPerformed", false);
            result.put("nextAction", "reply");
            result.put("requestedAction", requestedAction(toolCall));
            switch (toolCall.name()) {
                case "cancel_booking" -> result.put("cancelled", false);
                case "update_booking", "reschedule_booking" -> result.put("updated", false);
                default -> {
                }
            }
            if (bookingReferenceSuffix.isBlank()) {
                result.put("retryRecommended", true);
                result.put("retryField", "booking_reference_suffix");
                result.put(
                        "instruction",
                        noMutationInstruction(toolCall) + " Say once in the caller's current language that no booking "
                                + "matched the supplied details. Do not identify a wrong field, reveal stored data, or "
                                + "ask for the phone/date/time again. Ask one short question for only the final four "
                                + "letters or digits shown at the end of the booking confirmation. Keep the verified "
                                + "phone already in private state and run lookup_booking once when those four characters "
                                + "are supplied. Do not ask for the complete reference."
                );
            } else {
                result.put("retryRecommended", false);
                result.put(
                        "instruction",
                        noMutationInstruction(toolCall) + " Say briefly in the caller's current language that the "
                                + "booking still could not be located with the final four confirmation characters. "
                                + "Do not ask for any identity value again or start another lookup. Suggest checking "
                                + "the confirmation or contacting the business, then stop and wait."
                );
            }
            return Map.copyOf(result);
        }
        var captured = bookingNumber;
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "booking_identity_mismatch");
        result.put("bookingFound", false);
        result.put("actionPerformed", false);
        result.put("retryField", "booking_number");
        result.put("capturedBookingNumber", captured);
        result.put("bookingNumberReadback", bookingNumberReadback(captured));
        switch (toolCall.name()) {
            case "cancel_booking" -> result.put("cancelled", false);
            case "update_booking", "reschedule_booking" -> result.put("updated", false);
            default -> {
            }
        }
        result.put(
                "instruction",
                noMutationInstruction(toolCall) + " Tell the caller in their current "
                        + "language that the booking number and phone number could not be matched together. "
                        + "Read bookingNumberReadback back one character at a time, including the dash, and ask "
                        + "the caller to repeat or correct the booking number only. Do not say whether that booking "
                        + "number exists. Keep the caller-provided phone already held in private call state and run "
                        + "lookup_booking after the caller provides the corrected complete booking number."
        );
        return Map.copyOf(result);
    }

    private Map<String, Object> bookingIdentityAmbiguous(LlmToolCall toolCall) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "booking_identity_ambiguous");
        result.put("bookingFound", false);
        result.put("actionPerformed", false);
        result.put("retryField", "booking_time");
        switch (toolCall.name()) {
            case "cancel_booking" -> result.put("cancelled", false);
            case "update_booking", "reschedule_booking" -> result.put("updated", false);
            default -> {
            }
        }
        result.put(
                "instruction",
                "No booking details were disclosed and no booking was changed. To finish verification, ask only for "
                        + "the exact appointment time in the caller's current language, then retry lookup_booking with "
                        + "the same phone and date plus the supplied booking_time. Do not say how many "
                        + "possible bookings exist and do not reveal candidate times or names."
        );
        return Map.copyOf(result);
    }

    private Map<String, Object> bookingIdentityReferenceRequired(LlmToolCall toolCall) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "booking_identity_reference_required");
        result.put("bookingFound", false);
        result.put("actionPerformed", false);
        result.put("retryField", "booking_number");
        result.put(
                "instruction",
                "No booking details were disclosed and no booking was changed. More than one booking has the same "
                        + "verified phone, date, and time. Ask for the complete Sauti booking reference shown in the "
                        + "customer's email, then retry lookup_booking with that reference and the same phone. "
                        + "Do not reveal how many bookings matched or any stored booking details."
        );
        return Map.copyOf(result);
    }

    private Map<String, Object> bookingIdentitySuffixAmbiguous(LlmToolCall toolCall) {
        return Map.of(
                "status", "booking_reference_suffix_ambiguous",
                "bookingFound", false,
                "actionPerformed", false,
                "retryRecommended", false,
                "nextAction", "reply",
                "requestedAction", requestedAction(toolCall),
                "instruction", noMutationInstruction(toolCall) + " Explain briefly in the caller's current language "
                        + "that the final four confirmation characters are not enough to identify one booking safely. "
                        + "Do not disclose any booking data, ask for a full reference, or retry automatically. Suggest "
                        + "contacting the business for help, then stop and wait."
        );
    }

    private String noMutationInstruction(LlmToolCall toolCall) {
        return switch (requestedAction(toolCall)) {
            case "cancel" -> "Reassure the caller naturally that no booking was cancelled.";
            case "reschedule" -> "Reassure the caller naturally that their appointment has not been moved.";
            case "update" -> "Reassure the caller naturally that no booking details were updated.";
            default -> "Be clear that the lookup did not find a booking.";
        };
    }

    private String requestedAction(LlmToolCall toolCall) {
        var requested = stringArg(toolCall.arguments(), "requested_action", "lookup");
        if (java.util.Set.of("lookup", "update", "reschedule", "cancel").contains(requested)) return requested;
        return switch (toolCall.name()) {
            case "cancel_booking" -> "cancel";
            case "reschedule_booking" -> "reschedule";
            case "update_booking" -> "update";
            default -> "lookup";
        };
    }

    private List<String> bookingNumberReadback(String value) {
        if (value == null || value.isBlank()) return List.of();
        return value.trim().toUpperCase(java.util.Locale.ROOT).chars()
                .filter(character -> !Character.isWhitespace(character))
                .mapToObj(character -> Character.toString((char) character))
                .toList();
    }

    private void resetBookingIdentity(Call call) {
        try {
            var existing = callSessionStore.conversationState(call.getTwilioCallSid())
                    .orElse(ConversationState.empty());
            var values = new LinkedHashMap<>(existing.values());
            values.remove("booking_number");
            values.remove("booking_reference_suffix");
            values.remove("booking_date");
            values.remove("booking_lookup_name");
            values.remove("booking_time");
            values.remove("review_decision");
            callSessionStore.updateConversationState(
                    call.getTwilioCallSid(),
                    new ConversationState(
                            Map.copyOf(values),
                            existing.bookingSubject(),
                            existing.bookingIntent(),
                            existing.revision() + 1
                    )
            );
            callSessionStore.updatePendingAction(call.getTwilioCallSid(), null);
            callSessionStore.updateVerifiedBookingIdentity(call.getTwilioCallSid(), null);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not reset booking identity after a mismatch sautiCallId={} exception={}",
                    call.getId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void rememberVerifiedBookingIdentity(
            Call call,
            com.sauti.calendar.Booking booking
    ) {
        try {
            var existing = callSessionStore.conversationState(call.getTwilioCallSid())
                    .orElse(ConversationState.empty());
            var values = new LinkedHashMap<>(existing.values());
            values.remove("booking_number");
            values.remove("review_decision");
            callSessionStore.updateConversationState(
                    call.getTwilioCallSid(),
                    new ConversationState(
                            Map.copyOf(values),
                            existing.bookingSubject(),
                            existing.bookingIntent(),
                            existing.revision() + 1
                    )
            );
            callSessionStore.updateVerifiedBookingIdentity(
                    call.getTwilioCallSid(),
                    new VerifiedBookingIdentity(
                            call.getTenant().getId(),
                            booking.getId(),
                            booking.getBookingReference(),
                            BookingIdentityService.normalizePhone(booking.getCallerPhone()),
                            OffsetDateTime.now()
                    )
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not retain verified booking identity sautiCallId={} exception={}",
                    call.getId(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private static final class BookingIdentityMismatchException extends RuntimeException {
    }

    private static final class BookingIdentityAmbiguousException extends RuntimeException {
    }

    private static final class BookingIdentityReferenceRequiredException extends RuntimeException {
    }

    private static final class BookingIdentitySuffixAmbiguousException extends RuntimeException {
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapArg(Map<String, Object> arguments, String name) {
        var value = arguments.get(name);
        if (!(value instanceof Map<?, ?> values)) return Map.of();
        var result = new LinkedHashMap<String, Object>();
        values.forEach((key, nested) -> {
            if (key != null && nested != null) result.put(key.toString(), nested);
        });
        return Map.copyOf(result);
    }

    private String nullableStringArg(Map<String, Object> arguments, String name) {
        var value = arguments.get(name);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private Map<String, Object> capturedData(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return CAPTURED_DATA_MAPPER.readValue(
                    value,
                    new com.fasterxml.jackson.core.type.TypeReference<>() { }
            );
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String stringArg(Map<String, Object> arguments, String name, String defaultValue) {
        var value = arguments.get(name);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private OffsetDateTime inBusinessTimezone(Call call, OffsetDateTime value) {
        if (value == null || call == null || call.getAgent() == null) return value;
        try {
            return value.atZoneSameInstant(ZoneId.of(call.getAgent().getTimezone())).toOffsetDateTime();
        } catch (RuntimeException ignored) {
            return value;
        }
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
