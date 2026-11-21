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
            int defaultBookingDurationMinutes,
            String defaultLanguage,
            List<String> supportedLanguages,
            List<String> escalationPhrases,
            List<GeneratedVariable> variables
    ) {
    }

    /**
     * @param value a value stated in the brief, offered for the owner to confirm; empty when the brief
     *              doesn't state it. Values not found in the brief's own words are discarded.
     */
    public record GeneratedVariable(
            String key,
            String label,
            String description,
            boolean required,
            String value
    ) {
        public GeneratedVariable(String key, String label, String description, boolean required) {
            this(key, label, description, required, "");
        }
    }
}
