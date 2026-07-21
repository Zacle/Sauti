package com.sauti.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentVariableService;
import com.sauti.call.Call;
import com.sauti.call.CallTurn;
import com.sauti.call.CallTurnRepository;
import com.sauti.session.CallSessionStore;
import com.sauti.tenant.Tenant;
import com.sauti.tool.AgentToolLoader;
import com.sauti.tool.ToolFulfillmentRouter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationOrchestratorTest {
    @Test
    void convertsWrittenMarkdownIntoContinuousSpokenText() {
        assertThat(ConversationOrchestrator.voiceReadyText(
                "**Oh, I see…**\n- What day works best?\n- Morning or afternoon?"
        )).isEqualTo("Oh, I see… What day works best? Morning or afternoon?");
    }

    @Test
    void loopsThroughToolCallAndReturnsFollowUpResponse() {
        var provider = new ScriptedProvider();
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4);
        var call = activeCall();
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt())).thenReturn("Prompt");
        when(callSessionStore.conversationHistory(call.getTwilioCallSid()))
                .thenReturn(List.of(new ConversationMessage("user", "I want to book tomorrow")));
        when(toolLoader.loadForAgent(call.getAgent().getId())).thenReturn(List.of(
                new LlmToolDefinition("check_availability", "Check slots", Map.of("type", "object")),
                new LlmToolDefinition("book_slot", "Book slot", Map.of("type", "object"))
        ));
        var executedTools = new ArrayList<String>();
        when(router.route(any(Call.class), any(LlmToolCall.class))).thenAnswer(invocation -> {
            LlmToolCall toolCall = invocation.getArgument(1);
            executedTools.add(toolCall.name());
            return LlmToolResult.success(toolCall, "book_slot".equals(toolCall.name())
                    ? Map.of("bookingCreated", true)
                    : Map.of("ok", true));
        });

        var result = orchestrator.handleUserUtterance(call, "en", "I want to book tomorrow");

        assertThat(result.responseText()).isEqualTo("Your appointment is confirmed.");
        assertThat(result.outcome()).isEqualTo("booking_made");
        assertThat(executedTools).containsExactly("check_availability", "book_slot");
        assertThat(provider.contexts).hasSize(2);
        assertThat(provider.contexts.get(0).toolResults()).isEmpty();
        assertThat(provider.contexts.get(0).systemPrompt())
                .startsWith("Prompt")
                .contains("CURRENT CALLER LANGUAGE: en")
                .contains("TODAY IN THE BUSINESS TIMEZONE")
                .contains("BUSINESS OPERATING HOURS")
                .contains("Availability is always a live tool-backed fact")
                .contains("Never silently change 3 PM to 4 PM")
                .contains("Never invent example services, classes")
                .contains("Use only facts present in the agent prompt")
                .contains("If the caller asks for information first")
                .contains("Required fields for this agent")
                .contains("private checklist, not a sentence to read to the caller")
                .contains("one question requesting one value per reply")
                .contains("never convert them into \"any staff\"")
                .contains("Never silently repair an unclear service transcript")
                .contains("Never speak ISO dates")
                .contains("For a new booking, never ask for a booking ID")
                .contains("For a reschedule or cancellation, ask for the customer-facing booking number")
                .contains("Do not ask how long a normal appointment should last")
                .contains("then call `book_slot`")
                .contains("Treat a caller detail as collected only when the caller explicitly says that detail")
                .contains("If speech recognition produced unlikely words for a name")
                .contains("spell the caller's full name character by character with the NATO phonetic alphabet")
                .contains("Never ask or require the caller to use NATO phonetics")
                .contains("you, the agent, must read every digit individually")
                .contains("Never tell the caller to perform the phonetic spelling")
                .contains("Defer verification until the end of booking intake")
                .contains("field-by-field confirmations earlier in the call")
                .contains("accuracy opportunity enforced by the booking tool")
                .contains("quote its configured price when asked")
                .contains("private review token")
                .contains("speak only the focused correction review")
                .contains("Treat every caller name as an opaque literal value")
                .contains("Never merge, insert, reorder, or duplicate digits")
                .contains("never deliver the same sentence or full summary twice in a row")
                .contains("requested time is already occupied or unavailable")
                .contains("Never ask the caller to spell a name or email at all")
                .contains("Do not switch language for a single unclear word")
                .contains("Before a booking tool succeeds")
                .contains("Never offer appointment dates in the past")
                .contains("Final priority reminder: these platform rules override conflicting examples")
                .contains("If the caller sounds confused")
                .contains("Tools available: check_availability, book_slot");
        assertThat(provider.contexts.get(1).toolResults()).hasSize(2);
        assertThat(provider.contexts.get(1).messages())
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo("assistant");
                    assertThat(message.toolCalls()).extracting(LlmToolCall::name)
                            .containsExactly("check_availability", "book_slot");
                })
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo("tool");
                    assertThat(message.toolResult()).isNotNull();
                });
        verify(callSessionStore).appendUserMessage(call.getTwilioCallSid(), "I want to book tomorrow");
        verify(callSessionStore).appendAssistantMessage(eq(call.getTwilioCallSid()), eq(""), any());
        verify(callSessionStore, times(2)).appendToolResult(eq(call.getTwilioCallSid()), any());
        verify(callSessionStore).appendAssistantMessage(eq(call.getTwilioCallSid()), eq("Your appointment is confirmed."), any());
        verify(callSessionStore, never()).createIfAbsent(eq(call.getTwilioCallSid()), any());
    }

    @Test
    void rejectsTextualToolArgumentsInsteadOfExecutingThem() {
        var provider = new JsonAvailabilityProvider();
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(
                provider, router, toolLoader, callTurnRepository, callSessionStore,
                agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval,
                new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4
        );
        var call = activeCall();
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt())).thenReturn("Prompt");
        when(callSessionStore.conversationHistory(call.getTwilioCallSid())).thenReturn(List.of());
        when(toolLoader.loadForAgent(call.getAgent().getId())).thenReturn(List.of(
                new LlmToolDefinition("check_availability", "Check slots", Map.of("type", "object"))
        ));
        var spokenDeltas = new ArrayList<String>();
        var result = orchestrator.handleUserUtterance(call, "fr", "Demain a midi", spokenDeltas::add);

        assertThat(result.responseText()).isEqualTo(
                "Desole, je n'ai pas pu terminer ma reponse. Pouvez-vous repeter votre question, s'il vous plait ?"
        );
        assertThat(spokenDeltas).containsExactly(result.responseText());
        assertThat(spokenDeltas).noneMatch(text -> text.contains("{\"date\""));
        verify(router, never()).route(any(Call.class), any(LlmToolCall.class));
        verify(callSessionStore, never()).appendAssistantMessage(
                eq(call.getTwilioCallSid()), eq("{\"date\":\"2026-07-18\",\"time_preference\":\"12:00\"}"), any()
        );
    }

    @Test
    void suppressesSplitProtocolMarkersAndRetriesWithoutTools() {
        var provider = new ProtocolLeakProvider();
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(
                provider, router, toolLoader, callTurnRepository, callSessionStore,
                agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval,
                new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4
        );
        var call = activeCall();
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt())).thenReturn("Prompt");
        when(callSessionStore.conversationHistory(call.getTwilioCallSid()))
                .thenReturn(List.of(new ConversationMessage("user", "How much is a men's haircut?")));
        when(toolLoader.loadForAgent(call.getAgent().getId())).thenReturn(List.of(
                new LlmToolDefinition("get_business_hours", "Get business hours", Map.of("type", "object"))
        ));

        var spokenDeltas = new ArrayList<String>();
        var result = orchestrator.handleUserUtterance(
                call, "en", "How much is a men's haircut?", spokenDeltas::add
        );

        assertThat(spokenDeltas).containsExactly("A men's haircut is $5.");
        assertThat(result.responseText()).isEqualTo("A men's haircut is $5.");
        assertThat(provider.contexts).hasSize(2);
        assertThat(provider.contexts.get(0).tools()).hasSize(1);
        assertThat(provider.contexts.get(1).tools()).isEmpty();
        verify(callSessionStore, never()).appendAssistantMessage(
                call.getTwilioCallSid(), "analysis to=functions.get_business_hours code", List.of()
        );
        verify(callSessionStore).appendAssistantMessage(
                call.getTwilioCallSid(), "A men's haircut is $5.", List.of()
        );
    }

    @Test
    void fallsBackToCallTurnsAndIncludesCurrentCallerTranscriptWhenRedisHistoryIsMissing() {
        var provider = new SingleResponseProvider("What time works best for you?");
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4);
        var call = activeCall();
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt())).thenReturn("Prompt");
        when(callSessionStore.conversationHistory(call.getTwilioCallSid())).thenReturn(List.of());
        when(callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId())).thenReturn(List.of(
                new CallTurn(call, 1, "I need a checkup.", "I can help with that.", "en", 0, 0, 0, false)
        ));
        when(toolLoader.loadForAgent(call.getAgent().getId())).thenReturn(List.of());

        var result = orchestrator.handleUserUtterance(call, "en", "Tomorrow morning please.");

        assertThat(result.responseText()).isEqualTo("What time works best for you?");
        assertThat(provider.contexts).hasSize(1);
        assertThat(provider.contexts.get(0).messages())
                .extracting(ConversationMessage::role, ConversationMessage::content)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user", "I need a checkup."),
                        org.assertj.core.groups.Tuple.tuple("assistant", "I can help with that."),
                        org.assertj.core.groups.Tuple.tuple("user", "Tomorrow morning please.")
                );
    }

    @Test
    void returnsLocalizedFallbackWhenProviderFails() {
        var provider = new FailingProvider();
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4);
        var call = activeCall();
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt())).thenReturn("Prompt");
        var toolCall = new LlmToolCall("availability-1", "check_availability", Map.of("date", "2030-01-15"));
        when(callSessionStore.conversationHistory(call.getTwilioCallSid()))
                .thenReturn(List.of(new ConversationMessage("user", "Bonjour, je voudrais prendre rendez-vous.")))
                .thenReturn(List.of(
                        new ConversationMessage("user", "Bonjour, je voudrais prendre rendez-vous."),
                        ConversationMessage.assistantToolCalls("Je verifie.", List.of(toolCall)),
                        ConversationMessage.toolResult(LlmToolResult.error(toolCall, "Calendar unavailable")),
                        new ConversationMessage("user", "Bonjour, je voudrais prendre rendez-vous.")
                ));
        when(toolLoader.loadForAgent(call.getAgent().getId())).thenReturn(List.of());

        var result = orchestrator.handleUserUtterance(call, "fr", "Bonjour, je voudrais prendre rendez-vous.");

        assertThat(result.responseText()).isEqualTo("Je n'ai pas bien saisi. Vous pouvez repeter plus lentement ?");
        verify(callSessionStore, never()).appendAssistantMessage(call.getTwilioCallSid(), result.responseText(), List.of());
    }

    @Test
    void retriesWithoutToolsBeforeUsingFailureFallback() {
        var provider = new FailsWithToolsProvider("Pour quelle date souhaitez-vous prendre rendez-vous ?");
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4);
        var call = activeCall();
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt())).thenReturn("Prompt");
        var toolCall = new LlmToolCall("availability-1", "check_availability", Map.of("date", "2030-01-15"));
        when(callSessionStore.conversationHistory(call.getTwilioCallSid()))
                .thenReturn(List.of(new ConversationMessage("user", "Bonjour, je voudrais prendre rendez-vous.")))
                .thenReturn(List.of(
                        new ConversationMessage("user", "Bonjour, je voudrais prendre rendez-vous."),
                        ConversationMessage.assistantToolCalls("Je verifie.", List.of(toolCall)),
                        ConversationMessage.toolResult(LlmToolResult.error(toolCall, "Calendar unavailable")),
                        new ConversationMessage("user", "Bonjour, je voudrais prendre rendez-vous.")
                ));
        when(toolLoader.loadForAgent(call.getAgent().getId())).thenReturn(List.of(
                new LlmToolDefinition("check_availability", "Check slots", Map.of("type", "object"))
        ));

        var result = orchestrator.handleUserUtterance(call, "fr", "Bonjour, je voudrais prendre rendez-vous.");

        assertThat(result.responseText()).isEqualTo("Pour quelle date souhaitez-vous prendre rendez-vous ?");
        assertThat(provider.contexts).hasSize(2);
        assertThat(provider.contexts.get(0).tools()).hasSize(1);
        assertThat(provider.contexts.get(1).tools()).isEmpty();
        assertThat(provider.contexts.get(1).messages())
                .noneMatch(message -> "tool".equals(message.role()))
                .noneMatch(message -> message.toolCalls() != null && !message.toolCalls().isEmpty());
        verify(callSessionStore).appendAssistantMessage(call.getTwilioCallSid(), result.responseText(), List.of());
    }

    @Test
    void usesPhoneNativeFrenchFallbackOpeningWithInstitutionWhenGreetingGenerationFails() {
        var provider = new FailingProvider();
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4);
        var call = activeCall();
        when(agentVariableService.businessName(call.getAgent())).thenReturn("X-Fit");

        var greeting = orchestrator.generateOpeningGreeting(call, "fr", "voice call");

        assertThat(greeting).isEqualTo("Bonjour, c'est Amina de X-Fit. Comment puis-je vous aider ?");
    }

    @Test
    void omitsWorkspaceNameWhenAgentBusinessIsMissing() {
        var provider = new SingleResponseProvider("Hi, this is Amina. How can I help?");
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4);
        var call = activeCall();
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt())).thenReturn("Prompt");
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getGreetingMessage())).thenReturn("Open warmly.");
        when(toolLoader.loadForAgent(call.getAgent().getId())).thenReturn(List.of(
                new LlmToolDefinition("check_availability", "Check availability", Map.of("type", "object")),
                new LlmToolDefinition("book_slot", "Book an appointment", Map.of("type", "object"))
        ));

        var greeting = orchestrator.generateOpeningGreeting(call, "en", "browser test call");

        assertThat(greeting).isEqualTo("Hi, this is Amina. How can I help?");
        assertThat(provider.contexts).hasSize(1);
        assertThat(provider.contexts.get(0).systemPrompt())
                .contains("Never use the workspace/account name")
                .contains("Introduce yourself by agent name only")
                .contains("ACTIVE CAPABILITIES")
                .contains("create appointments or reservations")
                .contains("check live availability")
                .doesNotContain("Demo Clinic");
    }

    @Test
    void realtimeInstructionsKeepTheConfiguredBusinessRoleAndHours() {
        var provider = new SingleResponseProvider("unused");
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(
                provider, router, toolLoader, callTurnRepository, callSessionStore,
                agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval,
                new com.sauti.call.CallIntakeNoteService(callTurnRepository), 4
        );
        var call = activeCall();
        var prompt = "You are Alec, the virtual assistant for X-Fit.\n"
                + "- Hours: Mon 09:00-17:00; Tue 09:00-17:00; Wed 09:00-17:00";
        when(agentVariableService.resolvePrompt(call.getAgent(), call.getAgent().getSystemPrompt())).thenReturn(prompt);
        when(agentVariableService.businessName(call.getAgent())).thenReturn("X-Fit");
        when(agentVariableService.conversationContext(call.getAgent())).thenReturn("""
                - services_and_prices (Services and prices) — exact approved catalog; keep each service and its price together:
                  * men hairstyle: $5
                  * women hairstyle: $8
                  * nails: $4
                - cancellation_policy (Cancellation policy): Give 24 hours notice
                """);
        when(toolLoader.loadForAgent(call.getAgent().getId())).thenReturn(List.of());

        var instructions = orchestrator.realtimeInstructions(call, "en", "When are you open?");

        assertThat(instructions)
                .contains("You are working for X-Fit")
                .contains("not a general-purpose adviser")
                .contains("Never deny a capability explicitly granted")
                .contains("Mon 09:00-17:00; Tue 09:00-17:00; Wed 09:00-17:00")
                .contains("men hairstyle: $5", "women hairstyle: $8", "nails: $4")
                .contains("cancellation_policy", "Give 24 hours notice")
                .contains("Never say one of those configured facts is unavailable")
                .contains("Use only the current caller language")
                .contains("Ordinary replies must be bare natural speech")
                .contains("section heading such as ANSWER, FINAL ANSWER, or RESPONSE")
                .contains("Use tools only through native function calls")
                .contains("Keep the person speaking separate from the person receiving the service")
                .contains("Do not invent your own review preamble")
                .contains("Never emit JSON");
    }

    private Call activeCall() {
        var tenant = new Tenant("Demo Clinic", "owner@example.com", "SN");
        var agent = new Agent(tenant, "Amina", "Bonjour", "Prompt");
        agent.update(
                "Amina",
                "Bonjour",
                "Prompt",
                "en",
                List.of("en", "fr"),
                "+221770000000",
                List.of("speak to a human"),
                true,
                "Africa/Dakar",
                ""
        );
        agent.activate();
        return new Call(tenant, agent, "CA123", "+221771234567", "inbound");
    }

    private static final class ScriptedProvider implements LlmToolCallingProvider {
        private final List<LlmToolTurnContext> contexts = new ArrayList<>();

        @Override
        public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
            contexts.add(context);
            if (context.toolResults().isEmpty()) {
                return new LlmToolTurnResponse("", List.of(
                        new LlmToolCall("availability-1", "check_availability", Map.of("date", "2030-01-15")),
                        new LlmToolCall("booking-1", "book_slot", Map.of("appointment_at", "2030-01-15T10:00:00Z"))
                ));
            }
            return new LlmToolTurnResponse("Your appointment is confirmed.", List.of());
        }
    }

    private static final class SingleResponseProvider implements LlmToolCallingProvider {
        private final String response;
        private final List<LlmToolTurnContext> contexts = new ArrayList<>();

        private SingleResponseProvider(String response) {
            this.response = response;
        }

        @Override
        public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
            contexts.add(context);
            return new LlmToolTurnResponse(response, List.of());
        }
    }

    private static final class JsonAvailabilityProvider implements LlmToolCallingProvider {
        @Override
        public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
            if (context.toolResults().isEmpty()) {
                return new LlmToolTurnResponse(
                        "{\"date\":\"2026-07-18\",\"time_preference\":\"12:00\"}",
                        List.of()
                );
            }
            return new LlmToolTurnResponse("Le creneau de demain a midi est disponible.", List.of());
        }
    }

    private static final class ProtocolLeakProvider implements LlmToolCallingProvider {
        private final List<LlmToolTurnContext> contexts = new ArrayList<>();

        @Override
        public LlmToolTurnResponse streamTurn(
                LlmToolTurnContext context,
                java.util.function.Consumer<String> textDeltaConsumer
        ) {
            contexts.add(context);
            textDeltaConsumer.accept("ana");
            textDeltaConsumer.accept("lysis to=functions.get_business_hours code");
            return new LlmToolTurnResponse("analysis to=functions.get_business_hours code", List.of());
        }

        @Override
        public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
            contexts.add(context);
            return new LlmToolTurnResponse("A men's haircut is $5.", List.of());
        }
    }

    private static final class FailingProvider implements LlmToolCallingProvider {
        @Override
        public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
            throw new IllegalStateException("Provider unavailable");
        }
    }

    private static final class FailsWithToolsProvider implements LlmToolCallingProvider {
        private final String response;
        private final List<LlmToolTurnContext> contexts = new ArrayList<>();

        private FailsWithToolsProvider(String response) {
            this.response = response;
        }

        @Override
        public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
            contexts.add(context);
            if (!context.tools().isEmpty()) {
                throw new IllegalStateException("Tool provider unavailable");
            }
            return new LlmToolTurnResponse(response, List.of());
        }
    }

}
