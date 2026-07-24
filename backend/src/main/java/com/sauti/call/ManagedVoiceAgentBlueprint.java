package com.sauti.call;

import com.sauti.llm.LlmToolDefinition;
import java.util.List;

public record ManagedVoiceAgentBlueprint(
        String name,
        String greeting,
        String instructions,
        String language,
        List<String> supportedLanguages,
        List<LlmToolDefinition> tools,
        int maxCallDurationSeconds,
        double interruptionSensitivity,
        int endpointingMilliseconds,
        List<String> boostedKeywords
) {
}
