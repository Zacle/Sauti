package com.sauti.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentConfigurationPart;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Which integrations an agent uses, and how, in its versions. Workspace connections (and their
 * credentials) are not part of a version: an integration whose connection has since been removed
 * is left off on restore. Tools that follow an integration are restored by the tools part.
 */
@Component
@Order(10)
class AgentIntegrationsVersionPart implements AgentConfigurationPart {
    private final AgentIntegrationRepository bindings;
    private final IntegrationConnectionRepository connections;
    private final IntegrationCatalog catalog;
    private final ObjectMapper json;

    AgentIntegrationsVersionPart(AgentIntegrationRepository bindings, IntegrationConnectionRepository connections,
                                 IntegrationCatalog catalog, ObjectMapper json) {
        this.bindings = bindings;
        this.connections = connections;
        this.catalog = catalog;
        this.json = json;
    }

    @Override
    public String key() {
        return "integrations";
    }

    @Override
    public JsonNode capture(UUID tenantId, UUID agentId) {
        var captured = json.createArrayNode();
        for (var binding : bindings.findAllByTenantIdAndAgentIdOrderByProvider(tenantId, agentId)) {
            var node = captured.addObject();
            node.put("provider", binding.getProvider());
            node.put("enabled", binding.isEnabled());
            node.put("connectionId", binding.getConnectionId() == null ? null : binding.getConnectionId().toString());
            node.set("configuration", parse(binding.getConfiguration()));
        }
        return captured;
    }

    @Override
    public List<String> restore(UUID tenantId, UUID agentId, JsonNode captured) {
        var notes = new ArrayList<String>();
        var restored = new HashSet<String>();
        for (var node : captured) {
            var provider = node.path("provider").asText();
            if (provider.isBlank()) continue;
            restored.add(provider);
            var connectionText = node.path("connectionId").isNull() ? "" : node.path("connectionId").asText("");
            var connectionId = connectionText.isBlank() ? null : UUID.fromString(connectionText);
            var enabled = node.path("enabled").asBoolean();
            if (connectionId != null && connections.findByIdAndTenantId(connectionId, tenantId).isEmpty()) {
                if (enabled) notes.add(name(provider) + " was left off because its connection was removed.");
                connectionId = null;
                enabled = false;
            }
            var binding = bindings.findByTenantIdAndAgentIdAndProvider(tenantId, agentId, provider)
                    .orElseGet(() -> new AgentIntegration(tenantId, agentId, provider));
            binding.configure(enabled, connectionId, node.path("configuration").toString());
            bindings.save(binding);
        }
        bindings.findAllByTenantIdAndAgentIdOrderByProvider(tenantId, agentId).stream()
                .filter(binding -> !restored.contains(binding.getProvider()) && binding.isEnabled())
                .forEach(binding -> binding.configure(false, binding.getConnectionId(), binding.getConfiguration()));
        return notes;
    }

    private String name(String provider) {
        try {
            return catalog.require(provider).name();
        } catch (RuntimeException unknown) {
            return provider;
        }
    }

    private JsonNode parse(String configuration) {
        if (configuration == null || configuration.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(configuration);
        } catch (JsonProcessingException exception) {
            return json.createObjectNode();
        }
    }
}
