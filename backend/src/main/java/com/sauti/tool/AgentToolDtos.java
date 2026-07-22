package com.sauti.tool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.UUID;

public final class AgentToolDtos {
    private AgentToolDtos() {
    }

    public record AgentToolRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{1,63}$") String toolName,
            @NotBlank String toolDescription,
            @NotNull Map<String, Object> parametersSchema,
            @NotBlank String fulfillmentType,
            String actionEffect,
            String confirmationPolicy,
            String webhookUrl,
            String webhookMethod,
            String authType,
            String authCredential,
            String authHeaderName,
            String calendarType,
            UUID calendarCredentialId,
            boolean active,
            int displayOrder
    ) {
    }

    public record AgentToolResponse(
            UUID id,
            UUID agentId,
            String toolName,
            String toolDescription,
            Map<String, Object> parametersSchema,
            String fulfillmentType,
            String actionEffect,
            String confirmationPolicy,
            String webhookUrl,
            String webhookMethod,
            String authType,
            boolean authConfigured,
            String authHeaderName,
            String calendarType,
            UUID calendarCredentialId,
            boolean active,
            int displayOrder
    ) {
        public static AgentToolResponse from(AgentTool tool) {
            return new AgentToolResponse(
                    tool.getId(),
                    tool.getAgent().getId(),
                    tool.getToolName(),
                    tool.getToolDescription(),
                    tool.getParametersSchema(),
                    tool.getFulfillmentType(),
                    tool.getActionEffect(),
                    tool.getConfirmationPolicy(),
                    tool.getWebhookUrl(),
                    tool.getWebhookMethod(),
                    tool.getAuthType(),
                    tool.getAuthCredential() != null && !tool.getAuthCredential().isBlank(),
                    tool.getAuthHeaderName(),
                    tool.getCalendarType(),
                    tool.getCalendarCredentialId(),
                    tool.isActive(),
                    tool.getDisplayOrder()
            );
        }
    }
}
