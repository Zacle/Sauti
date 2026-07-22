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
                .containsEntry("reason", "explicit_confirmation_required");
        verify(fixture.webhook, never()).execute(any(), any(), any());
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

        var result = fixture.router.route(fixture.call, new LlmToolCall(
                "write", fixture.tool.getToolName(), Map.of(
                        "case_id", "CASE-42",
                        "question_handling", "ready_for_action",
                        "confirmation_state", "confirmed"
                )
        ));

        assertThat(result.result()).containsEntry("status", "updated");
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
        var router = new ToolFulfillmentRouter(
                repository,
                calendar,
                webhook,
                mock(SautiSmsFulfillment.class),
                mock(TwilioTransferFulfillment.class),
                mock(DuringCallIntegrationFulfillment.class),
                mock(NoopFulfillment.class),
                mock(ConversationStateTool.class),
                new ToolActionPolicy()
        );
        return new Fixture(router, call, tool, webhook);
    }

    private record Fixture(
            ToolFulfillmentRouter router,
            Call call,
            AgentTool tool,
            WebhookToolFulfillment webhook
    ) { }
}
