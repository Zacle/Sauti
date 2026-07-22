package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.tenant.Tenant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolLoaderTest {
    @Test
    void exposesTheInternalSemanticStateToolForEveryAgentWithoutDatabaseConfiguration() {
        var repository = mock(AgentToolRepository.class);
        var agentId = java.util.UUID.randomUUID();
        when(repository.findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(agentId)).thenReturn(List.of());

        assertThat(loader(repository).loadForAgent(agentId))
                .extracting(com.sauti.llm.LlmToolDefinition::name)
                .containsExactly(ConversationStateTool.NAME);
    }

    @Test
    void legacyDatabaseCollisionCannotDuplicateTheReservedPlatformTool() {
        var repository = mock(AgentToolRepository.class);
        var agentId = java.util.UUID.randomUUID();
        var collision = mock(AgentTool.class);
        when(collision.getToolName()).thenReturn(ConversationStateTool.NAME);
        when(repository.findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(agentId))
                .thenReturn(List.of(collision));

        assertThat(loader(repository).loadForAgent(agentId))
                .extracting(com.sauti.llm.LlmToolDefinition::name)
                .containsExactly(ConversationStateTool.NAME);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesEveryConfiguredBookingFieldToTheModelSchema() {
        var agent = new Agent(new Tenant("Clinic", "owner@example.com", "KE"), "Amina", "Hello", "Prompt");
        agent.update("Amina", "Hello", "Prompt", "en", List.of("en"), null, List.of(), true, "UTC", "");
        agent.configureBookingWorkflow(
                List.of("caller_email", "patient_date_of_birth", "insurance_member_number"),
                List.of("dashboard"), null
        );
        var schema = Map.<String, Object>of(
                "type", "object",
                "properties", Map.of(
                        "caller_name", Map.of("type", "string"),
                        "caller_email", Map.of("type", "string"),
                        "customer_details", Map.of("type", "object"),
                        "caller_name_spelling_confirmed", Map.of("type", "boolean"),
                        "final_booking_review_confirmed", Map.of("type", "boolean")
                ),
                "required", List.of("caller_name", "caller_name_spelling_confirmed", "final_booking_review_confirmed")
        );
        var tool = new AgentTool(agent, "book_slot", "Create booking", schema, "sauti_calendar", true, 20);
        tool.configureActionPolicy(ToolActionEffect.DATA_WRITE, ToolConfirmationPolicy.VERIFIED_REVIEW);
        var repository = mock(AgentToolRepository.class);
        when(repository.findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(agent.getId())).thenReturn(List.of(tool));

        var definition = loader(repository).loadForAgent(agent.getId()).get(0);

        var required = (List<String>) definition.inputSchema().get("required");
        var properties = (Map<String, Object>) definition.inputSchema().get("properties");
        var details = (Map<String, Object>) properties.get("customer_details");
        var detailProperties = (Map<String, Object>) details.get("properties");
        assertThat(required).contains(
                        "appointment_name", "caller_email", "customer_details", "review_action", "question_handling"
                )
                .doesNotContain("caller_name", "caller_name_spelling_confirmed", "final_booking_review_confirmed");
        assertThat(properties).doesNotContainKeys(
                "caller_name_spelling_confirmed",
                "caller_phone_digits_confirmed",
                "caller_email_spelling_confirmed",
                "final_booking_review_confirmed"
        );
        assertThat(properties).containsKeys(
                        "appointment_name", "review_token", "review_action", "question_handling"
                )
                .doesNotContainKey("caller_name");
        assertThat(required).doesNotContain("review_token");
        assertThat(definition.description())
                .contains("person receiving the service")
                .contains("review_action")
                .contains("approve_review")
                .contains("answer_before_action", "verified review");
        assertThat((List<String>) ((Map<String, Object>) properties.get("review_action")).get("enum"))
                .containsExactly("prepare_review", "correct_review", "approve_review");
        assertThat(((Map<String, Object>) properties.get("appointment_name")).get("description").toString())
                .contains("may differ from the person speaking");
        assertThat((List<String>) details.get("required"))
                .containsExactly("patient_date_of_birth", "insurance_member_number");
        assertThat(detailProperties).containsKeys("patient_date_of_birth", "insurance_member_number");
        assertThat(details).containsEntry("additionalProperties", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void derivesTheSafetyContractFromEffectsRatherThanBusinessToolNames() {
        var agent = new Agent(new Tenant("Clinic", "owner@example.com", "KE"), "Amina", "Hello", "Prompt");
        agent.update("Amina", "Hello", "Prompt", "en", List.of("en"), null, List.of(), true, "UTC", "");
        var repository = mock(AgentToolRepository.class);
        var sheetWrite = new AgentTool(agent, "replace_customer_record", "Replace CRM data", Map.of(
                "type", "object", "properties", Map.of(), "required", List.of()
        ), "webhook", true, 21);
        sheetWrite.configureActionPolicy(ToolActionEffect.DATA_WRITE, ToolConfirmationPolicy.EXPLICIT);
        var payment = new AgentTool(agent, "collect_deposit", "Collect a deposit", Map.of(
                "type", "object", "properties", Map.of(), "required", List.of()
        ), "sauti_integration", true, 22);
        payment.configureActionPolicy(ToolActionEffect.FINANCIAL, ToolConfirmationPolicy.EXPLICIT);
        var lookup = new AgentTool(agent, "find_customer_record", "Read CRM data", Map.of(
                        "type", "object", "properties", Map.of(), "required", List.of()
                ), "webhook", true, 23);
        lookup.configureActionPolicy(ToolActionEffect.READ_ONLY, ToolConfirmationPolicy.NONE);
        var tools = List.of(sheetWrite, payment, lookup);
        when(repository.findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(agent.getId())).thenReturn(tools);

        var definitions = loader(repository).loadForAgent(agent.getId()).stream()
                .filter(definition -> !ConversationStateTool.NAME.equals(definition.name()))
                .toList();

        assertThat(definitions).filteredOn(definition -> !"find_customer_record".equals(definition.name())).allSatisfy(definition -> {
            var required = (List<String>) definition.inputSchema().get("required");
            var properties = (Map<String, Object>) definition.inputSchema().get("properties");
            assertThat(required).contains("question_handling", "confirmation_state");
            assertThat((List<String>) ((Map<String, Object>) properties.get("question_handling")).get("enum"))
                    .containsExactly("ready_for_action", "answer_before_action");
            assertThat(definition.description()).contains("answer_before_action", "Explicit confirmation");
        });
        var readOnly = definitions.stream().filter(definition -> "find_customer_record".equals(definition.name())).findFirst().orElseThrow();
        assertThat((Map<String, Object>) readOnly.inputSchema().get("properties"))
                .doesNotContainKeys("question_handling", "confirmation_state");
    }

    private AgentToolLoader loader(AgentToolRepository repository) {
        return new AgentToolLoader(repository, new ToolActionPolicy());
    }
}
