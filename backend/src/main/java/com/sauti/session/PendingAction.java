package com.sauti.session;

import java.util.Map;

/**
 * Server-owned proposal for an external side effect. The proposal is created
 * before asking the caller for confirmation and may be consumed only by a
 * later conversation-state revision that explicitly approves the same action.
 */
public record PendingAction(
        String toolName,
        Map<String, Object> arguments,
        long proposedAtRevision
) {
    public PendingAction {
        toolName = toolName == null ? "" : toolName.trim();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        proposedAtRevision = Math.max(0, proposedAtRevision);
    }

    public boolean matches(String candidateToolName, Map<String, Object> candidateArguments) {
        return toolName.equals(candidateToolName == null ? "" : candidateToolName.trim())
                && arguments.equals(candidateArguments == null ? Map.of() : candidateArguments);
    }
}
