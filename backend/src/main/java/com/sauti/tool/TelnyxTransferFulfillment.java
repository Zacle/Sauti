package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.call.CallTransferService;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import org.springframework.stereotype.Component;

@Component
public class TelnyxTransferFulfillment implements ToolFulfillment {
    private final CallTransferService callTransferService;

    public TelnyxTransferFulfillment(CallTransferService callTransferService) {
        this.callTransferService = callTransferService;
    }

    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        try {
            var reason = String.valueOf(toolCall.arguments().getOrDefault("reason", "caller requested a human"));
            var requested = callTransferService.request(call, reason);
            // Recording the request is not enough: dial the team member so the caller is actually connected.
            if (Boolean.TRUE.equals(requested.get("transferPending"))) callTransferService.dialAfterCommit(call.getId());
            return LlmToolResult.success(toolCall, requested);
        } catch (Exception exception) {
            return LlmToolResult.error(toolCall, exception.getMessage());
        }
    }
}
