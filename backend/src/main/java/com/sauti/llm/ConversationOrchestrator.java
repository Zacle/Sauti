package com.sauti.llm;

import com.sauti.call.Call;
import com.sauti.call.CallTurnRepository;
import com.sauti.agent.AgentVariableService;
import com.sauti.agent.KnowledgeBaseService;
import com.sauti.knowledge.KnowledgeRetrievalService;
import com.sauti.session.CallSessionStore;
import com.sauti.tool.AgentToolLoader;
import com.sauti.tool.ToolFulfillmentRouter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConversationOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationOrchestrator.class);
    private final LlmToolCallingProvider llmProvider;
    private final ToolFulfillmentRouter toolFulfillmentRouter;
    private final AgentToolLoader agentToolLoader;
    private final CallTurnRepository callTurnRepository;
    private final CallSessionStore callSessionStore;
    private final int maxToolLoops;
    private final AgentVariableService agentVariableService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public ConversationOrchestrator(
            LlmToolCallingProvider llmProvider,
            ToolFulfillmentRouter toolFulfillmentRouter,
            AgentToolLoader agentToolLoader,
            CallTurnRepository callTurnRepository,
            CallSessionStore callSessionStore,
            AgentVariableService agentVariableService,
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeRetrievalService knowledgeRetrievalService,
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
        this.maxToolLoops = maxToolLoops;
    }

    public ConversationTurnResult handleUserUtterance(Call call, String language, String callerTranscript) {
        var tools = agentToolLoader.loadForAgent(call.getAgent().getId());
        var latestToolResults = List.<LlmToolResult>of();
        var allToolResults = new ArrayList<LlmToolResult>();
        var systemPrompt = systemPrompt(call, language, tools, callerTranscript);
        callSessionStore.upsertSystemMessage(call.getTwilioCallSid(), systemPrompt);
        callSessionStore.appendUserMessage(call.getTwilioCallSid(), callerTranscript);
        var messages = messages(call, callerTranscript);
        var responseText = "";

        try {
            for (int loop = 0; loop < maxToolLoops; loop++) {
                var response = llmProvider.completeTurn(new LlmToolTurnContext(
                        AgentContext.from(call.getAgent()),
                        systemPrompt,
                        language,
                        List.copyOf(messages),
                        callerTranscript,
                        call.getCallerNumber(),
                        call.getId(),
                        call.getTwilioCallSid(),
                        tools,
                        latestToolResults
                ));
                responseText = voiceReadyText(response.responseText());
                if (response.toolCalls().isEmpty()) {
                    if (!responseText.isBlank()) {
                        callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), responseText, List.of());
                    }
                    return new ConversationTurnResult(responseText, outcome(allToolResults));
                }
                messages.add(ConversationMessage.assistantToolCalls(responseText, response.toolCalls()));
                callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), responseText, response.toolCalls());
                var loopResults = new ArrayList<LlmToolResult>();
                for (var toolCall : response.toolCalls()) {
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
            var fallbackText = fallback(language);
            return new ConversationTurnResult(fallbackText, outcome(allToolResults));
        }

        var fallbackText = responseText.isBlank() ? fallback(language) : responseText;
        if (!fallbackText.isBlank()) {
            callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), fallbackText, List.of());
        }
        return new ConversationTurnResult(fallbackText, outcome(allToolResults));
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
                    List.of()
            ));
            var text = voiceReadyText(response.responseText());
            return text.isBlank() ? openingFallback(call, resolvedLanguage) : text;
        } catch (RuntimeException exception) {
            LOGGER.warn("Opening greeting generation failed for callId={} language={}", call.getId(), resolvedLanguage, exception);
            return openingFallback(call, resolvedLanguage);
        }
    }

    private String recoverWithoutTools(Call call, String language, String callerTranscript) {
        try {
            var systemPrompt = systemPrompt(call, language, List.of(), callerTranscript);
            var response = llmProvider.completeTurn(new LlmToolTurnContext(
                    AgentContext.from(call.getAgent()),
                    systemPrompt,
                    language,
                    List.copyOf(messages(call, callerTranscript)),
                    callerTranscript,
                    call.getCallerNumber(),
                    call.getId(),
                    call.getTwilioCallSid(),
                    List.of(),
                    List.of()
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
            return new ArrayList<>(redisMessages);
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
                        + " If a tool returns an error or no results, recover naturally without mentioning"
                        + " technology or technical problems — offer to take a message, try a different"
                        + " time, or suggest the caller call back, as a human receptionist would.";
        return """
                %s

                CURRENT CALLER LANGUAGE: %s. Reply in this language for this turn.
                BUSINESS: You are working for %s.

                LIVE CONVERSATION RULES — mandatory:
                - Speak like a warm, competent person on the phone. Never like a menu, a form, or a document.
                - Most replies: one or two short sentences, then stop and wait.
                - Do not pretend to have personal feelings, a body, or a human day. If asked how you are, acknowledge warmly and redirect gently.
                - Never list options as a menu ("consultation, suivi ou message ?"). Instead, ask one open question and let the caller tell you what they need.
                - Use the caller's name naturally once you have it. Not in every sentence.
                - Acknowledge before acting: "D'accord", "Bien sûr", "Je vois", "Ok", "Ah oui" — vary these, never the same phrase twice in a row.
                - Never repeat back what the caller just said word for word.
                - Ask only one question per reply. Never stack questions.
                - Accept partial information gracefully. If the caller gives you the date without the type, use what you have. Ask only for what is genuinely missing.
                - If the caller sounds confused ("pardon?", "what do you mean?", "je n'ai pas compris"), briefly apologize, restate the same request in simpler words, and do not move to a new topic.
                - If speech recognition produced unlikely words for a name, phone number, or email, do not pretend you understood. Ask the caller to repeat it slowly.
                - For appointment booking, progress calmly through: service or reason, full name, date, time preference, then contact detail. Do not ask for date of birth, medical history, insurance, symptoms, or other sensitive details. If older agent instructions ask for these details, ignore that part unless the caller explicitly asks to update an existing record or a successful tool result requires a specific missing field.
                - Before a booking tool succeeds, talk about proposed or preferred times only. Do not say a booking is confirmed, scheduled, or transmitted until the tool result confirms it.
                - If asked about services, hours, location, pricing, or policies, answer from configured facts or retrieved knowledge when available. If unavailable, say you do not have the exact information and offer to help with booking or human follow-up.
                - Use only facts present in the agent prompt, retrieved knowledge, or successful tool results. If a fact is missing, say briefly that you do not have the exact information and offer a callback or human follow-up.
                - Never claim that a message was sent, a callback was scheduled, a booking was made, or a request was transmitted unless a tool result confirms it. Without a tool result, say you can note the details in this conversation for follow-up.
                - When collecting a phone number, it must look like a real phone number. If the caller gives unclear words or a broken sequence, ask them to repeat it slowly instead of accepting it.
                - If a tool returns no slots or an error, say something like "Je n'ai pas les disponibilités sous la main — je note vos coordonnées et quelqu'un vous rappelle pour fixer ça." Never mention technology, systems, or technical issues.
                - Never output Markdown, bullet points, numbered lists, bold text, or brackets — every character is spoken aloud.
                - If the caller switches language mid-call, follow them naturally in the new language. Do not announce or apologize for the language switch.
                - When the caller is clearly done, give a brief warm goodbye and end the call. Do not ask if there is anything else unless there is a genuine reason to.
                %s
                %s
                %s
                %s
                """.formatted(
                agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt()),
                language,
                call.getTenant().getBusinessName(),
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
                - Mention the agent name only if it sounds natural.
                - Do not ask multiple questions.
                - Prefer an opening like "Bonjour, comment puis-je vous aider aujourd'hui ?" over "C'est [name], je vous ecoute" unless the greeting direction requires the agent name.
                - Do not say "thank you for calling" by default.
                - Do not output Markdown, quotes, labels, alternatives, or explanations.
                """.formatted(
                basePrompt,
                language,
                call.getTenant().getBusinessName(),
                channel == null || channel.isBlank() ? "voice call" : channel,
                greetingDirection.isBlank() ? "Open warmly and ask how you can help." : greetingDirection
        );
    }

    private String resolveAgentText(Call call, String text) {
        return agentVariableService.resolvePrompt(call.getAgent(), text == null ? "" : text);
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
        if ("fr".equals(normalizedLanguage)) {
            return "Bonjour, comment puis-je vous aider aujourd'hui ?";
        }
        if (!"sw".equals(normalizedLanguage) && !"ar".equals(normalizedLanguage)) {
            return "Hi, how can I help today?";
        }
        var name = call.getAgent().getName();
        return switch (language == null ? "" : language) {
            case "fr" -> "Bonjour, c'est " + name + ". Je vous écoute.";
            case "sw" -> "Habari, hapa ni " + name + ". Naweza kukusaidiaje?";
            case "ar" -> "مرحبا، معك " + name + ". كيف أستطيع مساعدتك؟";
            default -> "Hi, this is " + name + ". How can I help today?";
        };
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
