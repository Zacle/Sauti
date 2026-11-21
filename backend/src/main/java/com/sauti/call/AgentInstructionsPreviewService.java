package com.sauti.call;

import com.sauti.agent.AgentRepository;
import com.sauti.agent.AgentSetupDtos.InstructionsPreview;
import com.sauti.agent.AgentVariableService;
import com.sauti.llm.LlmToolDefinition;
import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shows exactly what the live voice assistant is given for an agent: the same blueprint that is
 * sent to Telnyx, built from the saved configuration, plus warnings about missing details.
 */
@Service
public class AgentInstructionsPreviewService {
    private final AgentRepository agents;
    private final ManagedVoiceAgentBlueprintFactory blueprints;
    private final CallPipelineService callPipeline;
    private final AgentVariableService variables;
    private final com.sauti.tool.AgentToolLoader tools;

    public AgentInstructionsPreviewService(AgentRepository agents, ManagedVoiceAgentBlueprintFactory blueprints,
                                           CallPipelineService callPipeline, AgentVariableService variables,
                                           com.sauti.tool.AgentToolLoader tools) {
        this.tools = tools;
        this.agents = agents;
        this.blueprints = blueprints;
        this.callPipeline = callPipeline;
        this.variables = variables;
    }

    @Transactional(readOnly = true)
    public InstructionsPreview preview(UUID tenantId, UUID agentId, String language) {
        var agent = agents.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        var requested = language == null || language.isBlank()
                ? agent.getDefaultLanguage()
                : language.trim().toLowerCase(Locale.ROOT);
        if (!agent.getSupportedLanguages().contains(requested)) {
            throw new IllegalArgumentException("Language " + requested + " is not supported by this agent");
        }
        var greeting = callPipeline.managedVoiceGreeting(agent, requested);
        var blueprint = blueprints.create(agent, greeting, requested);
        var rules = tools.actionRules(agent.getId());
        // Platform tools (conversation state, document search) only read, so the caller is never asked.
        var toolRules = blueprint.tools().stream().map(LlmToolDefinition::name)
                .map(name -> {
                    var rule = rules.get(name);
                    return rule == null
                            ? new com.sauti.agent.AgentSetupDtos.ToolRule(name, "read_only", "none")
                            : new com.sauti.agent.AgentSetupDtos.ToolRule(name, rule.effect(), rule.confirmation());
                })
                .toList();
        return new InstructionsPreview(
                requested,
                blueprint.greeting(),
                blueprint.instructions(),
                blueprint.tools().stream().map(LlmToolDefinition::name).toList(),
                variables.placeholderWarnings(agent),
                toolRules
        );
    }
}
