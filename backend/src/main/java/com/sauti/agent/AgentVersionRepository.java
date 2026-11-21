package com.sauti.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Storage for published agent versions. */
public interface AgentVersionRepository extends JpaRepository<AgentVersion, UUID> {
    List<AgentVersion> findTop50ByAgentIdOrderByVersionDesc(UUID agentId);

    Optional<AgentVersion> findByAgentIdAndVersion(UUID agentId, int version);

    @Query("select coalesce(max(v.version), 0) from AgentVersion v where v.agentId = ?1")
    int latestVersion(UUID agentId);
}
