package com.sauti.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AgentTemplateDtos {
    private AgentTemplateDtos() {
    }

    public record AgentTemplateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 80) String category,
            @NotBlank String greetingMessage,
            @NotBlank String systemPrompt,
            @NotBlank @Size(max = 10) String defaultLanguage,
            @NotEmpty List<@NotBlank @Size(max = 10) String> supportedLanguages,
            String configurationJson,
            boolean published
    ) {
    }

    public record AgentTemplateResponse(
            UUID id,
            UUID tenantId,
            String scope,
            boolean editable,
            String name,
            String description,
            String category,
            String greetingMessage,
            String systemPrompt,
            String defaultLanguage,
            List<String> supportedLanguages,
            String configurationJson,
            int version,
            boolean published,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static AgentTemplateResponse from(AgentTemplate template) {
            boolean system = template.isSystemTemplate();
            return new AgentTemplateResponse(
                    template.getId(),
                    system ? null : template.getTenant().getId(),
                    system ? "system" : "tenant",
                    !system,
                    template.getName(),
                    template.getDescription(),
                    template.getCategory(),
                    template.getGreetingMessage(),
                    template.getSystemPrompt(),
                    template.getDefaultLanguage(),
                    template.getSupportedLanguages(),
                    template.getConfigurationJson(),
                    template.getVersion(),
                    template.isPublished(),
                    template.getCreatedAt(),
                    template.getUpdatedAt()
            );
        }
    }

    public record CreateAgentFromTemplateRequest(
            @Size(max = 100) String name,
            @Size(max = 100) String timezone,
            @Size(max = 30) String humanTransferNumber
    ) {
    }
}
