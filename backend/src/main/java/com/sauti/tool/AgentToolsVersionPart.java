package com.sauti.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sauti.agent.AgentConfigurationPart;
import com.sauti.agent.AgentRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * An agent's tools in its versions. Credentials are never stored in a version: restoring keeps the
 * credential each tool has now. Tools added since the version are turned off, not deleted.
 */
@Component
@Order(20)
class AgentToolsVersionPart implements AgentConfigurationPart {
    private final AgentRepository agents;
    private final AgentToolRepository tools;
    private final CalendarCredentialRepository calendarCredentials;
    private final ObjectMapper json;

    AgentToolsVersionPart(AgentRepository agents, AgentToolRepository tools,
                          CalendarCredentialRepository calendarCredentials, ObjectMapper json) {
        this.agents = agents;
        this.tools = tools;
        this.calendarCredentials = calendarCredentials;
        this.json = json;
    }

    @Override
    public String key() {
        return "tools";
    }

    @Override
    public JsonNode capture(UUID tenantId, UUID agentId) {
        ArrayNode captured = json.createArrayNode();
        for (var tool : tools.findByAgent_IdOrderByDisplayOrderAsc(agentId)) {
            var node = captured.addObject();
            node.put("toolName", tool.getToolName());
            node.put("toolDescription", tool.getToolDescription());
            node.set("parametersSchema", json.valueToTree(tool.getParametersSchema()));
            node.put("fulfillmentType", tool.getFulfillmentType());
            node.put("webhookUrl", tool.getWebhookUrl());
            node.put("webhookMethod", tool.getWebhookMethod());
            node.put("authType", tool.getAuthType());
            node.put("authHeaderName", tool.getAuthHeaderName());
            node.put("calendarType", tool.getCalendarType());
            node.put("calendarCredentialId",
                    tool.getCalendarCredentialId() == null ? null : tool.getCalendarCredentialId().toString());
            node.put("active", tool.isActive());
            node.put("displayOrder", tool.getDisplayOrder());
            node.put("actionEffect", tool.getActionEffect());
            node.put("confirmationPolicy", tool.getConfirmationPolicy());
        }
        return captured;
    }

    @Override
    public List<String> restore(UUID tenantId, UUID agentId, JsonNode captured) {
        var agent = agents.findByIdAndTenantId(agentId, tenantId).orElseThrow();
        var current = tools.findByAgent_IdOrderByDisplayOrderAsc(agentId);
        var notes = new ArrayList<String>();
        var restored = new HashSet<String>();
        for (var node : captured) {
            var name = node.path("toolName").asText();
            if (name.isBlank()) continue;
            restored.add(name);
            var tool = current.stream().filter(candidate -> candidate.getToolName().equals(name)).findFirst()
                    .orElseGet(() -> tools.save(new AgentTool(agent, name, text(node, "toolDescription"), Map.of(),
                            text(node, "fulfillmentType"), false, node.path("displayOrder").asInt())));
            var active = node.path("active").asBoolean();
            var calendarType = text(node, "calendarType");
            var credentialId = uuid(text(node, "calendarCredentialId"));
            if (credentialId != null && calendarCredentials.findByIdAndTenant_Id(credentialId, tenantId).isEmpty()) {
                credentialId = null;
                calendarType = "noop_calendar";
                if (active) notes.add(name + " was left off because its calendar is no longer connected.");
                active = false;
            }
            var authType = text(node, "authType");
            if (active && authType != null && !"none".equals(authType) && tool.getAuthCredential() == null) {
                notes.add(name + " was left off until its credential is entered again.");
                active = false;
            }
            tool.update(name, text(node, "toolDescription"), schema(node.path("parametersSchema")),
                    text(node, "fulfillmentType"), text(node, "webhookUrl"), text(node, "webhookMethod"), authType,
                    null, text(node, "authHeaderName"), calendarType, credentialId, active,
                    node.path("displayOrder").asInt());
            var effect = text(node, "actionEffect");
            var confirmation = text(node, "confirmationPolicy");
            if (effect != null && confirmation != null) {
                tool.configureActionPolicy(ToolActionEffect.from(effect), ToolConfirmationPolicy.from(confirmation));
            }
        }
        current.stream().filter(tool -> !restored.contains(tool.getToolName())).forEach(AgentTool::deactivate);
        return notes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schema(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? Map.of() : json.convertValue(node, Map.class);
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
