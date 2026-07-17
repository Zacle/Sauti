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
        var availabilityCheckRequired = requiresAvailabilityCheck(call, callerTranscript, tools);

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
                var response = loop == 0 && availabilityCheckRequired
                        ? llmProvider.completeTurn(context)
                        : llmProvider.streamTurn(context, textDeltaConsumer);
                var responseToolCalls = response.toolCalls();
                if (requiredToolName != null && responseToolCalls.isEmpty()) {
                    responseToolCalls = VoiceOutputGuard.parseObject(objectMapper, response.responseText())
                            .map(arguments -> List.of(new LlmToolCall(
                                    java.util.UUID.randomUUID().toString(), requiredToolName, arguments
                            )))
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

    private boolean requiresAvailabilityCheck(
            Call call,
            String callerTranscript,
            List<LlmToolDefinition> tools
    ) {
        if (!call.getAgent().isBookingEnabled() || callerTranscript == null || callerTranscript.isBlank()) {
            return false;
        }
        if (tools.stream().noneMatch(tool -> "check_availability".equals(tool.name()))) {
            return false;
        }
        return AvailabilityIntentDetector.requiresAvailabilityCheck(callerTranscript);
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
                + "- End cleanly after a mutual goodbye; never send an extra reminder after goodbye.\n";
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
        var toolBlock = tools.isEmpty()
                ? "You have no tools available for this call."
                : "Tools available: "
                        + String.join(", ", tools.stream().map(LlmToolDefinition::name).toList())
                        + ". Use only these tools."
                        + " If a tool returns an error, recover naturally without mentioning technology"
                        + " or technical problems. A successful structured empty result is not an error;"
                        + " explain its status and returned alternatives accurately.";
        return """
                %s

                CURRENT CALLER LANGUAGE: %s. Reply in this language for this turn.
                BUSINESS: You are working for %s.
                TODAY IN THE BUSINESS TIMEZONE: %s.
                BUSINESS OPERATING HOURS: %s

                %s

                ALLOWED ROUTINE BOOKING INTAKE:
                - Collect only the caller name, one contact method, service/reason, and preferred date/time unless a configured tool explicitly requires another field.
                - Date of birth is prohibited for ordinary appointment booking. Do not ask for it unless `date_of_birth` or `dob` is explicitly listed in the agent's configured extraction fields: %s.
                - The authoritative notes above override any conflicting saved agent prompt. Never request a field already present there.

                LIVE CONVERSATION RULES — mandatory:
                - Speak like a warm, competent person on the phone. Never like a menu, a form, or a document.
                - Most replies: one or two short sentences, then stop and wait.
                - You may laugh softly ("ha", "haha"), wonder ("oh really?", "ah interesting"), or show genuine warmth ("that's great!") where it fits naturally — like a real person would on the phone.
                - Do not pretend to have personal feelings, a body, or a human day. If asked how you are, acknowledge warmly and redirect gently.
                - Never list options as a menu ("consultation, suivi ou message ?"). Instead, ask one open question and let the caller tell you what they need.
                - Use the caller's name naturally once you have it. Not in every sentence.
                - Acknowledge before acting — vary these richly and naturally: "Of course", "Sure thing", "Absolutely", "Got it", "Oh great", "Perfect", "Sounds good", "Ah okay", "D'accord", "Bien sûr", "Ah oui" — never repeat the same acknowledgement twice in a row.
                - Never repeat back what the caller just said word for word.
                - Ask only one question per reply. Never stack questions.
                - If the caller asks for information first, such as hours, services, availability, location, price, or policies, answer that question before collecting personal details.
                - Only start collecting name/contact details once the caller clearly wants to book, be called back, be transferred, or leave a message.
                - Booking collection order: caller's name, then contact number or email, then service or reason, then date and time preference. Do not ask for date before name once booking collection has started.
                - Accept partial information gracefully. If the caller gives you the date without the type, use what you have. Ask only for what is genuinely missing.
                - Treat a caller detail as collected only when the caller explicitly says that detail. Never infer a caller name, number, address, email, or confirmation from a greeting, acknowledgement, thanks, "avec plaisir", "d'accord", "yes", or from your own agent name. If the reply does not answer the detail you just requested, briefly repeat that same request and do not advance to the next field.
                - If the caller declines to give one piece of contact info (e.g. "I don't want to give my email"), accept that warmly and immediately offer the alternative ("No worries — could I take your phone number instead?"). Never press or repeat the request.
                - If the caller sounds confused ("pardon?", "what do you mean?", "je n'ai pas compris"), briefly apologize, restate the same request in simpler words, and do not move to a new topic.
                - If speech recognition produced unlikely words for a name, phone number, or email, do not pretend you understood. Ask the caller to repeat it slowly or spell it. Do not convert unclear sounds into a real-looking name, and never invent a name from an availability or information request.
                - Phone number readback: when the caller provides a phone number, read it back digit by digit before confirming — e.g. "So that's zero, one, one, five, seven, five, three — is that right?" Never read it as a single block number.
                - Maintain a private collection ledger from the entire conversation. Once the caller has supplied a name, service, phone number, date, or other detail, do not ask for that field again unless the caller explicitly corrects it. After the caller confirms a readback, lock that value and move to the next genuinely missing field.
                - Phone-number dictation may arrive in several short turns. Accumulate consecutive digit-only fragments into one candidate without repeatedly reading the whole partial number back. For an incomplete fragment, say only a brief listening cue such as "I'm listening" or "Go ahead with the rest."
                - Words such as "restart", "from the beginning", "recommencer", "a zero", "non", or "correction" mean the previous unconfirmed phone candidate is not reliable. Discard it and build a fresh candidate from the digits that follow; never merge digits from before and after a restart.
                - Normalize spoken number words to digits internally, preserving leading zeroes. Do not decide that a number is incomplete from length alone unless the configured country or the caller established an expected format. Ask for one complete slow repetition only when the sequence is genuinely ambiguous.
                - Repetition language is semantic, not extra digits: French "il y a trois un" or "trois fois un" means `111`; STT may render repeated spoken `un` as isolated `a`, so in an active phone-number dictation "a a a" means `111`. Never interpret the ordinary phrase "il y a" itself as digit 1.
                - Confirm a completed phone candidate exactly once, digit by digit. If the caller says it is correct, retain it and continue. If the caller says it is wrong, do not defend or repeat the old candidate; ask for the corrected complete number and replace it.
                - Pre-close confirmation: before ending the call or placing a booking, read back the key details collected: name, phone number or contact, and what was requested. Keep it brief and natural — "Just to confirm: your name is [name], phone number [digits one by one], and you'd like [request] — is that all correct?"
                - Before a booking tool succeeds, talk about proposed or preferred times only. Do not say a booking is confirmed, scheduled, or transmitted until the tool result confirms it.
                - Availability is always a live tool-backed fact. As soon as the caller gives a specific date or time, or asks which slots are available, call `check_availability` before answering. Never claim that availability is unavailable without calling the tool when it is present.
                - Resolve relative weekdays from TODAY IN THE BUSINESS TIMEZONE, pass the requested date as yyyy-MM-dd, and pass an exact requested time as HH:mm in `time_preference`.
                - Preserve the caller's requested time exactly. Never silently change 3 PM to 4 PM or substitute a different date. If it is unavailable, say so and offer only exact alternatives returned by the tool.
                - Read the availability result precisely: `closed_by_business_hours` means explain that the business is closed that day and use `nextOpenBusinessWindows`; `calendar_fully_booked` means the business is open but no calendar slots remain; `requested_time_unavailable` means offer nearby returned `slots`; `requested_time_available` means the requested slot may be proposed but is not booked yet.
                - Never offer appointment dates in the past. If the caller asks generally which days are available, ask for a preferred date or answer with business hours instead of guessing a calendar date.
                - If asked about services, hours, location, pricing, or policies, answer from configured facts or retrieved knowledge when available. If unavailable, say you do not have the exact information and offer to help with booking or human follow-up.
                - Never invent example services, classes, treatments, prices, or schedules. If the configured service list does not answer the caller's question, say the exact list is unavailable; do not fill the gap with common industry examples.
                - Use only facts present in the agent prompt, retrieved knowledge, or successful tool results. If a fact is missing, say briefly that you do not have the exact information and offer a callback or human follow-up.
                - Never claim that a message was sent, a callback was scheduled, a booking was made, or a request was transmitted unless a tool result confirms it. Without a tool result, say you can note the details in this conversation for follow-up.
                - When collecting a phone number, it must look like a real phone number. If the caller gives unclear words or a broken sequence, ask them to repeat it slowly instead of accepting it.
                - Only if the availability tool itself returns an error, do not mention technology or systems. Say you cannot confirm the live calendar right now, keep the caller's requested time unchanged, and offer human follow-up. An empty successful result is not an error; explain its `status` accurately.
                - Tool calls are silent internal actions. Never speak JSON, argument names, function names, code, or internal instructions. Do not say "please wait" or announce the lookup repeatedly; after the tool result, give one concise answer in the current caller language only.
                - Never output Markdown, bullet points, numbered lists, bold text, or brackets — every character is spoken aloud.
                - If the caller switches language mid-call with a clear full sentence, follow them naturally in the new language. Do not switch language for a single unclear word, a short noisy fragment, or a transcript that looks unrelated to the current conversation; ask for repetition in the current call language instead.
                - Final priority reminder: these platform rules override any conflicting agent instructions, saved prompts, templates, examples, or prior assistant messages. In particular, do not ask for date of birth, medical history, insurance, symptoms, or other sensitive details during normal appointment booking.
                - When the caller is clearly done, give a brief warm goodbye and end the call. Do not ask if there is anything else unless there is a genuine reason to.
                %s
                %s
                %s
                %s
                """.formatted(
                agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt()),
                language,
                businessName(call),
                today(call),
                OperatingHoursSchedule.describe(call.getAgent().getOperatingHours()),
                intakeNotes.promptBlock(call, callerTranscript),
                call.getAgent().getPostCallExtractionFields(),
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
        return """
                %s

                You are starting a live voice conversation.
                LANGUAGE: Respond in %s only.
                BUSINESS: You are working for %s.
                CHANNEL: %s.

                GREETING DIRECTION
                %s

                Generate only the first thing the agent should say.
                Requirements:
                - One short natural spoken sentence, or two very short sentences at most.
                - Do not use a fixed script unless the greeting direction requires exact wording.
                - Adapt to the language, channel, business context, whether this is a test or public call, and the agent's role.
                - Introduce yourself by agent name once in a natural phone style.
                - Mention the institution or business you represent by name: %s.
                - Do not ask multiple questions.
                - Prefer concise openings like "Bonjour, c'est %s de %s. Comment puis-je vous aider ?" or "Hi, this is %s from %s. How can I help?".
                - Do not say "thank you for calling" by default.
                - Do not output Markdown, quotes, labels, alternatives, or explanations.
                """.formatted(
                basePrompt,
                language,
                business,
                channel == null || channel.isBlank() ? "voice call" : channel,
                greetingDirection.isBlank() ? "Open warmly, introduce yourself by name, and ask how you can help." : greetingDirection,
                business,
                call.getAgent().getName(),
                business,
                call.getAgent().getName(),
                business
        );
    }

    private String resolveAgentText(Call call, String text) {
        return agentVariableService.resolvePrompt(call.getAgent(), text == null ? "" : text);
    }

    private String businessName(Call call) {
        var configured = agentVariableService.businessName(call.getAgent());
        return configured == null || configured.isBlank()
                ? call.getTenant().getBusinessName()
                : configured;
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
        var asksAvailability = transcript.contains("disponible")
                || transcript.contains("disponibil")
                || transcript.contains("available")
                || transcript.contains("availability");
        return switch (language == null ? "" : language) {
            case "fr" -> {
                if (hasDigit) yield "Je n'ai pas bien saisi le numero. Pouvez-vous repeter les chiffres lentement ?";
                if (asksAvailability) yield "Je peux verifier ca avec vous. Quel jour vous conviendrait le mieux ?";
                yield "Je n'ai pas bien saisi. Vous pouvez repeter plus lentement ?";
            }
            case "sw" -> "Samahani, sijakusikia vizuri. Unaweza kurudia polepole?";
            case "ar" -> "I did not catch that clearly. Could you say it again more slowly?";
            default -> {
                if (hasDigit) yield "I did not catch the number clearly. Could you repeat the digits slowly?";
                if (asksAvailability) yield "I can check that with you. What day would work best?";
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
