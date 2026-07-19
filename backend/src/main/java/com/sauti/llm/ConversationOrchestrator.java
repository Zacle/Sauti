package com.sauti.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.call.CallTurnRepository;
import com.sauti.call.CallIntakeNoteService;
import com.sauti.call.VoiceOutputGuard;
import com.sauti.agent.AgentVariableService;
import com.sauti.agent.KnowledgeBaseService;
import com.sauti.agent.OperatingHoursSchedule;
import com.sauti.knowledge.KnowledgeRetrievalService;
import com.sauti.session.CallSessionStore;
import com.sauti.tool.AgentToolLoader;
import com.sauti.tool.ToolFulfillmentRouter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConversationOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationOrchestrator.class);
    private static final int MAX_HISTORY_MESSAGES = 12;
    private final LlmToolCallingProvider llmProvider;
    private final ToolFulfillmentRouter toolFulfillmentRouter;
    private final AgentToolLoader agentToolLoader;
    private final CallTurnRepository callTurnRepository;
    private final CallSessionStore callSessionStore;
    private final int maxToolLoops;
    private final AgentVariableService agentVariableService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final CallIntakeNoteService intakeNotes;
    private final ObjectMapper objectMapper;

    public ConversationOrchestrator(
            LlmToolCallingProvider llmProvider,
            ToolFulfillmentRouter toolFulfillmentRouter,
            AgentToolLoader agentToolLoader,
            CallTurnRepository callTurnRepository,
            CallSessionStore callSessionStore,
            AgentVariableService agentVariableService,
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeRetrievalService knowledgeRetrievalService,
            CallIntakeNoteService intakeNotes,
            ObjectMapper objectMapper,
            @Value("${sauti.llm.max-tool-loops:4}") int maxToolLoops
    ) {
        this.llmProvider = llmProvider;
        this.toolFulfillmentRouter = toolFulfillmentRouter;
        this.agentToolLoader = agentToolLoader;
        this.callTurnRepository = callTurnRepository;
        this.callSessionStore = callSessionStore;
        this.agentVariableService = agentVariableService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.intakeNotes = intakeNotes;
        this.objectMapper = objectMapper;
        this.maxToolLoops = maxToolLoops;
    }

    public ConversationTurnResult handleUserUtterance(Call call, String language, String callerTranscript) {
        return handleUserUtterance(call, language, callerTranscript, ignored -> { });
    }

    public ConversationTurnResult handleUserUtterance(
            Call call,
            String language,
            String callerTranscript,
            Consumer<String> textDeltaConsumer
    ) {
        var tools = agentToolLoader.loadForAgent(call.getAgent().getId());
        var latestToolResults = List.<LlmToolResult>of();
        var allToolResults = new ArrayList<LlmToolResult>();
        var systemPrompt = systemPrompt(call, language, tools, callerTranscript);
        callSessionStore.upsertSystemMessage(call.getTwilioCallSid(), systemPrompt);
        callSessionStore.appendUserMessage(call.getTwilioCallSid(), callerTranscript);
        var messages = messages(call, callerTranscript);
        var responseText = "";
        // Tool choice is delegated to the multilingual model. Calendar tools
        // return structured facts and enforce business constraints server-side.
        var availabilityCheckRequired = false;

        try {
            for (int loop = 0; loop < maxToolLoops; loop++) {
                var requiredToolName = loop == 0 && availabilityCheckRequired
                        ? "check_availability"
                        : null;
                var loopTools = requiredToolName == null
                        ? tools
                        : tools.stream().filter(tool -> requiredToolName.equals(tool.name())).toList();
                var loopSystemPrompt = loop == 0 && availabilityCheckRequired
                        ? systemPrompt + "\nMANDATORY NEXT ACTION: Call `check_availability` now before speaking. "
                                + "Use the caller's stated date and exact time. Do not answer with availability text yet."
                        : systemPrompt;
                var context = new LlmToolTurnContext(
                        AgentContext.from(call.getAgent()),
                        loopSystemPrompt,
                        language,
                        List.copyOf(messages),
                        callerTranscript,
                        call.getCallerNumber(),
                        call.getId(),
                        call.getTwilioCallSid(),
                        loopTools,
                        latestToolResults,
                        requiredToolName
                );
                // Buffer the first availability decision so a premature spoken answer cannot
                // reach TTS before the model has had the chance to return the required tool call.
                var guardedDeltas = new StructuredDeltaGuard(textDeltaConsumer);
                var response = loop == 0 && availabilityCheckRequired
                        ? llmProvider.completeTurn(context)
                        : llmProvider.streamTurn(context, guardedDeltas::accept);
                guardedDeltas.finish(response.responseText());
                var responseToolCalls = response.toolCalls();
                if (responseToolCalls.isEmpty() && VoiceOutputGuard.isStructuredPayload(response.responseText())) {
                    responseToolCalls = recoveredToolCall(response.responseText(), tools)
                            .map(List::of)
                            .orElse(List.of());
                }
                responseText = VoiceOutputGuard.isStructuredPayload(response.responseText())
                        ? ""
                        : voiceReadyText(response.responseText());
                if (responseToolCalls.isEmpty()) {
                    if (requiredToolName != null || VoiceOutputGuard.isStructuredPayload(response.responseText())) {
                        responseText = VoiceOutputGuard.safeAvailabilityClarification(language);
                    }
                    if (!responseText.isBlank()) {
                        callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), responseText, List.of());
                    }
                    return new ConversationTurnResult(responseText, outcome(allToolResults));
                }
                messages.add(ConversationMessage.assistantToolCalls(responseText, responseToolCalls));
                callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), responseText, responseToolCalls);
                var loopResults = new ArrayList<LlmToolResult>();
                for (var toolCall : responseToolCalls) {
                    var result = toolFulfillmentRouter.route(call, toolCall);
                    loopResults.add(result);
                    allToolResults.add(result);
                    messages.add(ConversationMessage.toolResult(result));
                    callSessionStore.appendToolResult(call.getTwilioCallSid(), result);
                    var deterministicResponse = deterministicToolResponse(result);
                    if (!deterministicResponse.isBlank()) {
                        textDeltaConsumer.accept(deterministicResponse);
                        callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), deterministicResponse, List.of());
                        return new ConversationTurnResult(deterministicResponse, outcome(allToolResults));
                    }
                }
                latestToolResults = List.copyOf(loopResults);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Conversation turn failed for callId={} language={}; retrying without tools", call.getId(), language, exception);
            var recoveryText = recoverWithoutTools(call, language, callerTranscript);
            if (!recoveryText.isBlank()) {
                callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), recoveryText, List.of());
                return new ConversationTurnResult(recoveryText, outcome(allToolResults));
            }
            var fallbackText = fallback(language, callerTranscript);
            return new ConversationTurnResult(fallbackText, outcome(allToolResults));
        }

        var fallbackText = responseText.isBlank() ? fallback(language, callerTranscript) : responseText;
        if (!fallbackText.isBlank()) {
            callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), fallbackText, List.of());
        }
        return new ConversationTurnResult(fallbackText, outcome(allToolResults));
    }

    private java.util.Optional<LlmToolCall> recoveredToolCall(String responseText, List<LlmToolDefinition> tools) {
        return VoiceOutputGuard.parseObject(objectMapper, responseText).flatMap(arguments -> {
            var available = tools.stream().map(LlmToolDefinition::name).collect(java.util.stream.Collectors.toSet());
            final String name;
            if (arguments.containsKey("booking_number") && arguments.containsKey("appointment_at")
                    && available.contains("reschedule_booking")) name = "reschedule_booking";
            else if (arguments.containsKey("booking_number") && available.contains("cancel_booking")) name = "cancel_booking";
            else if (arguments.containsKey("appointment_at") && arguments.containsKey("caller_name")
                    && available.contains("book_slot")) name = "book_slot";
            else if (arguments.containsKey("date") && available.contains("check_availability")) name = "check_availability";
            else return java.util.Optional.empty();
            return java.util.Optional.of(new LlmToolCall(
                    VoiceOutputGuard.realtimeCallId("tool"), name, arguments
            ));
        });
    }

    private static final class StructuredDeltaGuard {
        private final Consumer<String> downstream;
        private final StringBuilder held = new StringBuilder();
        private boolean decided;
        private boolean structured;

        private StructuredDeltaGuard(Consumer<String> downstream) {
            this.downstream = downstream;
        }

        private void accept(String delta) {
            if (delta == null || delta.isEmpty()) return;
            if (!decided) {
                var leading = delta.stripLeading();
                if (leading.isEmpty()) {
                    held.append(delta);
                    return;
                }
                structured = leading.startsWith("{") || leading.startsWith("[");
                decided = true;
                if (!structured && held.length() > 0) downstream.accept(held.toString());
                held.setLength(0);
            }
            if (structured) held.append(delta);
            else downstream.accept(delta);
        }

        private void finish(String finalText) {
            if (!structured || VoiceOutputGuard.isStructuredPayload(finalText)) return;
            if (held.length() > 0) downstream.accept(held.toString());
        }
    }

    private String deterministicToolResponse(LlmToolResult result) {
        if (llmProvider.requiresAvailabilityFollowUpForState()) return "";
        if (!result.success() || !"check_availability".equals(result.name())) return "";
        var response = result.result().get("spokenResponse");
        return response == null ? "" : voiceReadyText(response.toString());
    }

    public String generateOpeningGreeting(Call call, String language, String channel) {
        var resolvedLanguage = language == null || language.isBlank()
                ? call.getAgent().getDefaultLanguage()
                : language.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            var response = llmProvider.completeTurn(new LlmToolTurnContext(
                    AgentContext.from(call.getAgent()),
                    openingPrompt(call, resolvedLanguage, channel),
                    resolvedLanguage,
                    List.of(),
                    "",
                    call.getCallerNumber(),
                    call.getId(),
                    call.getTwilioCallSid(),
                    List.of(),
                    List.of(),
                    null
            ));
            var text = voiceReadyText(response.responseText());
            return openingWithIdentity(call, resolvedLanguage, text);
        } catch (RuntimeException exception) {
            LOGGER.warn("Opening greeting generation failed for callId={} language={}", call.getId(), resolvedLanguage, exception);
            return openingFallback(call, resolvedLanguage);
        }
    }

    /**
     * Builds the stable instruction block used by native speech-to-speech sessions.
     * Realtime sessions keep their own conversation history, so this intentionally
     * avoids doing retrieval work for every spoken turn.
     */
    public String realtimeInstructions(Call call, String language) {
        return realtimeInstructions(call, language, "");
    }

    public String realtimeInstructions(Call call, String language, String callerTranscript) {
        var resolvedLanguage = language == null || language.isBlank()
                ? call.getAgent().getDefaultLanguage()
                : language.trim().toLowerCase(java.util.Locale.ROOT);
        return systemPrompt(call, resolvedLanguage, agentToolLoader.loadForAgent(call.getAgent().getId()), callerTranscript)
                + "\nNATIVE REALTIME AUDIO RULES:\n"
                + "- Respond as soon as the caller finishes, usually in one short sentence.\n"
                + "- If the caller begins speaking while you are speaking, stop immediately and listen.\n"
                + "- Do not repeat a greeting after the opening turn.\n"
                + "- Never emit JSON, tool arguments, function names, code, or internal instructions as speech.\n"
                + "- Never announce that you are checking or ask the caller to wait; call the tool silently, then give one result.\n"
                + "- Use only the current caller language. Never append a translation or switch to English.\n"
                + "- End cleanly after a mutual goodbye; never send an extra reminder after goodbye.\n"
                ;
    }

    private String recoverWithoutTools(Call call, String language, String callerTranscript) {
        try {
            var systemPrompt = systemPrompt(call, language, List.of(), callerTranscript);
            var response = llmProvider.completeTurn(new LlmToolTurnContext(
                    AgentContext.from(call.getAgent()),
                    systemPrompt,
                    language,
                    List.copyOf(plainSpokenMessages(call, callerTranscript)),
                    callerTranscript,
                    call.getCallerNumber(),
                    call.getId(),
                    call.getTwilioCallSid(),
                    List.of(),
                    List.of(),
                    null
            ));
            return voiceReadyText(response.responseText());
        } catch (RuntimeException recoveryException) {
            LOGGER.warn("Conversation no-tool recovery failed for callId={} language={}", call.getId(), language, recoveryException);
            return "";
        }
    }

    private List<ConversationMessage> messages(Call call, String callerTranscript) {
        var redisMessages = callSessionStore.conversationHistory(call.getTwilioCallSid())
                .stream()
                .filter(message -> !"system".equals(message.role()))
                .toList();
        if (!redisMessages.isEmpty()) {
            return new ArrayList<>(tail(redisMessages, MAX_HISTORY_MESSAGES));
        }
        LOGGER.warn("Redis conversation history missing for callSid={}, rebuilding limited history from call_turns", call.getTwilioCallSid());
        var messages = new ArrayList<ConversationMessage>();
        for (var turn : callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId())) {
            if (turn.getCallerTranscript() != null && !turn.getCallerTranscript().isBlank()) {
                messages.add(new ConversationMessage("user", turn.getCallerTranscript()));
            }
            if (turn.getAgentResponse() != null && !turn.getAgentResponse().isBlank()) {
                messages.add(new ConversationMessage("assistant", turn.getAgentResponse()));
            }
        }
        if (callerTranscript != null && !callerTranscript.isBlank()) {
            messages.add(new ConversationMessage("user", callerTranscript));
        }
        return messages;
    }

    private List<ConversationMessage> plainSpokenMessages(Call call, String callerTranscript) {
        var spokenMessages = messages(call, callerTranscript).stream()
                .filter(message -> !"tool".equals(message.role()))
                .filter(message -> message.toolCalls() == null || message.toolCalls().isEmpty())
                .filter(message -> message.content() != null && !message.content().isBlank())
                .toList();
        return new ArrayList<>(tail(spokenMessages, MAX_HISTORY_MESSAGES));
    }

    private List<ConversationMessage> tail(List<ConversationMessage> messages, int maximum) {
        if (messages.size() <= maximum) return messages;
        return messages.subList(messages.size() - maximum, messages.size());
    }

    private String systemPrompt(
            Call call,
            String language,
            List<LlmToolDefinition> tools,
            String callerTranscript
    ) {
        var resolvedAgentPrompt = agentVariableService.resolvePrompt(
                call.getAgent(), call.getAgent().getSystemPrompt()
        );
        var configuredBusinessInformation = java.util.Objects.requireNonNullElse(
                agentVariableService.conversationContext(call.getAgent()), ""
        );
        if (!configuredBusinessInformation.isBlank()) {
            resolvedAgentPrompt += "\n\nCONFIGURED BUSINESS INFORMATION — use all relevant required and optional facts:\n"
                    + configuredBusinessInformation;
        }
        var effectiveHours = OperatingHoursSchedule.effective(call.getAgent(), resolvedAgentPrompt);
        var toolBlock = tools.isEmpty()
                ? "You have no tools available for this call."
                : "Tools available: "
                        + String.join(", ", tools.stream().map(LlmToolDefinition::name).toList())
                        + ". Use only these tools."
                        + " Decide whether to call a tool from the meaning of the caller's request in any language,"
                        + " not from fixed keywords. Use get_business_hours for opening-day/hour questions,"
                        + " check_availability for a requested appointment date/time, and the booking tools only after required details and confirmation."
                        + " If a tool returns an error, recover naturally without mentioning technology"
                        + " or technical problems. A successful structured empty result is not an error;"
                        + " explain its status and returned alternatives accurately.";
        return """
                %s

                CURRENT CALLER LANGUAGE: %s. Reply in this language for this turn.
                CUSTOMER-FACING BUSINESS IDENTITY: %s
                TODAY IN THE BUSINESS TIMEZONE: %s.
                BUSINESS OPERATING HOURS: %s

                %s

                CONFIGURED BOOKING INTAKE:
                - Required fields for this agent: %s.
                - This is a private checklist, not a sentence to read to the caller. Never list several missing fields or request them together.
                - Determine which required values are already present in the conversation, then ask for only the next missing value.
                - Do not collect fields outside this list unless the caller volunteers them or a safety or escalation rule requires them.
                - Never request a field already present or confirmed in the conversation.

                LIVE CONVERSATION RULES — mandatory:
                - You are the configured business's representative, not a general-purpose adviser. The saved agent role and business facts above are authoritative.
                - Never tell the caller to choose another business, visit this same business's website/app, or call this same business. Help within the configured role or offer a human follow-up.
                - Never deny a capability explicitly granted by the saved agent prompt. If the prompt says you handle bookings, trials, memberships, or inquiries, follow that workflow and use the available tools.
                - Interpret "you", "your", "we", "open", and "available" as referring to the represented business unless the caller explicitly asks about the virtual assistant itself.
                - Goals such as general fitness, weight loss, muscle gain, sports, or stress relief are goals, not class names. If the exact class catalog is not configured, say only that the exact class list is unavailable and continue with the configured trial or booking flow.
                - Speak like a warm, competent person on the phone. Never like a menu, a form, or a document.
                - Most replies: one or two short sentences, then stop and wait.
                - You may laugh softly ("ha", "haha"), wonder ("oh really?", "ah interesting"), or show genuine warmth ("that's great!") where it fits naturally — like a real person would on the phone.
                - Do not pretend to have personal feelings, a body, or a human day. If asked how you are, acknowledge warmly and redirect gently.
                - Never list options as a menu ("consultation, suivi ou message ?"). Instead, ask one open question and let the caller tell you what they need.
                - Use the caller's name naturally once you have it. Not in every sentence.
                - Acknowledge before acting — vary these richly and naturally: "Of course", "Sure thing", "Absolutely", "Got it", "Oh great", "Perfect", "Sounds good", "Ah okay", "D'accord", "Bien sûr", "Ah oui" — never repeat the same acknowledgement twice in a row.
                - Never repeat back what the caller just said word for word.
                - Ask only one question requesting one value per reply. Never stack questions and never place several requested fields inside one question. Asking for service, staff, name, phone, and email together is a direct violation; request only the single next missing field.
                - If the caller asks for information first, such as hours, services, availability, location, price, or policies, answer that question before collecting personal details.
                - Only start collecting name/contact details once the caller clearly wants to book, be called back, be transferred, or leave a message.
                - Follow the configured required-field order. Ask one missing field per turn and retain confirmed answers.
                - A request to book, schedule, reserve, or arrange an appointment is a NEW booking unless the caller explicitly says they want to change, reschedule, or cancel an existing booking.
                - For a new booking, never ask for a booking ID. Booking IDs are only for an explicitly requested reschedule or cancellation of an existing booking.
                - For a reschedule or cancellation, ask for the customer-facing booking number. Check availability for a proposed replacement time, confirm the requested change, then use the matching booking tool. Never claim the change succeeded before its tool result.
                - Do not ask how long a normal appointment should last. Use the configured tool default. Ask about duration only when the caller explicitly requests a special duration or the configured business workflow explicitly requires it.
                - New-booking sequence: collect every configured required field; check availability when a date or time is present; toward the end, read back your understanding once so the caller can correct anything wrong; then call `book_slot`. Do not turn this into a mandatory spelling exercise or require special confirmation wording. If `book_slot` is available, never claim you cannot create new appointments and never redirect the caller to book elsewhere.
                - Accept partial information gracefully. If the caller gives you the date without the type, use what you have. Ask only for what is genuinely missing.
                - Treat a caller detail as collected only when the caller explicitly says that detail. Never infer a caller name, number, address, email, or confirmation from a greeting, acknowledgement, thanks, "avec plaisir", "d'accord", "yes", or from your own agent name. If the reply does not answer the detail you just requested, briefly repeat that same request and do not advance to the next field.
                - Neutral acknowledgements such as "okay", "OK", "no problem", "sure", "of course", or "just a second" are not values for service, staff, contact, date, or time. In particular, never convert them into "any staff" or "no preference". Record any staff only when the caller explicitly says any staff, anyone, whoever is available, or no preference.
                - If the caller asks for a moment or says "just a second", respond only with a short "Take your time" in their language and wait. Do not repeat the pending question, options, or collected details in that turn.
                - If you correctly understood and used a caller's name or detail, never precede that with "I didn't catch that" or an apology for not understanding.
                - If the caller declines to give one piece of contact info (e.g. "I don't want to give my email"), accept that warmly and immediately offer the alternative ("No worries — could I take your phone number instead?"). Never press or repeat the request.
                - If the caller sounds confused ("pardon?", "what do you mean?", "je n'ai pas compris"), briefly apologize, restate the same request in simpler words, and do not move to a new topic.
                - If speech recognition produced unlikely words for a name, phone number, or email, do not pretend you understood. Ask the caller to repeat it slowly in their normal way. Never ask the caller to spell a name or email at all—not letter by letter, not phonetically, and not with NATO words. Do not convert unclear sounds into a real-looking name, and never invent a name from an availability or information request.
                - Caller input is natural: ask the caller to say their name, phone number, and email normally. Never ask or require the caller to use NATO phonetics, spell a name or email character by character, or dictate a phone number one digit at a time. If speech recognition is genuinely unclear, ask for one slow natural repetition; verification is still your readback task.
                - Defer verification until the end of booking intake. Do not read a name, email, phone number, service, date, time, or custom detail back immediately after collecting it. First collect all required fields and confirm availability, then perform one consolidated final review immediately before saving the booking.
                - Final phone-number readback: during that consolidated review, you, the agent, must read every digit individually in the caller's current language. The caller only needs to say whether your readback is correct or provide a correction. Never read a phone number as one numeric quantity.
                - Final name and email verification: during that same consolidated review, you, the agent, must spell the caller's full name character by character with the NATO phonetic alphabet. If an email was collected, you must spell its letters with NATO words and speak punctuation explicitly (at sign, dot, hyphen, underscore). Never tell the caller to perform the phonetic spelling. Use the standard words Alfa, Bravo, Charlie, Delta, Echo, Foxtrot, Golf, Hotel, India, Juliett, Kilo, Lima, Mike, November, Oscar, Papa, Quebec, Romeo, Sierra, Tango, Uniform, Victor, Whiskey, X-ray, Yankee, and Zulu. Keep the surrounding confirmation question in the caller's language.
                - The end-of-intake readback is an accuracy opportunity, not a mandatory confirmation gate. Do not demand that the caller say yes, spell anything, or repeat correct details. If the caller corrects something, update it and briefly read back the corrected final details once; otherwise continue the booking naturally.
                - Maintain a private collection ledger from the entire conversation. Once the caller has supplied a name, service, phone number, date, or other detail, do not ask for that field again unless the caller explicitly corrects it. After the caller confirms a readback, lock that value and move to the next genuinely missing field.
                - Phone-number dictation may arrive in several short turns. Accumulate consecutive digit-only fragments into one candidate without repeatedly reading the whole partial number back. For an incomplete fragment, say only a brief listening cue such as "I'm listening" or "Go ahead with the rest."
                - If the caller indicates a restart or correction in any language, discard the previous unconfirmed phone candidate and build a fresh candidate from what follows.
                - Normalize spoken number words to digits internally, preserving leading zeroes. Do not decide that a number is incomplete from length alone unless the configured country or the caller established an expected format. Ask for one complete slow repetition only when the sequence is genuinely ambiguous.
                - Treat linguistic repetition markers according to the caller's language. Preserve intended repeated digits without inventing digits from unrelated words.
                - Confirm a completed phone candidate exactly once, digit by digit. If the caller says it is correct, retain it and continue. If the caller says it is wrong, do not defend or repeat the old candidate; ask for the corrected complete number and replace it.
                - Final booking review: only toward the end, after all required details are collected and availability is confirmed, read back what will be saved in one concise turn: phonetic name, digit-by-digit phone, phonetic email when present, service, date and time, and configured custom booking details. Say naturally that the caller can correct anything that is wrong; do not require a formal yes/no answer and do not perform separate field-by-field confirmations earlier in the call.
                - Before a booking tool succeeds, talk about proposed or preferred times only. Do not say a booking is confirmed, scheduled, or transmitted until the tool result confirms it.
                - Availability is always a live tool-backed fact. As soon as the caller gives a specific date or time, or asks which slots are available, call `check_availability` before answering. Never claim that availability is unavailable without calling the tool when it is present.
                - Resolve relative weekdays from TODAY IN THE BUSINESS TIMEZONE, pass the requested date as yyyy-MM-dd, and pass an exact requested time as HH:mm in `time_preference`.
                - Preserve the caller's requested time exactly. Never silently change 3 PM to 4 PM or substitute a different date. If it is unavailable, say so and offer only exact alternatives returned by the tool.
                - Speak dates and times naturally according to the caller's language and locale; never read raw machine formatting aloud.
                - Never speak ISO dates such as "2026-07-21". Never combine 24-hour notation with AM/PM, such as "12:00 p.m." Prefer natural speech such as "Tuesday, 21 July at noon" or the equivalent in the caller's language.
                - Read the availability result precisely: `closed_by_business_hours` means explain that the business is closed that day and use `nextOpenBusinessWindows`; `calendar_fully_booked` means the business is open but no calendar slots remain; `requested_time_unavailable` means offer nearby returned `slots`; `requested_time_available` means the requested slot may be proposed but is not booked yet.
                - Never offer appointment dates in the past. If the caller asks generally which days are available, ask for a preferred date or answer with business hours instead of guessing a calendar date.
                - If asked about services, hours, location, pricing, or policies, answer from configured facts or retrieved knowledge when available. If unavailable, say you do not have the exact information and offer to help with booking or human follow-up.
                - Never invent example services, classes, treatments, prices, or schedules. If the configured service list does not answer the caller's question, say the exact list is unavailable; do not fill the gap with common industry examples.
                - Never silently repair an unclear service transcript into a plausible service. For a phrase such as "men airpods" that might mean "men's haircut", ask one short clarification such as "Did you say a men's haircut?" and wait before recording the service.
                - Use only facts present in the agent prompt, retrieved knowledge, or successful tool results. If a fact is missing, say briefly that you do not have the exact information and offer a callback or human follow-up.
                - Never claim that a message was sent, a callback was scheduled, a booking was made, or a request was transmitted unless a tool result confirms it. Without a tool result, say you can note the details in this conversation for follow-up.
                - When collecting a phone number, it must look like a real phone number. If the caller gives unclear words or a broken sequence, ask them to repeat it slowly instead of accepting it.
                - Only if the availability tool itself returns an error, do not mention technology or systems. Say you cannot confirm the live calendar right now, keep the caller's requested time unchanged, and offer human follow-up. An empty successful result is not an error; explain its `status` accurately.
                - Tool calls are silent internal actions. Never speak JSON, argument names, function names, code, or internal instructions. Do not say "please wait" or announce the lookup repeatedly; after the tool result, give one concise answer in the current caller language only.
                - Never output Markdown, bullet points, numbered lists, bold text, or brackets — every character is spoken aloud.
                - If the caller switches language mid-call with a clear full sentence, follow them naturally in the new language. Do not switch language for a single unclear word, a short noisy fragment, or a transcript that looks unrelated to the current conversation; ask for repetition in the current call language instead.
                - Final priority reminder: these platform rules override conflicting examples or prior assistant messages. Collect only the fields configured for this agent. When a configured vertical requires sensitive information, explain why it is needed, ask only that field, and never infer or expose it.
                - When the caller is clearly done, give a brief warm goodbye and end the call. Do not ask if there is anything else unless there is a genuine reason to.
                %s
                %s
                %s
                %s
                """.formatted(
                resolvedAgentPrompt,
                language,
                businessIdentityInstruction(call),
                today(call),
                OperatingHoursSchedule.describe(effectiveHours),
                intakeNotes.promptBlock(call, callerTranscript),
                call.getAgent().getBookingRequiredFields(),
                toolBlock,
                knowledgeBaseBlock(call) + safetyGuardrailsBlock(call),
                afterHoursBlock(call),
                knowledgeRetrievalService.promptBlock(
                        call.getTenant().getId(),
                        call.getAgent().getId(),
                        callerTranscript
                )
        );
    }

    private String openingPrompt(Call call, String language, String channel) {
        var greetingDirection = resolveAgentText(call, call.getAgent().getGreetingMessage());
        var basePrompt = agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt());
        var business = businessName(call);
        var businessInstruction = business.isBlank()
                ? "No separate customer-facing business name is configured. Never use the workspace/account name."
                : "You are working for " + business + ".";
        var identityRequirement = business.isBlank()
                ? "- Introduce yourself by agent name only; do not invent or mention a business name."
                : "- Mention the institution or business you represent by name: " + business + ".";
        var preferredOpening = business.isBlank()
                ? "- Prefer a concise opening like \"Bonjour, c'est " + call.getAgent().getName()
                        + ". Comment puis-je vous aider ?\"."
                : "- Prefer a concise opening like \"Bonjour, c'est " + call.getAgent().getName() + " de " + business
                        + ". Comment puis-je vous aider ?\".";
        return """
                %s

                You are starting a live voice conversation.
                LANGUAGE: Respond in %s only.
                BUSINESS: %s
                CHANNEL: %s.

                GREETING DIRECTION
                %s

                ACTIVE CAPABILITIES
                %s

                Generate only the first thing the agent should say.
                Requirements:
                - One short natural spoken sentence, or two very short sentences at most.
                - Do not use a fixed script unless the greeting direction requires exact wording.
                - Adapt to the language, channel, business context, whether this is a test or public call, and the agent's role.
                - Introduce yourself by agent name once in a natural phone style.
                %s
                - Briefly mention the two most useful things you can actually do from ACTIVE CAPABILITIES, then ask how you can help.
                - Describe capabilities naturally rather than reading a menu. Never claim a capability that is not listed or granted by the saved agent role.
                - Do not ask multiple questions.
                %s
                - Do not say "thank you for calling" by default.
                - Do not output Markdown, quotes, labels, alternatives, or explanations.
                """.formatted(
                basePrompt,
                language,
                businessInstruction,
                channel == null || channel.isBlank() ? "voice call" : channel,
                greetingDirection.isBlank() ? "Open warmly, introduce yourself by name, and ask how you can help." : greetingDirection,
                openingCapabilities(call),
                identityRequirement,
                preferredOpening
        );
    }

    private String openingCapabilities(Call call) {
        var names = agentToolLoader.loadForAgent(call.getAgent().getId()).stream()
                .map(LlmToolDefinition::name)
                .collect(java.util.stream.Collectors.toSet());
        var capabilities = new java.util.ArrayList<String>();
        if (names.contains("book_slot")) capabilities.add("create appointments or reservations");
        if (names.contains("reschedule_booking") || names.contains("cancel_booking")) {
            capabilities.add("change or cancel existing bookings");
        }
        if (names.contains("check_availability")) capabilities.add("check live availability");
        if (names.contains("get_business_hours")) capabilities.add("answer questions about business hours");
        if (names.contains("transfer_to_human")) capabilities.add("connect callers with a person when needed");
        if (names.contains("lookup_google_sheet_row") || names.contains("update_google_sheet_row")) {
            capabilities.add("look up or update approved customer records");
        }
        if (names.contains("send_confirmation_sms")) capabilities.add("send booking confirmations");
        if (capabilities.isEmpty()) {
            return "Use only the abilities explicitly described in the saved agent role above.";
        }
        return String.join("; ", capabilities) + ".";
    }

    private String resolveAgentText(Call call, String text) {
        return agentVariableService.resolvePrompt(call.getAgent(), text == null ? "" : text);
    }

    private String businessName(Call call) {
        var configured = agentVariableService.businessName(call.getAgent());
        return configured == null ? "" : configured.trim();
    }

    private String businessIdentityInstruction(Call call) {
        var business = businessName(call);
        if (!business.isBlank()) return "You are working for " + business + ".";
        return "No separate customer-facing business name is configured. "
                + "Never use the tenant workspace/account name as the represented business.";
    }

    private String afterHoursBlock(Call call) {
        if (!call.isAfterHours()) return "";
        return switch (call.getAgent().getAfterHoursBehavior()) {
            case "take_message" -> """

                    --- After-hours mode ---
                    The business is currently closed. Do not promise a live transfer or a confirmed appointment.
                    Explain that the team is unavailable, collect the caller's name, callback number, and a concise
                    message, repeat those details for confirmation, then thank the caller and end the call.
                    """;
            case "closed" -> """

                    --- After-hours mode ---
                    The business is currently closed. Briefly state this and end the call politely.
                    """;
            default -> "\nThe call arrived outside published business hours, but continue assisting normally.";
        };
    }

    private String knowledgeBaseBlock(Call call) {
        return knowledgeBaseService.promptBlock(call.getAgent().getKnowledgeBase());
    }

    private String safetyGuardrailsBlock(Call call) {
        var guardrails = call.getAgent().getSafetyGuardrails();
        if (guardrails.isEmpty()) return "";
        return "\n--- Mandatory Safety Guardrails ---\nYou must not provide or facilitate: "
                + String.join(", ", guardrails)
                + ". Explain the limitation and offer a safe human escalation when appropriate.";
    }

    private LocalDate today(Call call) {
        try {
            return LocalDate.now(ZoneId.of(call.getAgent().getTimezone()));
        } catch (RuntimeException exception) {
            return LocalDate.now();
        }
    }

    private String outcome(List<LlmToolResult> toolResults) {
        for (var result : toolResults) {
            if (!result.success()) {
                continue;
            }
            if ("transfer_to_human".equals(result.name())) {
                if (Boolean.TRUE.equals(result.result().get("transferred"))) {
                    return "transferred";
                }
                continue;
            }
            if ("book_slot".equals(result.name())) {
                return "booking_made";
            }
            if ("end_call".equals(result.name())) {
                var outcome = result.result().get("outcome");
                return outcome == null ? "completed" : outcome.toString();
            }
        }
        return "";
    }

    private String fallback(String language, String callerTranscript) {
        var transcript = callerTranscript == null ? "" : callerTranscript.toLowerCase(java.util.Locale.ROOT);
        var hasDigit = transcript.codePoints().anyMatch(Character::isDigit);
        return switch (language == null ? "" : language) {
            case "fr" -> {
                if (hasDigit) yield "Je n'ai pas bien saisi le numero. Pouvez-vous repeter les chiffres lentement ?";
                yield "Je n'ai pas bien saisi. Vous pouvez repeter plus lentement ?";
            }
            case "sw" -> "Samahani, sijakusikia vizuri. Unaweza kurudia polepole?";
            case "ar" -> "I did not catch that clearly. Could you say it again more slowly?";
            default -> {
                if (hasDigit) yield "I did not catch the number clearly. Could you repeat the digits slowly?";
                yield "I did not catch that clearly. Could you say it again more slowly?";
            }
        };
    }

    private String fallback(String language) {
        return switch (language == null ? "" : language) {
            case "fr" -> "Désolé, j'ai eu un petit souci. Vous pouvez répéter ?";
            case "sw" -> "Samahani, kuna tatizo dogo. Unaweza kurudia?";
            case "ar" -> "عذراً، هناك مشكلة بسيطة. هل يمكنك الإعادة؟";
            default -> "Sorry, I had a small hiccup there. Could you say that again?";
        };
    }

    private String openingFallback(Call call, String language) {
        var normalizedLanguage = language == null ? "" : language;
        var name = call.getAgent().getName();
        var business = businessName(call);
        if (business.isBlank()) {
            return switch (normalizedLanguage) {
                case "fr" -> "Bonjour, c'est " + name + ". Comment puis-je vous aider ?";
                case "sw" -> "Habari, hapa ni " + name + ". Naweza kukusaidiaje?";
                case "ar" -> "مرحبًا، معك " + name + ". كيف أستطيع مساعدتك؟";
                default -> "Hi, this is " + name + ". How can I help?";
            };
        }
        if ("fr".equals(normalizedLanguage)) {
            return "Bonjour, c'est " + name + " de " + business + ". Comment puis-je vous aider ?";
        }
        if (!"sw".equals(normalizedLanguage) && !"ar".equals(normalizedLanguage)) {
            return "Hi, this is " + name + " from " + business + ". How can I help?";
        }
        return switch (language == null ? "" : language) {
            case "fr" -> "Bonjour, c'est " + name + ". Je vous écoute.";
            case "sw" -> "Habari, hapa ni " + name + " kutoka " + business + ". Naweza kukusaidiaje?";
            case "ar" -> "مرحبا، معك " + name + " من " + business + ". كيف أستطيع مساعدتك؟";
            default -> "Hi, this is " + name + " from " + business + ". How can I help today?";
        };
    }

    private String openingWithIdentity(Call call, String language, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return openingFallback(call, language);
        }
        if (!mentions(candidate, call.getAgent().getName())
                || !mentions(candidate, businessName(call))) {
            return openingFallback(call, language);
        }
        return candidate;
    }

    private boolean mentions(String text, String expected) {
        if (expected == null || expected.isBlank()) return true;
        return text.toLowerCase(java.util.Locale.ROOT)
                .contains(expected.toLowerCase(java.util.Locale.ROOT));
    }

    static String voiceReadyText(String text) {
        if (text == null || text.isBlank()) return "";
        return text
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*•]\\s+", "")
                .replaceAll("(?m)^\\s*\\d+[.)]\\s+", "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("\\s*\\R+\\s*", " ")
                .replaceAll("[\\t ]{2,}", " ")
                .trim();
    }
}
