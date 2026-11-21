package com.sauti.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

/**
 * Part of an agent's configuration owned by another module (its tools, documents, integrations),
 * kept in each version beside the agent settings and business details.
 *
 * <p>Implemented by the owning module, so the agent module stores and restores the part without
 * knowing its shape.
 */
public interface AgentConfigurationPart {
    /** Stable name of the part in stored versions, such as "tools". */
    String key();

    /** What the part looks like now. Must never contain secrets. */
    JsonNode capture(UUID tenantId, UUID agentId);

    /**
     * Puts the part back as it was captured, without publishing a change of its own.
     *
     * @return anything that could not be put back, in words for the owner; empty when all of it was
     */
    List<String> restore(UUID tenantId, UUID agentId, JsonNode captured);
}
