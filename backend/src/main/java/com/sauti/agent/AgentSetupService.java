package com.sauti.agent;

import com.sauti.agent.AgentSetupDtos.AgentSetupRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves an agent and its variables in one transaction, whichever way it was started: by hand,
 * from a template or from an AI brief. Either everything is saved or nothing is, and the live
 * assistant is refreshed once, after the variables exist.
 *
 * <p>Creating and updating here is publishing: each one is recorded as a numbered version, and any
 * version can be published again with {@link #restore}.
 */
@Service
public class AgentSetupService {
    private final AgentService agents;
    private final AgentTemplateService templates;
    private final AgentVariableService variables;
    private final AgentVersionService versions;

    public AgentSetupService(AgentService agents, AgentTemplateService templates, AgentVariableService variables,
                             AgentVersionService versions) {
        this.agents = agents;
        this.templates = templates;
        this.variables = variables;
        this.versions = versions;
    }

    @Transactional
    public Agent create(UUID tenantId, AgentSetupRequest request) {
        return create(tenantId, request, null);
    }

    @Transactional
    public Agent create(UUID tenantId, AgentSetupRequest request, String actor) {
        var agent = agents.create(tenantId, request.agent());
        if (request.templateId() != null) templates.applyVariableDefinitions(tenantId, request.templateId(), agent);
        applyVariables(tenantId, agent, request);
        versions.recordPublished(tenantId, agent, request, actor, "Created");
        return agent;
    }

    @Transactional
    public Agent update(UUID tenantId, UUID agentId, AgentSetupRequest request) {
        return publish(tenantId, agentId, request, null, null);
    }

    /** Applies the configuration to live calls and records it as the next version. */
    @Transactional
    public Agent publish(UUID tenantId, UUID agentId, AgentSetupRequest request, String actor, String note) {
        var agent = agents.update(tenantId, agentId, request.agent());
        applyVariables(tenantId, agent, request);
        versions.recordPublished(tenantId, agent, request, actor, note);
        return agent;
    }

    /** Publishes an earlier version again; it becomes the newest version, so nothing is lost. */
    @Transactional
    public Agent restore(UUID tenantId, UUID agentId, int version, String actor) {
        return publish(tenantId, agentId, versions.snapshot(tenantId, agentId, version), actor,
                "Restored version " + version);
    }

    private void applyVariables(UUID tenantId, Agent agent, AgentSetupRequest request) {
        for (var definition : request.variablesOrEmpty()) {
            variables.create(tenantId, agent.getId(), definition.key(), definition.label(),
                    definition.description(), definition.value(), definition.required());
        }
        variables.updateKnown(tenantId, agent.getId(), request.valuesOrEmpty());
    }
}
