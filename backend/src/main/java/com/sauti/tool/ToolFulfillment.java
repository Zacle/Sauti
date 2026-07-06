package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;

public interface ToolFulfillment {
    LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall);
}
