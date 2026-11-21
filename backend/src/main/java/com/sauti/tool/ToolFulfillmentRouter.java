package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.util.Map;
import com.sauti.integration.DuringCallIntegrationFulfillment;
import org.springframework.stereotype.Service;

@Service
public class ToolFulfillmentRouter {
    private final AgentToolRepository agentToolRepository;
    private final Map<String, ToolFulfillment> fulfillments;
    private final ConversationStateTool conversationStateTool;
    private final ToolActionPolicy actionPolicy;
    private final KnowledgeSearchTool knowledgeSearchTool;
    private final ToolActionLog actionLog;

    @org.springframework.beans.factory.annotation.Autowired
    public ToolFulfillmentRouter(
            AgentToolRepository agentToolRepository,
            SautiCalendarFulfillment calendarFulfillment,
            WebhookToolFulfillment webhookFulfillment,
            SautiSmsFulfillment smsFulfillment,
            TelnyxTransferFulfillment transferFulfillment,
            DuringCallIntegrationFulfillment integrationFulfillment,
            NoopFulfillment noopFulfillment,
            ConversationStateTool conversationStateTool,
            ToolActionPolicy actionPolicy,
            KnowledgeSearchTool knowledgeSearchTool,
            ToolActionLog actionLog
    ) {
        this.actionLog = actionLog;
        this.agentToolRepository = agentToolRepository;
        this.conversationStateTool = conversationStateTool;
        this.actionPolicy = actionPolicy;
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.fulfillments = Map.of(
                "sauti_calendar", calendarFulfillment,
                "webhook", webhookFulfillment,
                "sauti_sms", smsFulfillment,
                "call_transfer", transferFulfillment,
                "sauti_integration", integrationFulfillment,
                "noop", noopFulfillment
        );
    }

    public ToolFulfillmentRouter(
            AgentToolRepository agentToolRepository,
            SautiCalendarFulfillment calendarFulfillment,
            WebhookToolFulfillment webhookFulfillment,
            SautiSmsFulfillment smsFulfillment,
            TelnyxTransferFulfillment transferFulfillment,
            DuringCallIntegrationFulfillment integrationFulfillment,
            NoopFulfillment noopFulfillment,
            ConversationStateTool conversationStateTool,
            ToolActionPolicy actionPolicy
    ) {
        this(agentToolRepository, calendarFulfillment, webhookFulfillment, smsFulfillment, transferFulfillment,
                integrationFulfillment, noopFulfillment, conversationStateTool, actionPolicy,
                new KnowledgeSearchTool(KnowledgeSearch.NONE), ToolActionLog.disabled());
    }

    public LlmToolResult route(Call call, LlmToolCall toolCall) {
        if (ConversationStateTool.NAME.equals(toolCall.name())) {
            return conversationStateTool.execute(call, toolCall);
        }
        if (KnowledgeSearchTool.NAME.equals(toolCall.name())) {
            return knowledgeSearchTool.execute(call, toolCall);
        }
        var toolConfig = agentToolRepository
                .findByAgent_IdAndToolNameAndIsActiveTrue(call.getAgent().getId(), toolCall.name())
                .orElse(null);
        if (toolConfig == null) {
            return LlmToolResult.error(toolCall, "Unknown or inactive tool: " + toolCall.name());
        }
        var fulfillment = fulfillments.get(toolConfig.getFulfillmentType());
        if (fulfillment == null) {
            return LlmToolResult.error(toolCall, "Unknown fulfillment type: " + toolConfig.getFulfillmentType());
        }
        var effect = ToolSafetyFloor.effect(toolConfig);
        var key = ToolActionLog.invocationKey(toolCall);
        var deferred = actionPolicy.guard(call, toolConfig, toolCall);
        if (deferred.isPresent()) {
            if ("verified_confirmation_required".equals(deferred.get().result().get("reason"))) {
                actionLog.awaitingConfirmation(call, toolConfig, effect, key);
            }
            return deferred.get();
        }
        if (!effect.isSideEffecting()) {
            return actionPolicy.factualOutcome(toolConfig,
                    fulfillment.execute(call, toolConfig, actionPolicy.businessCall(toolConfig, toolCall)));
        }
        // Each side effect runs at most once per provider tool call, even if it is delivered again.
        var repeated = actionLog.begin(call, toolConfig, effect, toolCall, key);
        if (repeated.isPresent()) return repeated.get();
        LlmToolResult result;
        try {
            result = actionPolicy.factualOutcome(toolConfig,
                    fulfillment.execute(call, toolConfig, actionPolicy.businessCall(toolConfig, toolCall)));
        } catch (RuntimeException failure) {
            actionLog.finish(call, toolCall, key, LlmToolResult.error(toolCall, "failed"));
            throw failure;
        }
        actionLog.finish(call, toolCall, key, result);
        return result;
    }
}
