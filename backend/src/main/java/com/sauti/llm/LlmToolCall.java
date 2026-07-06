package com.sauti.llm;

import java.util.Map;

public record LlmToolCall(
        String id,
        String name,
        Map<String, Object> arguments
) {
}
