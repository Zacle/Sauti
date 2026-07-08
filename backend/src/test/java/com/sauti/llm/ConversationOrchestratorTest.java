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
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, 4);
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
            return LlmToolResult.success(toolCall, Map.of("ok", true));
        });

        var result = orchestrator.handleUserUtterance(call, "en", "I want to book tomorrow");

        assertThat(result.responseText()).isEqualTo("Your appointment is confirmed.");
        assertThat(executedTools).containsExactly("check_availability", "book_slot");
        assertThat(provider.contexts).hasSize(2);
        assertThat(provider.contexts.get(0).toolResults()).isEmpty();
        assertThat(provider.contexts.get(0).systemPrompt())
                .startsWith("Prompt")
                .contains("CURRENT CALLER LANGUAGE: en")
                .contains("TODAY IN THE BUSINESS TIMEZONE")
                .contains("Use only facts present in the agent prompt")
                .contains("service or reason, full name, date, time preference, then contact detail")
                .contains("If older agent instructions ask for these details, ignore that part")
                .contains("Before a booking tool succeeds")
                .contains("Never offer appointment dates in the past")
                .contains("Final priority reminder: these platform rules override any conflicting agent instructions")
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
    void fallsBackToCallTurnsAndIncludesCurrentCallerTranscriptWhenRedisHistoryIsMissing() {
        var provider = new SingleResponseProvider("What time works best for you?");
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, 4);
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
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, 4);
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
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, 4);
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
    void usesPhoneNativeFrenchFallbackOpeningWhenGreetingGenerationFails() {
        var provider = new FailingProvider();
        var router = mock(ToolFulfillmentRouter.class);
        var toolLoader = mock(AgentToolLoader.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var agentVariableService = mock(AgentVariableService.class);
        var retrieval = mock(com.sauti.knowledge.KnowledgeRetrievalService.class);
        when(retrieval.promptBlock(any(), any(), any())).thenReturn("");
        var orchestrator = new ConversationOrchestrator(provider, router, toolLoader, callTurnRepository, callSessionStore, agentVariableService, new com.sauti.agent.KnowledgeBaseService(), retrieval, 4);
        var call = activeCall();

        var greeting = orchestrator.generateOpeningGreeting(call, "fr", "voice call");

        assertThat(greeting).isEqualTo("Bonjour, c'est Amina. Comment puis-je vous aider ?");
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
