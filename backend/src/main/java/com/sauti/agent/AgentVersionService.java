package com.sauti.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentDtos.AgentRequest;
import com.sauti.agent.AgentSetupDtos.AgentSetupRequest;
import com.sauti.agent.AgentSetupDtos.DraftView;
import com.sauti.agent.AgentSetupDtos.VariableDefinition;
import com.sauti.agent.AgentSetupDtos.VersionView;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drafts and versions of an agent. Live calls only ever see what was published or edited directly;
 * a draft waits on the server until it is published.
 *
 * <p>A version holds the agent settings, every business detail with its value, and the parts other
 * modules own ({@link AgentConfigurationPart}: tools, integrations, documents). Publishing records a
 * version, and so does every direct edit; edits one person makes within a couple of minutes of each
 * other are folded into one version so a busy session does not flood the history.
 */
@Service
public class AgentVersionService {
    /** Direct edits closer together than this, by the same person, share a version. */
    static final Duration EDIT_WINDOW = Duration.ofMinutes(2);

    private final AgentRepository agents;
    private final AgentDraftRepository drafts;
    private final AgentVersionRepository versions;
    private final AgentVariableService variables;
    private final ObjectProvider<AgentConfigurationPart> parts;
    private final ObjectMapper json;

    public AgentVersionService(AgentRepository agents, AgentDraftRepository drafts, AgentVersionRepository versions,
                               AgentVariableService variables, ObjectProvider<AgentConfigurationPart> parts,
                               ObjectMapper json) {
        this.agents = agents;
        this.drafts = drafts;
        this.versions = versions;
        this.variables = variables;
        this.parts = parts;
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
        return list.stream().map(version -> new VersionView(version.getVersion(), version.getKind(),
                version.getNote(), version.getPublishedBy(), version.getPublishedAt(),
                version.getVersion() == latest, List.copyOf(readParts(version.getPartsJson()).keySet()))).toList();
    }

    /** The settings and business details that were live in a version, ready to publish again. */
    @Transactional(readOnly = true)
    public AgentSetupRequest snapshot(UUID tenantId, UUID agentId, int version) {
        return find(tenantId, agentId, version).map(found -> read(found.getSnapshotJson()))
                .orElseThrow(() -> new EntityNotFoundException("Version not found"));
    }

    /**
     * Puts back the other modules' parts of a version, such as its tools. A part the version has no
     * record of (versions saved before parts were kept) is left as it is now.
     *
     * @return what could not be put back, in words for the owner
     */
    @Transactional
    public List<String> restoreParts(UUID tenantId, UUID agentId, int version) {
        var stored = find(tenantId, agentId, version).map(found -> readParts(found.getPartsJson()))
                .orElseThrow(() -> new EntityNotFoundException("Version not found"));
        var notes = new ArrayList<String>();
        parts.orderedStream().forEach(part -> {
            var captured = stored.get(part.key());
            if (captured == null) return;
            notes.addAll(part.restore(tenantId, agentId, captured));
        });
        return notes;
    }

    /**
     * Records what is live right after a publish: the agent settings that were applied, every
     * business detail with its value (blank ones included, so restoring also clears them), and the
     * other modules' parts. Runs in the publishing transaction and clears the draft it replaces.
     */
    @Transactional
    public int recordPublished(UUID tenantId, Agent agent, AgentSetupRequest applied, String actor, String note) {
        var next = versions.latestVersion(agent.getId()) + 1;
        versions.save(new AgentVersion(agent.getId(), tenantId, next, AgentVersion.PUBLISH,
                write(withDetails(tenantId, agent, applied.agent())), writeParts(tenantId, agent.getId()),
                note, actor, now()));
        drafts.findByAgentIdAndTenantId(agent.getId(), tenantId).ifPresent(drafts::delete);
        return next;
    }

    /**
     * Records the live state after a direct edit (one business detail, a tool, a document, an
     * integration, the timezone). Folds it into the latest version when that is an edit by the same
     * person a moment ago. A pending draft is kept: it was based on an older state, and the owner
     * still decides whether to publish it.
     */
    @Transactional
    public int recordEdit(UUID tenantId, UUID agentId, String change, String actor) {
        var agent = agents.findByIdAndTenantId(agentId, tenantId).orElse(null);
        if (agent == null) return 0;
        var snapshot = write(withDetails(tenantId, agent, currentSettings(agent)));
        var captured = writeParts(tenantId, agentId);
        var at = now();
        var latest = versions.findFirstByAgentIdOrderByVersionDesc(agentId);
        if (latest.isPresent() && latest.get().absorbs(actor, at, EDIT_WINDOW)) {
            latest.get().absorb(snapshot, captured, change, at);
            return latest.get().getVersion();
        }
        var next = latest.map(AgentVersion::getVersion).orElse(0) + 1;
        versions.save(new AgentVersion(agentId, tenantId, next, AgentVersion.EDIT, snapshot, captured, change,
                actor, at));
        return next;
    }

    private AgentSetupRequest withDetails(UUID tenantId, Agent agent, AgentRequest settings) {
        var details = variables.list(tenantId, agent.getId());
        var definitions = details.stream().map(detail -> new VariableDefinition(
                detail.key(), detail.label(), detail.description(), detail.value(), detail.required())).toList();
        var values = new LinkedHashMap<String, String>();
        details.forEach(detail -> values.put(detail.key(), detail.value() == null ? "" : detail.value()));
        return new AgentSetupRequest(settings, null, definitions, values);
    }

    /** The agent's settings as a request that, published, would leave it exactly as it is. */
    static AgentRequest currentSettings(Agent agent) {
        var keypad = agent.getKeypad();
        var behaviour = agent.getCallBehaviour();
        var speech = agent.getSpeech();
        var booking = agent.getBooking();
        var customFields = booking.getRequiredFields().stream()
                .filter(field -> !booking.isEnabled() || !BookingWorkflow.CORE_FIELDS.contains(field)).toList();
        return new AgentRequest(
                agent.getName(), agent.getDescription(), agent.getGreetingMessage(), agent.getSystemPrompt(),
                agent.getDefaultLanguage(), agent.getSupportedLanguages(), agent.getTtsVoiceId(),
                agent.getHumanTransferNumber(), agent.getEscalationPhrases(), booking.isEnabled(),
                booking.getDefaultDurationMinutes(), agent.getTimezone(), agent.getKnowledgeBase(),
                agent.getOperatingHours(), agent.getAfterHoursBehavior(), agent.getAfterHoursMessage(),
                behaviour.getMaxCallDurationSeconds(), agent.isSaveTranscript(), agent.isRecordCalls(),
                agent.getLlmTier(), behaviour.getBargeInSensitivity(), behaviour.getBargeInGraceMs(),
                behaviour.getEndCallOnSilenceSeconds(), behaviour.getReminderAfterSilenceSeconds(),
                behaviour.getMaxReminders(), behaviour.isDetectVoicemail(), behaviour.isHandleCallScreening(),
                keypad.isEnabled(), speech.getEndpointingMs(), speech.getVocabularyDomain(),
                speech.getBoostedKeywords(), agent.getSafetyGuardrails(), agent.getPostCallExtractionFields(),
                customFields, booking.getNotificationChannels(), booking.getNotificationRecipient(),
                keypad.getTerminationKey(), keypad.getInputTimeoutSeconds(), keypad.getMaxDigits(),
                keypad.getDigitMappings(), agent.isWebVoiceEnabled(), agent.getWebVoiceAllowedOrigins(),
                agent.isWebVoiceRequireConsent(), agent.isWhatsappEnabled(), agent.getWhatsappPhoneNumberId()
        );
    }

    private Optional<AgentVersion> find(UUID tenantId, UUID agentId, int version) {
        requireAgent(tenantId, agentId);
        return versions.findByAgentIdAndVersion(agentId, version);
    }

    private void requireAgent(UUID tenantId, UUID agentId) {
        agents.findByIdAndTenantId(agentId, tenantId).orElseThrow(() -> new EntityNotFoundException("Agent not found"));
    }

    private String writeParts(UUID tenantId, UUID agentId) {
        var captured = new LinkedHashMap<String, JsonNode>();
        parts.orderedStream().forEach(part -> captured.put(part.key(), part.capture(tenantId, agentId)));
        return captured.isEmpty() ? null : write(captured);
    }

    private Map<String, JsonNode> readParts(String stored) {
        if (stored == null || stored.isBlank()) return Map.of();
        try {
            return json.readValue(stored, new TypeReference<LinkedHashMap<String, JsonNode>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("A stored agent configuration could not be read", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
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
