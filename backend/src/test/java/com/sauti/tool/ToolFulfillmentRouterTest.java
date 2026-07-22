package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import com.sauti.agent.Agent;
import com.sauti.call.Call;
import com.sauti.integration.DuringCallIntegrationFulfillment;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.session.CallSessionStore;
import com.sauti.session.ConversationState;
import com.sauti.session.PendingAction;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolFulfillmentRouterTest {
    @Test
    void blocksUnresolvedSideEffectsAcrossUnrelatedBusinessDomains() {
        for (var effect : new ToolActionEffect[] {
                ToolActionEffect.DATA_WRITE,
                ToolActionEffect.EXTERNAL_COMMUNICATION,
                ToolActionEffect.FINANCIAL,
                ToolActionEffect.TRANSFER,
                ToolActionEffect.TERMINAL
        }) {
            var fixture = fixture("perform_" + effect.value(), effect, ToolConfirmationPolicy.NONE);

            var result = fixture.router.route(fixture.call, new LlmToolCall(
                    "call-" + effect.value(), fixture.tool.getToolName(),
                    Map.of("question_handling", "answer_before_action")
            ));

            assertThat(result.success()).isTrue();
            assertThat(result.result())
                    .containsEntry("status", "action_deferred")
                    .containsEntry("actionPerformed", false)
                    .containsEntry("effect", effect.value())
                    .containsEntry("reason", "unresolved_customer_request");
            verify(fixture.webhook, never()).execute(any(), any(), any());
        }
    }

    @Test
    void requiresExplicitConfirmationFromPolicyNotFromTheToolName() {
        var fixture = fixture(
                "synchronize_enterprise_customer_profile",
                ToolActionEffect.DATA_WRITE,
                ToolConfirmationPolicy.EXPLICIT
        );

        var result = fixture.router.route(fixture.call, new LlmToolCall(
                "confirmation-policy", fixture.tool.getToolName(),
                Map.of("question_handling", "ready_for_action", "confirmation_state", "not_confirmed")
        ));

        assertThat(result.result())
                .containsEntry("status", "action_deferred")
                .containsEntry("reason", "verified_confirmation_required");
        verify(fixture.webhook, never()).execute(any(), any(), any());
    }

    @Test
    void aModelConfirmedFlagCannotExecuteWithoutAStoredProposalAndLaterCallerApproval() {
        var fixture = fixture(
                "cancel_customer_order",
                ToolActionEffect.DATA_WRITE,
                ToolConfirmationPolicy.EXPLICIT
        );

        var result = fixture.router.route(fixture.call, new LlmToolCall(
                "unverified-confirmation", fixture.tool.getToolName(), Map.of(
                        "order_id", "ORDER-42",
                        "question_handling", "ready_for_action",
                        "confirmation_state", "confirmed"
                )
        ));

        assertThat(result.result())
                .containsEntry("status", "action_deferred")
                .containsEntry("actionPerformed", false)
                .containsEntry("reason", "verified_confirmation_required");
        verify(fixture.sessions).updatePendingAction(
                "policy-call",
                new PendingAction("cancel_customer_order", Map.of("order_id", "ORDER-42"), 0)
        );
        verify(fixture.webhook, never()).execute(any(), any(), any());
    }

    @Test
    void protocolSuccessDoesNotTurnAReviewIntoACompletedBusinessAction() {
        var policy = new ToolActionPolicy();
        var agent = mock(Agent.class);
        var tool = new AgentTool(
                agent, "book_slot", "Booking", Map.of(), "noop", true, 1
        );
        tool.configureActionPolicy(ToolActionEffect.DATA_WRITE, ToolConfirmationPolicy.VERIFIED_REVIEW);
        var call = new LlmToolCall("booking-review", "book_slot", Map.of());

        var result = policy.factualOutcome(tool, LlmToolResult.success(call, Map.of(
                "status", "booking_review_required",
                "bookingCreated", false
        )));

        assertThat(result.result())
                .containsEntry("actionPerformed", false)
                .containsEntry("action", "book_slot");
    }

    @Test
    void aConfirmedFarewellCanEndWithoutAnArtificialSecondConfirmationTurn() {
        var fixture = fixture(
                "finish_conversation",
                ToolActionEffect.TERMINAL,
                ToolConfirmationPolicy.EXPLICIT
        );
        var providerResult = LlmToolResult.success(
                new LlmToolCall("end", fixture.tool.getToolName(), Map.of()),
                Map.of("ended", true)
        );
        when(fixture.webhook.execute(any(), any(), any())).thenReturn(providerResult);

        var result = fixture.router.route(fixture.call, new LlmToolCall(
                "end", fixture.tool.getToolName(), Map.of(
                        "question_handling", "ready_for_action",
                        "confirmation_state", "confirmed"
                )
        ));

        assertThat(result.result())
                .containsEntry("ended", true)
                .containsEntry("actionPerformed", true);
        verify(fixture.sessions, never()).updatePendingAction(any(), any());
    }

    @Test
    void permitsReadOnlyToolsWithoutMutationFields() {
        var fixture = fixture("search_policy_documents", ToolActionEffect.READ_ONLY, ToolConfirmationPolicy.NONE);
        var expected = LlmToolResult.success(
                new LlmToolCall("read", fixture.tool.getToolName(), Map.of()),
                Map.of("status", "found")
        );
        when(fixture.webhook.execute(any(), any(), any())).thenReturn(expected);

        var result = fixture.router.route(fixture.call, new LlmToolCall(
                "read", fixture.tool.getToolName(), Map.of("query", "refund policy")
        ));

        assertThat(result.result()).containsEntry("status", "found");
        verify(fixture.webhook).execute(any(), any(), any());
    }

    @Test
    void removesPlatformPolicyFieldsBeforeCallingBusinessFulfillment() {
        var fixture = fixture("update_case_management_system", ToolActionEffect.DATA_WRITE, ToolConfirmationPolicy.EXPLICIT);
        var expected = LlmToolResult.success(
                new LlmToolCall("write", fixture.tool.getToolName(), Map.of()),
                Map.of("status", "updated")
        );
        when(fixture.webhook.execute(any(), any(), any())).thenReturn(expected);
        when(fixture.sessions.conversationState("policy-call")).thenReturn(Optional.of(
                new ConversationState(
                        Map.of("review_decision", "approved"),
                        ConversationState.SUBJECT_UNKNOWN,
                        ConversationState.INTENT_ACTIVE,
                        8
                )
        ));
        when(fixture.sessions.pendingAction("policy-call")).thenReturn(Optional.of(
                new PendingAction(
                        fixture.tool.getToolName(), Map.of("case_id", "CASE-42"), 7
                )
        ));
        when(fixture.sessions.consumeConfirmedAction(
                "policy-call", fixture.tool.getToolName(), Map.of("case_id", "CASE-42")
        )).thenReturn(true);

        var result = fixture.router.route(fixture.call, new LlmToolCall(
                "write", fixture.tool.getToolName(), Map.of(
                        "case_id", "CASE-42",
                        "question_handling", "ready_for_action",
                        "confirmation_state", "confirmed"
                )
        ));

        assertThat(result.result())
                .containsEntry("status", "updated")
                .containsEntry("actionPerformed", true)
                .containsEntry("effect", "data_write")
                .containsEntry("action", "update_case_management_system");
        verify(fixture.sessions).consumeConfirmedAction(
                "policy-call", fixture.tool.getToolName(), Map.of("case_id", "CASE-42")
        );
        verify(fixture.webhook).execute(any(), any(), argThat(call ->
                call.arguments().equals(Map.of("case_id", "CASE-42"))
        ));
    }

    private Fixture fixture(
            String name,
            ToolActionEffect effect,
            ToolConfirmationPolicy confirmation
    ) {
        var repository = mock(AgentToolRepository.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getAgent()).thenReturn(agent);
        when(call.getTwilioCallSid()).thenReturn("policy-call");
        when(agent.getId()).thenReturn(UUID.randomUUID());
        var tool = new AgentTool(
                agent, name, "Policy test", Map.of("type", "object", "properties", Map.of()),
                "webhook", true, 1
        );
        tool.configureActionPolicy(effect, confirmation);
        when(repository.findByAgent_IdAndToolNameAndIsActiveTrue(agent.getId(), name))
                .thenReturn(Optional.of(tool));

        var calendar = mock(SautiCalendarFulfillment.class);
        var webhook = mock(WebhookToolFulfillment.class);
        var sessions = mock(CallSessionStore.class);
        var router = new ToolFulfillmentRouter(
                repository,
                calendar,
                webhook,
                mock(SautiSmsFulfillment.class),
                mock(TwilioTransferFulfillment.class),
                mock(DuringCallIntegrationFulfillment.class),
                mock(NoopFulfillment.class),
                mock(ConversationStateTool.class),
                new ToolActionPolicy(sessions)
        );
        return new Fixture(router, call, tool, webhook, sessions);
    }

    private record Fixture(
            ToolFulfillmentRouter router,
            Call call,
            AgentTool tool,
            WebhookToolFulfillment webhook,
            CallSessionStore sessions
    ) { }
}
