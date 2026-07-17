package com.sauti.llm;

import java.util.List;
import java.util.UUID;

public record LlmToolTurnContext(
        AgentContext agent,
        String systemPrompt,
        String language,
        List<ConversationMessage> messages,
        String callerTranscript,
        String callerPhone,
        UUID callId,
        String callSid,
        List<LlmToolDefinition> tools,
        List<LlmToolResult> toolResults,
        String requiredToolName
) {
}
