package com.sauti.tool;

import com.sauti.llm.LlmToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                .map(this::definition)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private LlmToolDefinition definition(AgentTool tool) {
        var definition = LlmToolDefinition.from(tool);
        if (!"book_slot".equals(tool.getToolName())) return definition;

        var schema = new LinkedHashMap<String, Object>(definition.inputSchema());
        var properties = new LinkedHashMap<String, Object>(
                (Map<String, Object>) schema.getOrDefault("properties", Map.of())
        );
        var required = new java.util.ArrayList<String>(
                (List<String>) schema.getOrDefault("required", List.of())
        );
        var configured = tool.getAgent().getBookingRequiredFields();
        var topLevel = java.util.Set.of(
                "caller_name", "caller_phone", "caller_email", "service_type", "appointment_at"
        );
        configured.stream().filter(topLevel::contains).forEach(field -> {
            if (!required.contains(field)) required.add(field);
        });

        var detailFields = configured.stream().filter(field -> !topLevel.contains(field)).toList();
        var detailProperties = new LinkedHashMap<String, Object>();
        detailFields.forEach(field -> detailProperties.put(field, Map.of(
                "type", "string",
                "description", "Required booking detail: " + humanize(field)
        )));
        var detailSchema = new LinkedHashMap<String, Object>();
        detailSchema.put("type", "object");
        detailSchema.put("description", "Configured booking details. Include every required property and preserve any additional relevant detail the caller voluntarily provides.");
        detailSchema.put("properties", detailProperties);
        detailSchema.put("required", detailFields);
        detailSchema.put("additionalProperties", true);
        properties.put("customer_details", Map.copyOf(detailSchema));
        if (!detailFields.isEmpty() && !required.contains("customer_details")) required.add("customer_details");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", List.copyOf(required));
        return new LlmToolDefinition(definition.name(), definition.description(), Map.copyOf(schema));
    }

    private String humanize(String field) {
        return java.util.Arrays.stream(field.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
