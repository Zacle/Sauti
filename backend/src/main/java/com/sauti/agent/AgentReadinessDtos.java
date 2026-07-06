package com.sauti.agent;

import java.util.List;
import java.util.UUID;

public final class AgentReadinessDtos {
    private AgentReadinessDtos() {
    }

    public record AgentReadinessResponse(
            UUID agentId,
            boolean businessDetailsComplete,
            boolean calendarRequired,
            boolean calendarConfigured,
            boolean phoneNumberConfigured,
            boolean webVoiceConfigured,
            boolean whatsappConfigured,
            boolean channelConfigured,
            boolean active,
            boolean readyToActivate,
            String nextStep,
            List<String> missingRequiredVariables
    ) {
    }
}
