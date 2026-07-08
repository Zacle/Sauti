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
            LOGGER.warn("Conversation turn failed for callId={} language={}", call.getId(), language, exception);
            var fallbackText = fallback(language, callerTranscript, call.getAgent().getName());
            callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), fallbackText, List.of());
            return new ConversationTurnResult(fallbackText, outcome(allToolResults));
        }

        var fallbackText = responseText.isBlank() ? fallback(language, callerTranscript, call.getAgent().getName()) : responseText;
        if (!fallbackText.isBlank()) {
            callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), fallbackText, List.of());
        }
        return new ConversationTurnResult(fallbackText, outcome(allToolResults));
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
                        + ". Use only these tools.";
        return """
                %s

                LANGUAGE: Respond in %s only. Do not switch languages.
                BUSINESS: You are working for %s.

                LIVE CONVERSATION POLICY — apply judgment from meaning and context, not keyword matching:
                - Before replying, silently infer the caller's communicative intent, emotional state, certainty, and whether their thought is complete. Do not reveal this analysis.
                - Choose the next conversational move that best advances the call: acknowledge, answer, clarify, ask for the next necessary detail, execute a tool, or close the call. Do not perform every move in every response.
                - Generate acknowledgements naturally from the caller's meaning, language, tone, and prior turns. Never select them from a fixed script, and omit them when they add no social value.
                - Treat speech recognition as fallible. If wording appears truncated, contradictory, ambiguous, or acoustically misrecognized, ask one precise clarification instead of guessing.
                - Never invent or silently repair names, dates, times, phone digits, addresses, services, prices, consent, or other operational facts. Confirm critical details before using a tool.
                - If the caller is still listing related information, let them finish. Accept multiple details supplied in one turn; do not force them to repeat each item separately.
                - Match the caller's conversational register while remaining professional. Natural fragments, contractions, brief hesitation, and light punctuation are allowed when context calls for them, but do not manufacture verbal tics.
                - Most replies should be one or two short spoken sentences. Give only the information needed at this moment, then yield the floor.
                - Do not mechanically repeat or formally restate what the caller just said. Summarize only to resolve ambiguity or confirm consequential information.
                - Ask at most one new question at a time, unless reading back a compact set of details for final confirmation.
                - Never output Markdown, lists, tables, stage directions, emotion labels, or internal reasoning because every character will be spoken aloud.
                - When the caller's goal is satisfied or they clearly indicate they are done, give one context-appropriate closing and end the call.
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
            case "fr" -> "Je suis desole, je n'ai pas pu terminer cette demande. Pouvez-vous reformuler ?";
            case "sw" -> "Samahani, sikuweza kukamilisha ombi hilo. Unaweza kulisema kwa njia nyingine?";
            case "ar" -> "عذرا، لم أتمكن من إكمال هذا الطلب. هل يمكنك إعادة صياغته؟";
            default -> "I am sorry, I could not complete that request. Could you rephrase?";
        };
    }

    private String fallback(String language, String callerTranscript, String agentName) {
        var transcript = callerTranscript == null ? "" : java.text.Normalizer
                .normalize(callerTranscript.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        var name = callerName(transcript);
        return switch (language == null ? "" : language) {
            case "fr" -> frenchFallback(transcript, name, agentName);
            case "sw" -> "Nimekupata. Ninaweza kukusaidiaje leo?";
            case "ar" -> "I understand. How can I help today?";
            default -> name.isBlank()
                    ? "I understand. How can I help today?"
                    : "Thanks, " + name + ". How can I help today?";
        };
    }

    private String frenchFallback(String transcript, String callerName, String agentName) {
        if (transcript.contains("rendez-vous")) {
            return "Bien sur. Quelle date et quelle heure souhaitez-vous pour ce rendez-vous ?";
        }
        if (transcript.contains("verifier")) {
            return "Bien sur. Que souhaitez-vous verifier exactement ?";
        }
        if (!callerName.isBlank()) {
            return "Merci, " + callerName + ". Comment puis-je vous aider aujourd'hui ?";
        }
        var resolvedAgentName = agentName == null || agentName.isBlank() ? "votre agent" : agentName;
        return "J'ai bien compris. Je suis " + resolvedAgentName + ". Comment puis-je vous aider aujourd'hui ?";
    }

    private String callerName(String transcript) {
        var patterns = List.of(
                "\\b(?:my name is|i am|i'm)\\s+([a-z][a-z\\-']{1,40})\\b",
                "\\b(?:je m appelle|je mappelle|mon nom c est|moi c est)\\s+([a-z][a-z\\-']{1,40})\\b"
        );
        for (var pattern : patterns) {
            var matcher = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(transcript);
            if (matcher.find()) {
                var name = matcher.group(1);
                return name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + name.substring(1);
            }
        }
        return "";
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
