package com.sauti.tool;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolRepository extends JpaRepository<AgentTool, UUID> {
    List<AgentTool> findByAgent_IdOrderByDisplayOrderAsc(UUID agentId);

    List<AgentTool> findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(UUID agentId);

    Optional<AgentTool> findByIdAndAgent_Tenant_Id(UUID id, UUID tenantId);

    Optional<AgentTool> findByAgent_IdAndToolNameAndIsActiveTrue(UUID agentId, String toolName);

    boolean existsByAgent_IdAndToolName(UUID agentId, String toolName);
}
