package com.sauti.agent;

import java.util.Map;
import java.util.UUID;

/**
 * Workspace-wide business facts, keyed like agent variables (business_address, services, faq...).
 * An agent's own filled variable wins; these fill whatever the agent leaves empty. The business
 * profile module provides it, so agents don't depend on how the profile is stored.
 */
public interface BusinessFacts {
    BusinessFacts NONE = workspaceId -> Map.of();

    Map<String, String> factsFor(UUID workspaceId);
}
