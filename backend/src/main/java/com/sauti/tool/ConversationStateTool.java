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
import com.sauti.session.PersonNameEntityExtractor;
import com.sauti.session.PersonNameNormalizer;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final int BOOKING_REFERENCE_SUFFIX_LENGTH = 12;
    private static final Map<String, String> SPOKEN_BOOKING_TOKENS = Map.ofEntries(
            Map.entry("zero", "0"),
            Map.entry("one", "1"),
            Map.entry("two", "2"),
            Map.entry("three", "3"),
            Map.entry("four", "4"),
            Map.entry("five", "5"),
            Map.entry("six", "6"),
            Map.entry("seven", "7"),
            Map.entry("eight", "8"),
            Map.entry("nine", "9"),
            Map.entry("dash", "-"),
            Map.entry("hyphen", "-"),
            Map.entry("minus", "-")
    );
    private static final Set<String> COMMON_FIELDS = Set.of(
            "caller_name", "appointment_name", "recipient_relation", "service_type",
            "caller_phone", "new_caller_phone", "caller_email",
            "booking_number", "booking_date", "booking_lookup_name", "booking_time",
            "existing_booking_action",
            "preferred_day", "preferred_time",
            "review_decision"
    );
    private static final Set<String> PERSON_NAME_FIELDS = Set.of(
            "caller_name", "appointment_name", "booking_lookup_name"
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
    private static final Set<String> NAME_CAPTURE_STATUS = Set.of(
            "not_applicable", "incomplete", "complete"
    );
    private static final Set<String> PHONE_CAPTURE_STATUS = Set.of(
            "not_applicable", "incomplete", "complete"
    );
    private static final Set<String> CALLER_QUESTION = Set.of(
            "none", "answered_in_spoken_response", "requires_business_tool"
    );
    private static final Set<String> ACTION_AUTHORIZATION = Set.of(
            "not_applicable", "unconditional", "blocked"
    );
    private static final Set<String> CALL_DISPOSITIONS = Set.of("continue", "end");

    private final CallSessionStore sessions;
    private final AgentToolRepository agentTools;
    private final PersonNameEntityExtractor personNames;

    @Autowired
    public ConversationStateTool(
            CallSessionStore sessions,
            AgentToolRepository agentTools,
            PersonNameEntityExtractor personNames
    ) {
        this.sessions = sessions;
        this.agentTools = agentTools;
        this.personNames = personNames;
    }

    public ConversationStateTool(CallSessionStore sessions, AgentToolRepository agentTools) {
        this(sessions, agentTools, (call, candidate) -> PersonNameNormalizer.normalize(candidate));
    }

    public ConversationStateTool(CallSessionStore sessions) {
        this(sessions, null);
    }

    public static LlmToolDefinition definition() {
        var valueProperties = new LinkedHashMap<String, Object>();
        COMMON_FIELDS.forEach(field -> valueProperties.put(field, Map.of(
                "type", "string",
                "description", switch (field) {
                    case "caller_name" -> "The exact, complete semantic name entity for the person speaking. Return only the name in its original script and with its original diacritics. Exclude every introduction, carrier phrase, title that was not stated as part of the name, and other sentence text in whatever language the caller used. An introduction that ends before an actual name entity is incomplete and must be omitted. When a later turn completes or corrects the name, emit that complete name so it replaces any earlier partial value. Never return the raw utterance.";
                    case "appointment_name" -> "The exact, complete semantic name entity for the person receiving the service. Return only the name in its original script and with its original diacritics; exclude all surrounding sentence text in any language. Omit an incomplete introduction with no actual name entity, and emit a later completed or corrected name so it replaces stale state.";
                    case "recipient_relation" -> "Relationship of an explicitly different recipient to the caller, expressed compactly.";
                    case "service_type" -> "Requested configured service, only when the meaning is clear.";
                    case "caller_phone" -> "Complete caller-provided phone number normalized to an optional leading plus followed only by digits. Emit it only when every spoken digit was understood unambiguously and the caller finished the sequence.";
                    case "new_caller_phone" -> "Complete replacement contact phone explicitly requested for an existing booking, normalized to an optional leading plus followed only by digits. Never overwrite caller_phone, which verifies the current booking.";
                    case "caller_email" -> "Complete caller-provided email address.";
                    case "booking_number" -> "Accumulated customer-facing booking number supplied for a lookup, update, reschedule, or cancellation. Normalize spelled characters, number words, and dash/hyphen into the SAT-XXXXXXXXXXXX form, where exactly twelve characters follow SAT-. Consecutive partial fragments are valid updates, but never claim to look up a partial reference.";
                    case "booking_date" -> "Date of an existing appointment, normalized to yyyy-MM-dd in the business timezone. This identifies an existing booking and is distinct from preferred_day for a new or replacement date.";
                    case "booking_lookup_name" -> "The exact semantic name entity the caller says the existing booking was saved under, with no surrounding sentence text in any language. If the caller spells the name letter by letter, reconstruct the name from those letters and prefer that explicit spelling over a nearby speech-to-text word. Never fill this from a name disclosed by a tool result.";
                    case "booking_time" -> "Exact time of an existing appointment normalized to HH:mm for private phone/date/time verification.";
                    case "existing_booking_action" -> "Explicit requested operation for an existing booking: lookup, update, reschedule, or cancel.";
                    case "preferred_day" -> "Clearly understood appointment date normalized to yyyy-MM-dd using TODAY IN THE BUSINESS TIMEZONE. Omit when the date is unclear.";
                    case "preferred_time" -> "Clearly understood exact appointment time normalized to HH:mm, or a clear broad period such as morning or afternoon. Omit when the time is unclear.";
                    case "review_decision" -> "Meaning of the caller's latest response to the immediately preceding server-retained booking review or action confirmation: approved, corrected, rejected, or unclear. Interpret the answer in context, not by isolated positive or negative words: when asked to correct anything inaccurate, a response meaning 'no, everything is correct' is approved. This is turn-scoped and never inferred from politeness alone.";
                    default -> "Explicitly provided conversation value.";
                }
        )));
        var updates = new LinkedHashMap<String, Object>();
        updates.put("type", "object");
        updates.put("description", "Only semantic field values explicitly and clearly stated or corrected in the latest caller turn. These are extracted entities, never copied utterances. For every person-name field, preserve the exact complete name and original script but remove all surrounding words based on meaning in the caller's language. A name introduction with no following name entity is incomplete, not a name. A later completed or corrected value must be emitted even when the assistant already acknowledged it verbally, so authoritative state replaces the stale value. Omit unchanged or doubtful values. Never infer a person, service, date, time, or selection from a similar-sounding or incoherent fragment.");
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
        properties.put("name_capture_status", Map.of(
                "type", "string",
                "enum", List.of("not_applicable", "incomplete", "complete"),
                "description", "Language-neutral completeness judgment for person-name information in the latest caller turn. Use incomplete when the caller starts introducing a name but no actual complete name entity follows; emit no person-name updates. Use complete only when updates contains the extracted complete name entity. Use not_applicable when this turn supplies no name."
        ));
        properties.put("phone_capture_status", Map.of(
                "type", "string",
                "enum", List.of("not_applicable", "incomplete", "complete"),
                "description", "Language-neutral completeness judgment for phone information in the latest caller turn. Use incomplete when any spoken digit is ambiguous, missing, interrupted, replaced by an unrecognized sound, or the caller has not clearly finished the sequence; emit no phone update and ask for one slow natural repetition. Use complete only when every digit is unambiguous and updates contains the complete normalized phone. Length alone never proves completeness. Use not_applicable when this turn supplies no phone."
        ));
        properties.put("spoken_response", Map.of(
                "type", "string",
                "description", "A concise, polite, natural reply in the caller's current language. Answer direct questions first. When call_disposition is end, this must be the complete brief respectful farewell and must not be empty. Otherwise leave empty only when a separate business tool must run before any reply. Never include tool syntax, JSON, headings, or private reasoning."
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
        properties.put("call_disposition", Map.of(
                "type", "string",
                "enum", List.of("continue", "end"),
                "description", "Semantic call disposition in any language. Use end only when the caller clearly says they are finished, declines further help, says goodbye, or explicitly asks to end the call. Use continue while any request, correction, question, confirmation, or business operation remains unresolved. Do not decide from keywords alone."
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
                                "name_capture_status", "phone_capture_status",
                                "spoken_response", "caller_question", "action_authorization",
                                "call_disposition", "next_action", "business_tool"
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
            var hasMaterialCorrection = turnUpdates.keySet().stream()
                    .anyMatch(field -> !"review_decision".equals(field));
            if (hasMaterialCorrection || !clearedFields.isEmpty()) {
                // Any corrected material state invalidates a previously spoken
                // side-effect review. A later confirmation must be for a new
                // server-retained proposal built from the corrected values.
                sessions.updatePendingAction(call.getTwilioCallSid(), null);
                pendingAction = Optional.empty();
            }
            var changesExistingBookingIdentity = Set.of(
                    "caller_phone", "booking_date", "booking_time", "booking_number"
            ).stream().anyMatch(field -> turnUpdates.containsKey(field) || clearedFields.contains(field));
            if (changesExistingBookingIdentity) {
                sessions.updateVerifiedBookingIdentity(call.getTwilioCallSid(), null);
            }
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
            var incompleteBookingReference = incompleteBookingReference(next, turnUpdates);
            var bookingIdentityBecameReady = (turnUpdates.containsKey("booking_number")
                    || turnUpdates.containsKey("caller_phone")
                    || turnUpdates.containsKey("booking_date")
                    || turnUpdates.containsKey("booking_lookup_name")
                    || turnUpdates.containsKey("booking_time")
                    || turnUpdates.containsKey("existing_booking_action"))
                    && !incompleteBookingReference
                    && lookupBookingArguments(next).isPresent()
                    && configuredFor(call, "lookup_booking");
            var callDisposition = choice(
                    toolCall.arguments().get("call_disposition"), CALL_DISPOSITIONS, "continue"
            );
            var callMustEnd = "end".equals(callDisposition)
                    && configuredFor(call, "end_call");
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
            var nextAction = callMustEnd
                    ? "use_business_tool"
                    : bookingIdentityBecameReady
                    ? "use_business_tool"
                    : "answered_in_spoken_response".equals(callerQuestion)
                    ? "reply"
                    : "requires_business_tool".equals(callerQuestion)
                        ? (questionTool.isBlank() ? "reply" : "use_business_tool")
                        : approvedPendingAction || reviewMustContinue || availabilityMustContinue || bookingBecameReady
                            ? "use_business_tool"
                            : choice(toolCall.arguments().get("next_action"), NEXT_ACTIONS, "reply");
            var businessTool = callMustEnd
                    ? "end_call"
                    : bookingIdentityBecameReady
                    ? "lookup_booking"
                    : !questionTool.isBlank()
                    ? questionTool
                    : approvedPendingAction
                        ? pendingAction.orElseThrow().toolName()
                        : availabilityMustContinue
                        ? "check_availability"
                        : reviewMustContinue || bookingBecameReady
                            ? "book_slot"
                            : requestedBusinessTool;
            var spoken = "reply".equals(nextAction) && !incompleteBookingReference
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
                    lookupBookingArguments(next).ifPresent(arguments ->
                            result.put("nextToolArguments", arguments)
                    );
                } else if ("end_call".equals(businessTool)) {
                    var farewell = VoiceOutputGuard.speechText(
                            stringArgument(toolCall.arguments(), "spoken_response")
                    );
                    if (!farewell.isBlank()) {
                        result.put("nextToolArguments", Map.of(
                                "outcome", "completed",
                                "spoken_farewell", farewell,
                                "question_handling", "ready_for_action",
                                "confirmation_state", "confirmed"
                        ));
                    }
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
                    bookingIdentityArguments(call, next).ifPresent(identity -> {
                        var arguments = new LinkedHashMap<String, Object>(identity);
                        arguments.put("question_handling", "ready_for_action");
                        arguments.put("confirmation_state", "confirmed");
                        result.put("nextToolArguments", Map.copyOf(arguments));
                    });
                }
            }
            result.put("instruction", incompleteBookingReference
                    ? "The caller supplied only part of a Sauti booking number. Do not call lookup_booking and do "
                        + "not say that a lookup is running. Ask in the caller's current language for the complete "
                        + "reference: SAT, the dash, and all twelve following letters or digits. Preserve the phone "
                        + "already collected. Wait for the caller to repeat the full reference."
                    : spoken.isBlank()
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
                .flatMap(arguments -> bookingIdentityArguments(call, state).map(identity -> {
                    var secured = new LinkedHashMap<String, Object>(arguments);
                    secured.putAll(identity);
                    return Map.copyOf(secured);
                }));
    }

    private Optional<Map<String, Object>> bookingIdentityArguments(Call call, ConversationState state) {
        try {
            var retained = sessions.verifiedBookingIdentity(call.getTwilioCallSid());
            if (retained != null
                    && retained.isPresent()
                    && call.getTenant().getId().equals(retained.orElseThrow().tenantId())) {
                // Identity is server-owned. Mutation arguments intentionally
                // contain no booking identifier or phone for the model to alter.
                return Optional.of(Map.of());
            }
        } catch (RuntimeException ignored) {
            // Fall through for an in-flight session created by an older release.
        }
        var bookingNumber = state.values().getOrDefault("booking_number", "").trim();
        var callerPhone = state.values().getOrDefault("caller_phone", "").trim();
        if (!completeBookingNumber(bookingNumber) || callerPhone.isBlank()) return Optional.empty();
        return Optional.of(Map.of(
                "booking_number", bookingNumber,
                "caller_phone", callerPhone
        ));
    }

    private Optional<Map<String, Object>> lookupBookingArguments(ConversationState state) {
        var bookingNumber = state.values().getOrDefault("booking_number", "").trim();
        var callerPhone = state.values().getOrDefault("caller_phone", "").trim();
        if (completeBookingNumber(bookingNumber) && !callerPhone.isBlank()) {
            var arguments = new LinkedHashMap<String, Object>();
            arguments.put("booking_number", bookingNumber);
            arguments.put("caller_phone", callerPhone);
            arguments.put("requested_action", existingBookingAction(state));
            return Optional.of(Map.copyOf(arguments));
        }
        var bookingDate = state.values().getOrDefault("booking_date", "").trim();
        try {
            java.time.LocalDate.parse(bookingDate);
        } catch (java.time.format.DateTimeParseException exception) {
            return Optional.empty();
        }
        if (callerPhone.isBlank()) return Optional.empty();
        var arguments = new LinkedHashMap<String, Object>();
        arguments.put("caller_phone", callerPhone);
        arguments.put("booking_date", bookingDate);
        var bookingTime = state.values().getOrDefault("booking_time", "").trim();
        if (bookingTime.isBlank()) return Optional.empty();
        try {
            java.time.LocalTime.parse(bookingTime);
        } catch (java.time.format.DateTimeParseException exception) {
            return Optional.empty();
        }
        arguments.put("booking_time", bookingTime);
        arguments.put("requested_action", existingBookingAction(state));
        return Optional.of(Map.copyOf(arguments));
    }

    private String existingBookingAction(ConversationState state) {
        var requestedAction = state.values().getOrDefault("existing_booking_action", "").trim();
        return Set.of("lookup", "update", "reschedule", "cancel").contains(requestedAction)
                ? requestedAction : "lookup";
    }

    private boolean incompleteBookingReference(
            ConversationState state,
            Map<String, String> turnUpdates
    ) {
        if (!turnUpdates.containsKey("booking_number")) return false;
        var bookingNumber = state.values().getOrDefault("booking_number", "").trim();
        return !bookingNumber.isBlank() && !completeBookingNumber(bookingNumber);
    }

    private boolean completeBookingNumber(String value) {
        return value != null && value.trim().matches("SAT-[A-Z0-9]{12}");
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
        var nameCaptureStatus = choice(
                arguments.get("name_capture_status"), NAME_CAPTURE_STATUS, "incomplete"
        );
        var phoneCaptureStatus = choice(
                arguments.get("phone_capture_status"), PHONE_CAPTURE_STATUS, "incomplete"
        );
        var extractedNames = new HashMap<String, String>();
        // Review decisions authorize at most the current caller turn. They must
        // never leak into a later turn as stale approval.
        values.remove("review_decision");
        if (turnUpdates.containsKey("booking_date")
                || turnUpdates.containsKey("booking_lookup_name")) {
            values.remove("booking_number");
            values.remove("booking_time");
        }
        clearFields(arguments.get("clear_fields"), allowedDetails).forEach(values::remove);

        var subject = choice(arguments.get("booking_subject"), SUBJECTS, "unchanged");
        if ("unchanged".equals(subject)) subject = current.bookingSubject();
        var intent = choice(arguments.get("booking_intent"), INTENTS, "unchanged");
        if ("unchanged".equals(intent)) intent = current.bookingIntent();

        turnUpdates.forEach((key, value) -> {
            if (!COMMON_FIELDS.contains(key) || value.isBlank()) return;
            if ("booking_number".equals(key)) {
                var normalized = normalizeBookingNumber(value);
                var previous = normalizeBookingNumber(values.getOrDefault(key, ""));
                if (canAppendBookingFragment(previous, normalized)) {
                    normalized = previous + normalized.replaceFirst("^-", "");
                }
                if (!normalized.isBlank()) values.put(key, normalized);
                return;
            }
            if (PERSON_NAME_FIELDS.contains(key)) {
                if (!"complete".equals(nameCaptureStatus)) return;
                var normalizedName = extractedNames.computeIfAbsent(
                        value,
                        candidate -> PersonNameNormalizer.normalize(personNames.extract(call, candidate))
                );
                if (!normalizedName.isBlank()) values.put(key, normalizedName);
                return;
            }
            if ("caller_phone".equals(key) || "new_caller_phone".equals(key)) {
                if (!"complete".equals(phoneCaptureStatus)) return;
                var normalizedPhone = normalizePhone(value);
                if (!normalizedPhone.isBlank()) values.put(key, normalizedPhone);
                return;
            }
            values.put(key, value);
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

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) return "";
        var trimmed = value.trim();
        var international = trimmed.startsWith("+");
        var digits = new StringBuilder();
        for (var index = 0; index < trimmed.length(); index++) {
            var character = trimmed.charAt(index);
            var digit = Character.digit(character, 10);
            if (digit >= 0) {
                digits.append(digit);
                continue;
            }
            if (Character.isWhitespace(character)
                    || character == '-' || character == '(' || character == ')'
                    || (character == '+' && index == 0)) {
                continue;
            }
            return "";
        }
        if (digits.length() < 7 || digits.length() > 15) return "";
        return international ? "+" + digits : digits.toString();
    }

    private String normalizeBookingNumber(String value) {
        if (value == null || value.isBlank()) return "";
        var result = new StringBuilder();
        var tokens = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}-]+", " ")
                .split("\\s+");
        for (var token : tokens) {
            if (token.isBlank()) continue;
            var spoken = SPOKEN_BOOKING_TOKENS.get(token);
            if (spoken != null) {
                result.append(spoken);
            } else if ("sat".equals(token)) {
                result.append("SAT");
            } else {
                result.append(token.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT));
            }
        }
        var normalized = result.toString()
                .replaceAll("-+", "-")
                .replaceAll("[^A-Z0-9-]", "");
        if (normalized.startsWith("SAT") && !normalized.startsWith("SAT-")) {
            normalized = "SAT-" + normalized.substring(3).replaceFirst("^-", "");
        }
        return normalized;
    }

    private boolean canAppendBookingFragment(String previous, String fragment) {
        if (!previous.matches("SAT-[A-Z0-9]{0," + (BOOKING_REFERENCE_SUFFIX_LENGTH - 1) + "}")) {
            return false;
        }
        if (fragment.isBlank() || fragment.startsWith("SAT")) return false;
        var suffix = fragment.replaceFirst("^-", "").replaceAll("[^A-Z0-9]", "");
        var previousLength = previous.substring(4).length();
        return !suffix.isBlank() && previousLength + suffix.length() <= BOOKING_REFERENCE_SUFFIX_LENGTH;
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
