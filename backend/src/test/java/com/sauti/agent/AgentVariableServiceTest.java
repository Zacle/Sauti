package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.tenant.Tenant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentVariableServiceTest {
    @Test
    void resolvesAutomaticAndBusinessVariablesWithoutLeakingEmptyPlaceholders() {
        var repository = mock(AgentVariableRepository.class);
        var agent = new Agent(new Tenant("Demo Clinic", "owner@example.com", "KE"), "Amina", "Hello", "Prompt");
        agent.update(
                "Amina", "Hello", "Prompt", "en", List.of("en"),
                null, List.of(), true, "Africa/Nairobi", ""
        );
        var clinicName = new AgentVariable(agent, "clinic_name", "Clinic name", null, true);
        clinicName.updateValue("Nairobi Family Health");
        var insurance = new AgentVariable(agent, "accepted_insurance", "Accepted insurance", null, false);
        when(repository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()))
                .thenReturn(List.of(clinicName, insurance));
        var service = new AgentVariableService(repository, mock(AgentRepository.class));

        var resolved = service.resolvePrompt(
                agent,
                "{{agent_name}} answers for {{clinic_name}} in {{timezone}}. Insurance: {{accepted_insurance}}."
        );

        assertThat(resolved)
                .isEqualTo("Amina answers for Nairobi Family Health in Africa/Nairobi. Insurance: .")
                .doesNotContain("{{");
    }
}
