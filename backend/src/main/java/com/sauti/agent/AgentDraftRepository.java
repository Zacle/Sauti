package com.sauti.agent;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Storage for unpublished agent drafts; one per agent. */
public interface AgentDraftRepository extends JpaRepository<AgentDraft, UUID> {
    Optional<AgentDraft> findByAgentIdAndTenantId(UUID agentId, UUID tenantId);
}
