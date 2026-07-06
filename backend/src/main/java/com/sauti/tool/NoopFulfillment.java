package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NoopFulfillment implements ToolFulfillment {
    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        if ("end_call".equals(toolCall.name())) {
            return LlmToolResult.success(toolCall, Map.of(
                    "ended", true,
                    "outcome", toolCall.arguments().getOrDefault("outcome", "completed").toString(),
                    "summary", toolCall.arguments().getOrDefault("summary", "").toString()
            ));
        }
        return LlmToolResult.success(toolCall, Map.of("noop", true));
    }
}
