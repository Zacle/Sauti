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
        var tools = new java.util.ArrayList<>(agentToolRepository.findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(agentId)
                .stream()
                // The semantic boundary is platform-owned. Ignore any legacy
                // database row with the reserved name so Realtime never receives
                // duplicate function definitions.
                .filter(tool -> !ConversationStateTool.NAME.equals(tool.getToolName()))
                .map(this::definition)
                .toList());
        tools.add(ConversationStateTool.definition());
        return List.copyOf(tools);
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
        properties.put("review_action", Map.of(
                "type", "string",
                "enum", List.of("prepare_review", "correct_review", "approve_review"),
                "description", "Semantic purpose of this call. Use prepare_review when producing the first final review, correct_review when the caller changed a reviewed value, and approve_review only when the caller clearly approved the latest review in their own words or language."
        ));
        if (!required.contains("review_action")) required.add("review_action");
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
                "Two-step booking. appointment_name is the person receiving the service, not necessarily the person speaking. Set review_action from the caller's meaning in their language: prepare_review for the first review, correct_review for a correction, and approve_review only for clear approval of the latest review. The server retains the private review token. The caller states details naturally and is never required to spell anything. Never expose the token.",
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
