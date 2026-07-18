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
                        "customer_details", Map.of("type", "object")
                ),
                "required", List.of("caller_name")
        );
        var tool = new AgentTool(agent, "book_slot", "Create booking", schema, "sauti_calendar", true, 20);
        var repository = mock(AgentToolRepository.class);
        when(repository.findByAgent_IdAndIsActiveTrueOrderByDisplayOrderAsc(agent.getId())).thenReturn(List.of(tool));

        var definition = new AgentToolLoader(repository).loadForAgent(agent.getId()).get(0);

        var required = (List<String>) definition.inputSchema().get("required");
        var properties = (Map<String, Object>) definition.inputSchema().get("properties");
        var details = (Map<String, Object>) properties.get("customer_details");
        var detailProperties = (Map<String, Object>) details.get("properties");
        assertThat(required).contains("caller_name", "caller_email", "customer_details");
        assertThat((List<String>) details.get("required"))
                .containsExactly("patient_date_of_birth", "insurance_member_number");
        assertThat(detailProperties).containsKeys("patient_date_of_birth", "insurance_member_number");
        assertThat(details).containsEntry("additionalProperties", true);
    }
}
