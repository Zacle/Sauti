package com.sauti.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AgentDraftGenerationDtos {
    private AgentDraftGenerationDtos() {
    }

    public record GenerateAgentDraftRequest(@NotBlank @Size(max = 2000) String brief) {
    }

    public record GeneratedAgentDraftResponse(
            String name,
            String description,
            String greetingMessage,
            String systemPrompt,
            boolean bookingEnabled,
            String defaultLanguage,
            List<String> supportedLanguages,
            List<String> escalationPhrases,
            List<GeneratedVariable> variables
    ) {
    }

    public record GeneratedVariable(
            String key,
            String label,
            String description,
            boolean required
    ) {
    }
}
