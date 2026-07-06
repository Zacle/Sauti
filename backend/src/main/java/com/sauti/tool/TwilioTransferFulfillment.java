package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.call.CallTransferService;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import org.springframework.stereotype.Component;

@Component
public class TwilioTransferFulfillment implements ToolFulfillment {
    private final CallTransferService callTransferService;

    public TwilioTransferFulfillment(CallTransferService callTransferService) {
        this.callTransferService = callTransferService;
    }

    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        try {
            var reason = String.valueOf(toolCall.arguments().getOrDefault("reason", "caller requested a human"));
            return LlmToolResult.success(toolCall, callTransferService.request(call, reason));
        } catch (Exception exception) {
            return LlmToolResult.error(toolCall, exception.getMessage());
        }
    }
}
