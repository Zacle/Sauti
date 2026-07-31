package com.sauti.tool;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentToolRepository extends JpaRepository<AgentTool, UUID> {
    List<AgentTool> findByAgent_IdOrderByDisplayOrderAsc(UUID agentId);

    List<AgentTool> findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(UUID agentId);

    Optional<AgentTool> findByIdAndAgent_Tenant_Id(UUID id, UUID tenantId);

    Optional<AgentTool> findByAgent_IdAndToolNameAndIsActiveTrue(UUID agentId, String toolName);

    @Query("""
            select distinct tool.agent.id
            from AgentTool tool
            where tool.agent.tenant.id = :tenantId
              and tool.calendarCredentialId = :credentialId
              and tool.toolName = :toolName
              and tool.isActive = true
              and lower(tool.calendarType) = 'google'
            """)
    List<UUID> findActiveAgentIdsSharingCalendar(
            @Param("tenantId") UUID tenantId,
            @Param("credentialId") UUID credentialId,
            @Param("toolName") String toolName
    );

    boolean existsByAgent_IdAndToolName(UUID agentId, String toolName);
}
