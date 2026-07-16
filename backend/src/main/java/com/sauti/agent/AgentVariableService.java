package com.sauti.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.sauti.agent.AgentVariableDtos.AgentVariableResponse;
import com.sauti.agent.AgentVariableDtos.AgentVariableValue;
import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentVariableService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}");
    private static final Pattern VARIABLE_KEY = Pattern.compile("[a-z][a-z0-9_]{0,99}");

    private final AgentVariableRepository variableRepository;
    private final AgentRepository agentRepository;

    public AgentVariableService(AgentVariableRepository variableRepository, AgentRepository agentRepository) {
        this.variableRepository = variableRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentVariableResponse> list(UUID tenantId, UUID agentId) {
        requireOwnedAgent(tenantId, agentId);
        return variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agentId)
                .stream()
                .map(AgentVariableResponse::from)
                .toList();
    }

    @Transactional
    public List<AgentVariableResponse> updateAll(
            UUID tenantId,
            UUID agentId,
            List<AgentVariableValue> values
    ) {
        var agent = requireOwnedAgent(tenantId, agentId);
        var variables = variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agentId);
        var byKey = new LinkedHashMap<String, AgentVariable>();
        variables.forEach(variable -> byKey.put(variable.getKey(), variable));
        for (var value : values) {
            var variable = byKey.get(value.key());
            if (variable == null) {
                throw new IllegalArgumentException("Unknown agent variable: " + value.key());
            }
            variable.updateValue(value.value());
            syncStructuredSetting(agent, value.key(), value.value());
        }
        return variables.stream().map(AgentVariableResponse::from).toList();
    }

    @Transactional
    public AgentVariableResponse updateOne(UUID tenantId, UUID agentId, String key, String value) {
        var agent = requireOwnedAgent(tenantId, agentId);
        var variable = variableRepository.findByAgentIdAndKey(agentId, key)
                .orElseThrow(() -> new EntityNotFoundException("Agent variable not found"));
        variable.updateValue(value);
        syncStructuredSetting(agent, key, value);
        return AgentVariableResponse.from(variable);
    }

    @Transactional
    public void updateIfPresent(UUID agentId, String key, String value) {
        variableRepository.findByAgentIdAndKey(agentId, key).ifPresent(variable -> variable.updateValue(value));
    }

    @Transactional
    public AgentVariableResponse create(
            UUID tenantId,
            UUID agentId,
            String key,
            String label,
            String description,
            String value,
            boolean required
    ) {
        var agent = requireOwnedAgent(tenantId, agentId);
        var normalizedKey = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        if (!VARIABLE_KEY.matcher(normalizedKey).matches()) {
            throw new IllegalArgumentException(
                    "Variable keys must start with a letter and contain only lowercase letters, numbers, and underscores"
            );
        }
        if ("agent_name".equals(normalizedKey) || "timezone".equals(normalizedKey)) {
            throw new IllegalArgumentException(normalizedKey + " is an automatic variable and cannot be redefined");
        }
        if (variableRepository.findByAgentIdAndKey(agentId, normalizedKey).isPresent()) {
            throw new IllegalArgumentException("Agent variable already exists: " + normalizedKey);
        }
        var variable = new AgentVariable(
                agent,
                normalizedKey,
                label.trim(),
                description == null || description.isBlank() ? null : description.trim(),
                required
        );
        variable.updateValue(value);
        return AgentVariableResponse.from(variableRepository.save(variable));
    }

    @Transactional
    public void seedDefinitions(Agent agent, JsonNode configuration) {
        var definitions = configuration.path("variables");
        if (!definitions.isArray()) {
            return;
        }
        for (var definition : definitions) {
            var key = definition.path("key").asText("").trim();
            if (key.isBlank() || "agent_name".equals(key) || "timezone".equals(key)) {
                continue;
            }
            if (variableRepository.findByAgentIdAndKey(agent.getId(), key).isPresent()) {
                continue;
            }
            var label = definition.path("label").asText(key).trim();
            var description = definition.path("description").asText("").trim();
            variableRepository.save(new AgentVariable(
                    agent,
                    key,
                    label.isBlank() ? key : label,
                    description.isBlank() ? null : description,
                    definition.path("required").asBoolean(false)
            ));
        }
    }

    @Transactional(readOnly = true)
    public List<String> missingRequired(UUID agentId) {
        return variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agentId)
                .stream()
                .filter(AgentVariable::isRequired)
                .filter(variable -> !variable.isFilled())
                .map(AgentVariable::getDisplayLabel)
                .toList();
    }

    @Transactional(readOnly = true)
    public String resolvePrompt(Agent agent, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("agent_name", agent.getName());
        values.put("timezone", agent.getTimezone());
        variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId())
                .forEach(variable -> values.put(variable.getKey(), variable.getValue()));

        var matcher = PLACEHOLDER.matcher(prompt);
        var result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                    values.getOrDefault(matcher.group(1), "")
            ));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @Transactional(readOnly = true)
    public String businessName(Agent agent) {
        return variableRepository.findByAgentIdAndKey(agent.getId(), "business_name")
                .filter(AgentVariable::isFilled)
                .map(AgentVariable::getValue)
                .orElse(agent.getTenant().getBusinessName());
    }

    private Agent requireOwnedAgent(UUID tenantId, UUID agentId) {
        return agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
    }

    private void syncStructuredSetting(Agent agent, String key, String value) {
        if ("calendar_provider".equals(key)) {
            var allowed = List.of("Google Calendar", "Calendly", "Custom webhook", "Set up later");
            if (!allowed.contains(value)) {
                throw new IllegalArgumentException("Choose a supported calendar destination");
            }
            agent.updateCalendarProvider(value);
        }
        if ("routing_policy".equals(key)) {
            var allowed = List.of("Fixed calendar", "Set up later");
            if (!allowed.contains(value)) {
                throw new IllegalArgumentException("Choose a supported meeting routing option");
            }
            agent.updateRoutingPolicy(value);
        }
    }
}
