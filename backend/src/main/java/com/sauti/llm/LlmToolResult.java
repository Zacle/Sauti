package com.sauti.llm;

import java.util.Map;

public record LlmToolResult(
        String toolCallId,
        String name,
        boolean success,
        Map<String, Object> result,
        String error
) {
    public static LlmToolResult success(LlmToolCall toolCall, Map<String, Object> result) {
        return new LlmToolResult(toolCall.id(), toolCall.name(), true, result, "");
    }

    public static LlmToolResult error(LlmToolCall toolCall, String error) {
        return new LlmToolResult(toolCall.id(), toolCall.name(), false, Map.of(), error);
    }
}
