package com.sauti.tool;

import com.sauti.llm.LlmToolDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentToolLoader {
    private final AgentToolRepository agentToolRepository;

    public AgentToolLoader(AgentToolRepository agentToolRepository) {
        this.agentToolRepository = agentToolRepository;
    }

    @Transactional(readOnly = true)
    public List<LlmToolDefinition> loadForAgent(UUID agentId) {
        return agentToolRepository.findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(agentId)
                .stream()
                .map(LlmToolDefinition::from)
                .toList();
    }
}
