package com.sauti.llm;

import com.sauti.tool.AgentTool;
import java.util.Map;

public record LlmToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
) {
    public static LlmToolDefinition from(AgentTool tool) {
        return new LlmToolDefinition(
                tool.getToolName(),
                tool.getToolDescription(),
                tool.getParametersSchema()
        );
    }
}
