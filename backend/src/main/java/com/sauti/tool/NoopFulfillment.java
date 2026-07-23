package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.call.VoiceOutputGuard;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NoopFulfillment implements ToolFulfillment {
    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        if ("end_call".equals(toolCall.name())) {
            var result = new LinkedHashMap<String, Object>();
            result.put("ended", true);
            result.put("outcome", toolCall.arguments().getOrDefault("outcome", "completed").toString());
            result.put("summary", toolCall.arguments().getOrDefault("summary", "").toString());
            var farewell = VoiceOutputGuard.speechText(
                    toolCall.arguments().getOrDefault("spoken_farewell", "").toString()
            );
            if (!farewell.isBlank() && farewell.length() <= 300) {
                result.put("spokenResponse", farewell);
            }
            return LlmToolResult.success(toolCall, Map.copyOf(result));
        }
        return LlmToolResult.success(toolCall, Map.of("noop", true));
    }
}
