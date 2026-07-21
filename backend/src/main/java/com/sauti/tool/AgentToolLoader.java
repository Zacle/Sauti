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
        var legacyConfirmationFields = java.util.Set.of(
                "caller_name_spelling_confirmed",
                "caller_phone_digits_confirmed",
                "caller_email_spelling_confirmed",
                "final_booking_review_confirmed"
        );
        legacyConfirmationFields.forEach(properties::remove);
        required.removeIf(legacyConfirmationFields::contains);
        properties.put("review_token", Map.of(
                "type", "string",
                "description", "Private token returned by the immediately preceding booking review. Never say it aloud. Keep passing the preceding token when correcting one value so only that correction is reconfirmed."
        ));
        properties.remove("caller_name");
        properties.put("appointment_name", Map.of(
                "type", "string",
                "description", "Full name to place on the appointment. This is the person receiving the service and may differ from the person speaking when they book for a wife, husband, child, patient, guest, or other person."
        ));
        if (required.remove("caller_name") && !required.contains("appointment_name")) {
            required.add("appointment_name");
        }
        var configured = tool.getAgent().getBookingRequiredFields();
        var topLevel = java.util.Set.of(
                "caller_name", "caller_phone", "caller_email", "service_type", "appointment_at"
        );
        configured.stream().filter(topLevel::contains).forEach(field -> {
            var exposedField = "caller_name".equals(field) ? "appointment_name" : field;
            if (!required.contains(exposedField)) required.add(exposedField);
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
        return new LlmToolDefinition(
                definition.name(),
                "Two-step booking. appointment_name is the person receiving the service, not necessarily the person speaking. First call without review_token after configured details and availability are complete. Speak the returned review and wait. On a correction, change only that field and pass the preceding review_token so the server returns a focused correction review. After approval, call again with unchanged details and the latest review_token. The caller states details naturally and is never required to spell them. Never expose the token.",
                Map.copyOf(schema)
        );
    }

    private String humanize(String field) {
        return java.util.Arrays.stream(field.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
