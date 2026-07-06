package com.sauti.llm;

import java.util.List;

public record LlmToolTurnResponse(
        String responseText,
        List<LlmToolCall> toolCalls
) {
    public LlmToolTurnResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
