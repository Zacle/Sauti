package com.sauti.llm;

import java.util.List;

public record ConversationMessage(
        String role,
        String content,
        List<LlmToolCall> toolCalls,
        LlmToolResult toolResult
) {
    public ConversationMessage(String role, String content) {
        this(role, content, List.of(), null);
    }

    public static ConversationMessage assistantToolCalls(String content, List<LlmToolCall> toolCalls) {
        return new ConversationMessage("assistant", content, List.copyOf(toolCalls), null);
    }

    public static ConversationMessage toolResult(LlmToolResult result) {
        return new ConversationMessage("tool", "", List.of(), result);
    }
}
