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

    public ToolFulfillmentRouter(
            AgentToolRepository agentToolRepository,
            SautiCalendarFulfillment calendarFulfillment,
            WebhookToolFulfillment webhookFulfillment,
            SautiSmsFulfillment smsFulfillment,
            TwilioTransferFulfillment transferFulfillment,
            DuringCallIntegrationFulfillment integrationFulfillment,
            NoopFulfillment noopFulfillment
    ) {
        this.agentToolRepository = agentToolRepository;
        this.fulfillments = Map.of(
                "sauti_calendar", calendarFulfillment,
                "webhook", webhookFulfillment,
                "sauti_sms", smsFulfillment,
                "twilio_transfer", transferFulfillment,
                "sauti_integration", integrationFulfillment,
                "noop", noopFulfillment
        );
    }

    public LlmToolResult route(Call call, LlmToolCall toolCall) {
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
        return fulfillment.execute(call, toolConfig, toolCall);
    }
}
