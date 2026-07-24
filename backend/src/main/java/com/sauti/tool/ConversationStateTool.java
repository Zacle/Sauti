package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.call.VoiceOutputGuard;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolDefinition;
import com.sauti.llm.LlmToolResult;
import com.sauti.session.CallSessionStore;
import com.sauti.session.BookingDraft;
import com.sauti.session.ConversationState;
import com.sauti.session.PendingAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Internal semantic boundary between multilingual model understanding and
 * deterministic conversation state. This tool never performs a business side effect.
 */
@Service
public class ConversationStateTool {
    public static final String NAME = "update_conversation_state";
    private static final Set<String> COMMON_FIELDS = Set.of(
            "caller_name", "appointment_name", "recipient_relation", "service_type",
            "caller_phone", "new_caller_phone", "caller_email",
            "booking_number", "preferred_day", "preferred_time",
            "review_decision"
    );
    private static final Set<String> SUBJECTS = Set.of(
            "unchanged", ConversationState.SUBJECT_UNKNOWN,
            ConversationState.SUBJECT_SELF, ConversationState.SUBJECT_OTHER
    );
    private static final Set<String> INTENTS = Set.of(
            "unchanged", ConversationState.INTENT_UNKNOWN, ConversationState.INTENT_INFORMATION,
            ConversationState.INTENT_ACTIVE, ConversationState.INTENT_PAUSED
    );
    private static final Set<String> NEXT_ACTIONS = Set.of("reply", "use_business_tool");
    private static final Set<String> TURN_UNDERSTANDING = Set.of("clear", "unclear");
    private static final Set<String> CALLER_QUESTION = Set.of(
            "none", "answered_in_spoken_response", "requires_business_tool"
    );
    private static final Set<String> ACTION_AUTHORIZATION = Set.of(
            "not_applicable", "unconditional", "blocked"
    );

    private final CallSessionStore sessions;
    private final AgentToolRepository agentTools;

    @Autowired
    public ConversationStateTool(CallSessionStore sessions, AgentToolRepository agentTools) {
        this.sessions = sessions;
        this.agentTools = agentTools;
    }

    public ConversationStateTool(CallSessionStore sessions) {
        this(sessions, null);
    }

    public static LlmToolDefinition definition() {
        var valueProperties = new LinkedHashMap<String, Object>();
        COMMON_FIELDS.forEach(field -> valueProperties.put(field, Map.of(
                "type", "string",
                "description", switch (field) {
                    case "caller_name" -> "Name of the person speaking, only when explicitly stated or corrected.";
                    case "appointment_name" -> "Name of the person receiving the service, only when explicitly stated or corrected.";
                    case "recipient_relation" -> "Relationship of an explicitly different recipient to the caller, expressed compactly.";
                    case "service_type" -> "Requested configured service, only when the meaning is clear.";
                    case "caller_phone" -> "Complete caller-provided phone number.";
                    case "new_caller_phone" -> "Replacement contact phone explicitly requested for an existing booking. Never overwrite caller_phone, which verifies the current booking.";
                    case "caller_email" -> "Complete caller-provided email address.";
                    case "booking_number" -> "Exact customer-facing booking number supplied for a lookup, update, reschedule, or cancellation.";
                    case "preferred_day" -> "Clearly understood appointment date normalized to yyyy-MM-dd using TODAY IN THE BUSINESS TIMEZONE. Omit when the date is unclear.";
                    case "preferred_time" -> "Clearly understood exact appointment time normalized to HH:mm, or a clear broad period such as morning or afternoon. Omit when the time is unclear.";
                    case "review_decision" -> "Meaning of the caller's latest response to the immediately preceding server-retained booking review or action confirmation: approved, corrected, rejected, or unclear. This is turn-scoped and never inferred from politeness alone.";
                    default -> "Explicitly provided conversation value.";
                }
        )));
        var updates = new LinkedHashMap<String, Object>();
        updates.put("type", "object");
        updates.put("description", "Only values explicitly and clearly stated or corrected in the latest caller turn. Omit unchanged or doubtful values. Never infer a person, service, date, time, or selection from a similar-sounding or incoherent fragment.");
        updates.put("properties", Map.copyOf(valueProperties));
        updates.put("additionalProperties", false);

        var details = new LinkedHashMap<String, Object>();
        details.put("type", "object");
        details.put("description", "Additional configured booking fields explicitly supplied in this turn, keyed by their configured field name.");
        details.put("additionalProperties", Map.of("type", "string"));

        var properties = new LinkedHashMap<String, Object>();
        properties.put("updates", Map.copyOf(updates));
        properties.put("additional_details", Map.copyOf(details));
        properties.put("clear_fields", Map.of(
                "type", "array",
                "description", "Previously collected common or configured booking fields the caller explicitly rejected or withdrew in this turn. Meaning, not wording, determines this list.",
                "items", Map.of("type", "string")
        ));
        properties.put("booking_subject", Map.of(
                "type", "string",
                "enum", List.of("unchanged", "unknown", "self", "other"),
                "description", "Whether the appointment is for the caller, explicitly for another person, still unknown, or unchanged. A corrected caller name alone never creates another person."
        ));
        properties.put("booking_intent", Map.of(
                "type", "string",
                "enum", List.of("unchanged", "unknown", "information_only", "active", "paused"),
                "description", "The caller's current booking intent based on meaning in context. paused means no booking action is authorized."
        ));
        properties.put("turn_understanding", Map.of(
                "type", "string",
                "enum", List.of("clear", "unclear"),
                "description", "Whether the latest accepted transcript is semantically coherent enough in context to support the proposed updates and action. Use unclear for gibberish, a noisy or unrelated fragment, or a reply that cannot distinguish one offered choice. Accents, imperfect grammar, and short answers remain clear when their meaning is evident. When unclear, provide one short repetition request in spoken_response and emit no updates, clears, subject/intent changes, or business tool."
        ));
        properties.put("spoken_response", Map.of(
                "type", "string",
                "description", "A concise, polite, natural reply in the caller's current language. Answer direct questions first. Leave empty only when a separate business tool must run before any reply. Never include tool syntax, JSON, headings, or private reasoning."
        ));
        properties.put("caller_question", Map.of(
                "type", "string",
                "enum", List.of("none", "answered_in_spoken_response", "requires_business_tool"),
                "description", "Turn-scoped status of an explicit customer question, condition, hesitation, or request for information that must be resolved before any side effect. Use answered_in_spoken_response only when spoken_response directly answers it from authoritative configured facts. Use requires_business_tool when a read-only lookup must run first. Use none for a clean answer, correction, or unconditional action confirmation with no separate unresolved request. An action request itself is not a customer question."
        ));
        properties.put("action_authorization", Map.of(
                "type", "string",
                "enum", List.of("not_applicable", "unconditional", "blocked"),
                "description", "Independent semantic safety judgment for the complete latest caller turn in any language. Use unconditional only when the caller clearly and consistently authorizes the exact pending side effect with no contradiction, rejection, correction, condition, hesitation, or separate request. Use blocked when any such conflict is present, even if the same turn also contains approval wording. Use not_applicable when this turn is not authorizing a side effect."
        ));
        properties.put("next_action", Map.of(
                "type", "string",
                "enum", List.of("reply", "use_business_tool"),
                "description", "reply when spoken_response fully answers this turn without a side effect or live lookup; use_business_tool when a configured tool must run before speaking, such as live availability or saving/changing a booking."
        ));
        properties.put("business_tool", Map.of(
                "type", "string",
                "description", "When next_action is use_business_tool, the exact name of the one available configured tool that must run next. Otherwise an empty string. Never use update_conversation_state here."
        ));
        return new LlmToolDefinition(
                NAME,
                "Required internal turn interpreter. Understand the latest caller turn semantically in any language or phrasing, compare it with authoritative state, emit only explicit state changes, and provide the natural caller-facing reply. Do not map by keywords. Corrections replace the affected value. Keep the speaker separate from a genuinely explicit third-party recipient. This tool records state only and never books, changes, or cancels anything.",
                Map.of(
                        "type", "object",
                        "properties", Map.copyOf(properties),
                        "required", List.of(
                                "updates", "additional_details", "clear_fields",
                                "booking_subject", "booking_intent", "turn_understanding",
                                "spoken_response", "caller_question", "action_authorization",
                                "next_action", "business_tool"
                        ),
                        "additionalProperties", false
                )
        );
    }

    public LlmToolResult execute(Call call, LlmToolCall toolCall) {
        try {
            var existing = sessions.conversationState(call.getTwilioCallSid())
                    .orElse(ConversationState.empty());
            var turnUnderstanding = choice(
                    toolCall.arguments().get("turn_understanding"), TURN_UNDERSTANDING, "clear"
            );
            if ("unclear".equals(turnUnderstanding)) {
                var preservedValues = new LinkedHashMap<>(existing.values());
                preservedValues.remove("review_decision");
                var preserved = new ConversationState(
                        preservedValues,
                        existing.bookingSubject(),
                        existing.bookingIntent(),
                        existing.revision() + 1
                );
                sessions.updateConversationState(call.getTwilioCallSid(), preserved);
                var result = new LinkedHashMap<String, Object>();
                result.put("status", "conversation_turn_unclear");
                result.put("state", preserved.asNotes());
                result.put("bookingAllowed", !ConversationState.INTENT_PAUSED.equals(preserved.bookingIntent()));
                result.put("nextAction", "reply");
                var spoken = VoiceOutputGuard.speechText(
                        stringArgument(toolCall.arguments(), "spoken_response")
                );
                if (!spoken.isBlank()) result.put("spokenResponse", spoken);
                result.put("instruction", "The unclear turn did not change booking state or authorize a business tool. Speak spokenResponse once and wait for a clearer caller reply.");
                return LlmToolResult.success(toolCall, Map.copyOf(result));
            }
            var previousBookingArguments = verifiedBookingArguments(call, existing);
            var pendingAction = pendingAction(call);
            var callerQuestion = choice(
                    toolCall.arguments().get("caller_question"), CALLER_QUESTION, "none"
            );
            var questionBlocksMutation = !"none".equals(callerQuestion);
            var actionAuthorization = choice(
                    toolCall.arguments().get("action_authorization"),
                    ACTION_AUTHORIZATION,
                    "not_applicable"
            );
            var next = reduce(call, existing, toolCall.arguments());
            var proposedReviewDecision = next.values().getOrDefault("review_decision", "");
            var approvalIsUnconditional = "approved".equals(proposedReviewDecision)
                    && "unconditional".equals(actionAuthorization)
                    && !questionBlocksMutation;
            if ((questionBlocksMutation
                    || ("approved".equals(proposedReviewDecision) && !approvalIsUnconditional))
                    && next.values().containsKey("review_decision")) {
                var valuesWithoutApproval = new LinkedHashMap<>(next.values());
                valuesWithoutApproval.remove("review_decision");
                next = new ConversationState(
                        valuesWithoutApproval,
                        next.bookingSubject(),
                        next.bookingIntent(),
                        next.revision()
                );
            }
            sessions.updateConversationState(call.getTwilioCallSid(), next);
            if (ConversationState.INTENT_PAUSED.equals(next.bookingIntent())
                    || "rejected".equals(next.values().getOrDefault("review_decision", ""))) {
                sessions.updatePendingAction(call.getTwilioCallSid(), null);
                pendingAction = Optional.empty();
            }

            var result = new LinkedHashMap<String, Object>();
            result.put("status", "conversation_state_updated");
            result.put("state", next.asNotes());
            result.put("bookingAllowed", !ConversationState.INTENT_PAUSED.equals(next.bookingIntent()));
            var turnUpdates = updates(toolCall.arguments().get("updates"));
            var clearedFields = clearFields(
                    toolCall.arguments().get("clear_fields"),
                    call.getAgent().getBookingRequiredFields() == null
                            ? Set.of()
                            : Set.copyOf(call.getAgent().getBookingRequiredFields())
            );
            var invalidatesVerifiedSlot = turnUpdates.containsKey("preferred_day")
                    || turnUpdates.containsKey("preferred_time")
                    || clearedFields.contains("preferred_day")
                    || clearedFields.contains("preferred_time")
                    || !ConversationState.INTENT_ACTIVE.equals(next.bookingIntent());
            if (invalidatesVerifiedSlot) {
                sessions.updatePendingBooking(call.getTwilioCallSid(), null);
            }
            var directBookingArguments = invalidatesVerifiedSlot
                    ? Optional.<Map<String, Object>>empty()
                    : verifiedBookingArguments(call, next);
            var reviewDecision = next.values().getOrDefault("review_decision", "");
            var approvedPendingAction = !questionBlocksMutation
                    && approvalIsUnconditional
                    && pendingAction.isPresent()
                    && next.revision() > pendingAction.orElseThrow().proposedAtRevision();
            var reviewMustContinue = !questionBlocksMutation
                    && !ConversationState.INTENT_PAUSED.equals(next.bookingIntent())
                    && (approvalIsUnconditional || "corrected".equals(reviewDecision))
                    && configuredFor(call, "book_slot");
            var bookingBecameReady = !questionBlocksMutation
                    && previousBookingArguments.isEmpty()
                    && directBookingArguments.isPresent()
                    && configuredFor(call, "book_slot");
            var availabilityMustContinue = !questionBlocksMutation
                    && ConversationState.INTENT_ACTIVE.equals(next.bookingIntent())
                    && (turnUpdates.containsKey("preferred_day") || turnUpdates.containsKey("preferred_time"))
                    && configuredFor(call, "check_availability");
            // Approval and correction of a server-generated booking review are
            // workflow transitions, not another conversational confirmation.
            // The model supplies the multilingual meaning; the server owns the
            // deterministic next action so "yes" cannot loop indefinitely.
            // A newly supplied or corrected booking date/time is likewise a
            // workflow transition: live availability must be checked before any
            // caller-facing claim, regardless of the caller's wording.
            var requestedBusinessTool = stringArgument(toolCall.arguments(), "business_tool");
            var questionTool = "requires_business_tool".equals(callerQuestion)
                    && !sideEffecting(call, requestedBusinessTool)
                    ? requestedBusinessTool : "";
            var nextAction = "answered_in_spoken_response".equals(callerQuestion)
                    ? "reply"
                    : "requires_business_tool".equals(callerQuestion)
                        ? (questionTool.isBlank() ? "reply" : "use_business_tool")
                        : approvedPendingAction || reviewMustContinue || availabilityMustContinue || bookingBecameReady
                            ? "use_business_tool"
                            : choice(toolCall.arguments().get("next_action"), NEXT_ACTIONS, "reply");
            var businessTool = !questionTool.isBlank()
                    ? questionTool
                    : approvedPendingAction
                        ? pendingAction.orElseThrow().toolName()
                        : availabilityMustContinue
                        ? "check_availability"
                        : reviewMustContinue || bookingBecameReady
                            ? "book_slot"
                            : requestedBusinessTool;
            var spoken = "reply".equals(nextAction)
                    ? VoiceOutputGuard.speechText(stringArgument(toolCall.arguments(), "spoken_response"))
                    : "";
            if (!spoken.isBlank()) result.put("spokenResponse", spoken);
            result.put("nextAction", nextAction);
            if ("use_business_tool".equals(nextAction)
                    && businessTool.matches("[A-Za-z][A-Za-z0-9_]{1,63}")
                    && !NAME.equals(businessTool)
                    && !(ConversationState.INTENT_PAUSED.equals(next.bookingIntent())
                        && Set.of(
                                "book_slot", "update_booking",
                                "reschedule_booking", "cancel_booking"
                        ).contains(businessTool))
                    && configuredFor(call, businessTool)) {
                result.put("nextTool", businessTool);
                result.put("nextToolAuthorized", true);
                if (approvedPendingAction) {
                    var arguments = new LinkedHashMap<String, Object>(
                            pendingAction.orElseThrow().arguments()
                    );
                    arguments.put("question_handling", "ready_for_action");
                    arguments.put("confirmation_state", "confirmed");
                    result.put("nextToolArguments", Map.copyOf(arguments));
                } else if ("lookup_booking".equals(businessTool)) {
                    bookingIdentityArguments(next).ifPresent(arguments ->
                            result.put("nextToolArguments", arguments)
                    );
                } else if ("get_business_hours".equals(businessTool)) {
                    // This read has no arguments. Supplying the authoritative
                    // empty object lets Realtime execute it directly instead of
                    // spending another model turn asking for the same tool.
                    result.put("nextToolArguments", Map.of());
                } else if ("check_availability".equals(businessTool)) {
                    var availabilityArguments = availabilityArguments(next);
                    if (!availabilityArguments.isEmpty()) {
                        result.put("nextToolArguments", availabilityArguments);
                    }
                } else if ("book_slot".equals(businessTool)) {
                    directBookingArguments.ifPresent(arguments ->
                            result.put("nextToolArguments", arguments)
                    );
                } else if ("reschedule_booking".equals(businessTool)) {
                    verifiedRescheduleArguments(call, next).ifPresent(arguments ->
                            result.put("nextToolArguments", arguments)
                    );
                } else if ("cancel_booking".equals(businessTool)) {
                    bookingIdentityArguments(next).ifPresent(identity -> {
                        var arguments = new LinkedHashMap<String, Object>(identity);
                        arguments.put("question_handling", "ready_for_action");
                        arguments.put("confirmation_state", "confirmed");
                        result.put("nextToolArguments", Map.copyOf(arguments));
                    });
                }
            }
            result.put("instruction", spoken.isBlank()
                    ? questionBlocksMutation
                        ? "No side effect is authorized on this turn. Answer the caller's explicit question or condition first, using a read-only business tool when authorized, then stop and wait for a fresh action decision."
                        : "State is updated. Continue with the appropriate configured business tool before speaking, or answer naturally if no business tool is needed."
                    : "The caller-facing reply has already been supplied exactly once. Do not generate another reply for this turn.");
            return LlmToolResult.success(toolCall, Map.copyOf(result));
        } catch (RuntimeException exception) {
            return LlmToolResult.error(toolCall, "Conversation state could not be updated");
        }
    }

    private Map<String, Object> availabilityArguments(ConversationState state) {
        var preferredDay = state.values().getOrDefault("preferred_day", "").trim();
        try {
            java.time.LocalDate.parse(preferredDay);
        } catch (java.time.format.DateTimeParseException exception) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("date", preferredDay);
        var preferredTime = state.values().getOrDefault("preferred_time", "").trim();
        if (!preferredTime.isBlank()) result.put("time_preference", preferredTime);
        return Map.copyOf(result);
    }

    private Optional<Map<String, Object>> verifiedBookingArguments(Call call, ConversationState state) {
        Optional<BookingDraft> pending;
        try {
            pending = sessions.pendingBooking(call.getTwilioCallSid());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (pending == null || pending.isEmpty()) return Optional.empty();
        return BookingToolArgumentResolver.resolve(call, state.asNotes(), pending.get());
    }

    private Optional<Map<String, Object>> verifiedRescheduleArguments(Call call, ConversationState state) {
        Optional<BookingDraft> pending;
        try {
            pending = sessions.pendingBooking(call.getTwilioCallSid());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (pending == null || pending.isEmpty()) return Optional.empty();
        return BookingToolArgumentResolver.resolveReschedule(call, state.asNotes(), pending.get())
                .flatMap(arguments -> bookingIdentityArguments(state).map(identity -> {
                    var secured = new LinkedHashMap<String, Object>(arguments);
                    secured.putAll(identity);
                    return Map.copyOf(secured);
                }));
    }

    private Optional<Map<String, Object>> bookingIdentityArguments(ConversationState state) {
        var bookingNumber = state.values().getOrDefault("booking_number", "").trim();
        var callerPhone = state.values().getOrDefault("caller_phone", "").trim();
        if (bookingNumber.isBlank() || callerPhone.isBlank()) return Optional.empty();
        return Optional.of(Map.of(
                "booking_number", bookingNumber,
                "caller_phone", callerPhone
        ));
    }

    private Optional<PendingAction> pendingAction(Call call) {
        try {
            var pending = sessions.pendingAction(call.getTwilioCallSid());
            return pending == null ? Optional.empty() : pending;
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private ConversationState reduce(Call call, ConversationState current, Map<String, Object> arguments) {
        var values = new LinkedHashMap<>(current.values());
        var allowedDetails = call.getAgent().getBookingRequiredFields() == null
                ? Set.<String>of()
                : Set.copyOf(call.getAgent().getBookingRequiredFields());
        var turnUpdates = updates(arguments.get("updates"));
        var detailUpdates = updates(arguments.get("additional_details"));
        // Review decisions authorize at most the current caller turn. They must
        // never leak into a later turn as stale approval.
        values.remove("review_decision");
        clearFields(arguments.get("clear_fields"), allowedDetails).forEach(values::remove);

        var subject = choice(arguments.get("booking_subject"), SUBJECTS, "unchanged");
        if ("unchanged".equals(subject)) subject = current.bookingSubject();
        var intent = choice(arguments.get("booking_intent"), INTENTS, "unchanged");
        if ("unchanged".equals(intent)) intent = current.bookingIntent();

        turnUpdates.forEach((key, value) -> {
            if (COMMON_FIELDS.contains(key) && !value.isBlank()) values.put(key, value);
        });
        detailUpdates.forEach((key, value) -> {
            if (allowedDetails.contains(key) && !value.isBlank()) values.put(key, value);
        });

        if (ConversationState.SUBJECT_SELF.equals(subject)) {
            values.remove("recipient_relation");
            var caller = values.get("caller_name");
            if (caller == null || caller.isBlank()) values.remove("appointment_name");
            else values.put("appointment_name", caller);
        } else if (ConversationState.SUBJECT_OTHER.equals(subject)) {
            var changedFromSelfOrUnknown = !ConversationState.SUBJECT_OTHER.equals(current.bookingSubject());
            var changedRecipientRelation = turnUpdates.containsKey("recipient_relation");
            if ((changedFromSelfOrUnknown || changedRecipientRelation)
                    && !turnUpdates.containsKey("appointment_name")) {
                // A previous self/name cannot silently become the name of a newly
                // introduced third-party recipient. Ask for that person's name.
                values.remove("appointment_name");
            }
        } else if (ConversationState.SUBJECT_UNKNOWN.equals(subject)) {
            values.remove("appointment_name");
            values.remove("recipient_relation");
        }
        return new ConversationState(values, subject, intent, current.revision() + 1);
    }

    private Map<String, String> updates(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        var result = new LinkedHashMap<String, String>();
        map.forEach((key, raw) -> {
            if (key == null || raw == null) return;
            var normalizedKey = key.toString().trim().toLowerCase(Locale.ROOT);
            var normalizedValue = raw.toString().trim();
            if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                result.put(normalizedKey, normalizedValue);
            }
        });
        return Map.copyOf(result);
    }

    private List<String> clearFields(Object value, Set<String> allowedDetails) {
        if (!(value instanceof List<?> list)) return List.of();
        var result = new ArrayList<String>();
        list.forEach(item -> {
            if (item == null) return;
            var field = item.toString().trim().toLowerCase(Locale.ROOT);
            if (COMMON_FIELDS.contains(field) || allowedDetails.contains(field)) result.add(field);
        });
        return List.copyOf(result);
    }

    private String choice(Object raw, Set<String> allowed, String fallback) {
        var value = raw == null ? "" : raw.toString().trim().toLowerCase(Locale.ROOT);
        return allowed.contains(value) ? value : fallback;
    }

    private String stringArgument(Map<String, Object> arguments, String key) {
        var value = arguments.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private boolean configuredFor(Call call, String toolName) {
        return agentTools == null || agentTools
                .findByAgent_IdAndToolNameAndIsActiveTrue(call.getAgent().getId(), toolName)
                .isPresent();
    }

    private boolean sideEffecting(Call call, String toolName) {
        if (toolName == null || toolName.isBlank()) return false;
        if (agentTools == null) return true;
        return agentTools.findByAgent_IdAndToolNameAndIsActiveTrue(call.getAgent().getId(), toolName)
                .map(AgentTool::actionEffect)
                .map(ToolActionEffect::isSideEffecting)
                .orElse(true);
    }
}
