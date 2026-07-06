package com.sauti.llm;

import com.sauti.agent.Agent;
import java.util.List;
import java.util.UUID;

public record AgentContext(
        UUID id,
        String name,
        boolean bookingEnabled,
        String timezone,
        String humanTransferNumber,
        List<String> escalationPhrases,
        String llmTier
) {
    public AgentContext {
        escalationPhrases = escalationPhrases == null ? List.of() : List.copyOf(escalationPhrases);
    }

    public static AgentContext from(Agent agent) {
        return new AgentContext(
                agent.getId(),
                agent.getName(),
                agent.isBookingEnabled(),
                agent.getTimezone(),
                agent.getHumanTransferNumber(),
                agent.getEscalationPhrases(),
                agent.getLlmTier()
        );
    }
}
