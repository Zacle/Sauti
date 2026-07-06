package com.sauti.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentVariableRepository extends JpaRepository<AgentVariable, UUID> {
    List<AgentVariable> findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(UUID agentId);
    Optional<AgentVariable> findByAgentIdAndKey(UUID agentId, String key);
}
