package com.sauti.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentSetupDtos.AgentSetupRequest;
import com.sauti.agent.AgentSetupDtos.DraftView;
import com.sauti.agent.AgentSetupDtos.VariableDefinition;
import com.sauti.agent.AgentSetupDtos.VersionView;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drafts and published versions of an agent. Live calls only ever see what was published; a draft
 * waits on the server until it is published, and every publish is kept so it can be restored.
 */
@Service
public class AgentVersionService {
    private final AgentRepository agents;
    private final AgentDraftRepository drafts;
    private final AgentVersionRepository versions;
    private final AgentVariableService variables;
    private final ObjectMapper json;

    public AgentVersionService(AgentRepository agents, AgentDraftRepository drafts, AgentVersionRepository versions,
                               AgentVariableService variables, ObjectMapper json) {
        this.agents = agents;
        this.drafts = drafts;
        this.versions = versions;
        this.variables = variables;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Optional<DraftView> draft(UUID tenantId, UUID agentId) {
        requireAgent(tenantId, agentId);
        return drafts.findByAgentIdAndTenantId(agentId, tenantId)
                .map(draft -> new DraftView(read(draft.getSetupJson()), draft.getSavedBy(), draft.getUpdatedAt()));
    }

    @Transactional
    public DraftView saveDraft(UUID tenantId, UUID agentId, AgentSetupRequest request, String actor) {
        requireAgent(tenantId, agentId);
        AgentVariableService.requireWellFormedPlaceholders(
                request.agent().systemPrompt(), request.agent().greetingMessage());
        var draft = drafts.findByAgentIdAndTenantId(agentId, tenantId).orElseGet(() -> new AgentDraft(agentId, tenantId));
        draft.replace(write(request), actor, now());
        drafts.save(draft);
        return new DraftView(request, actor, draft.getUpdatedAt());
    }

    @Transactional
    public void discardDraft(UUID tenantId, UUID agentId) {
        requireAgent(tenantId, agentId);
        drafts.findByAgentIdAndTenantId(agentId, tenantId).ifPresent(drafts::delete);
    }

    @Transactional(readOnly = true)
    public List<VersionView> history(UUID tenantId, UUID agentId) {
        requireAgent(tenantId, agentId);
        var list = versions.findTop50ByAgentIdOrderByVersionDesc(agentId);
        var latest = list.isEmpty() ? 0 : list.get(0).getVersion();
        return list.stream().map(version -> new VersionView(version.getVersion(), version.getNote(),
                version.getPublishedBy(), version.getPublishedAt(), version.getVersion() == latest)).toList();
    }

    /** The settings and business details that were live in a version, ready to publish again. */
    @Transactional(readOnly = true)
    public AgentSetupRequest snapshot(UUID tenantId, UUID agentId, int version) {
        requireAgent(tenantId, agentId);
        return versions.findByAgentIdAndVersion(agentId, version)
                .map(found -> read(found.getSnapshotJson()))
                .orElseThrow(() -> new EntityNotFoundException("Version not found"));
    }

    /**
     * Records what is live right after a publish: the agent settings that were applied, and every
     * business detail with its value (blank ones included, so restoring also clears them). Runs in
     * the publishing transaction and clears the draft it replaces.
     */
    @Transactional
    public int recordPublished(UUID tenantId, Agent agent, AgentSetupRequest applied, String actor, String note) {
        var details = variables.list(tenantId, agent.getId());
        var definitions = details.stream().map(detail -> new VariableDefinition(
                detail.key(), detail.label(), detail.description(), detail.value(), detail.required())).toList();
        var values = new LinkedHashMap<String, String>();
        details.forEach(detail -> values.put(detail.key(), detail.value() == null ? "" : detail.value()));
        var snapshot = new AgentSetupRequest(applied.agent(), null, definitions, values);
        var next = versions.latestVersion(agent.getId()) + 1;
        versions.save(new AgentVersion(agent.getId(), tenantId, next, write(snapshot), note, actor, now()));
        drafts.findByAgentIdAndTenantId(agent.getId(), tenantId).ifPresent(drafts::delete);
        return next;
    }

    private void requireAgent(UUID tenantId, UUID agentId) {
        agents.findByIdAndTenantId(agentId, tenantId).orElseThrow(() -> new EntityNotFoundException("Agent not found"));
    }

    private String write(AgentSetupRequest request) {
        try {
            return json.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store the agent configuration", exception);
        }
    }

    private AgentSetupRequest read(String stored) {
        try {
            return json.readValue(stored, AgentSetupRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("A stored agent configuration could not be read", exception);
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
