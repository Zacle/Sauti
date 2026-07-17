package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.tenant.Tenant;
import java.util.List;
import java.util.Optional;
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

    @Test
    void runtimeStructuredSettingsOverrideStaleOnboardingVariables() {
        var repository = mock(AgentVariableRepository.class);
        var agent = new Agent(new Tenant("Demo Clinic", "owner@example.com", "KE"), "Amina", "Hello", "Prompt");
        agent.configureOnboarding(
                "Healthcare", "Appointment booking", null, List.of("Consultation"),
                "Google Calendar", "Fixed calendar", "Provider default"
        );
        var calendar = new AgentVariable(agent, "calendar_provider", "Calendar destination", null, false);
        calendar.updateValue("Set up later");
        var routing = new AgentVariable(agent, "routing_policy", "Meeting routing", null, false);
        routing.updateValue("Set up later");
        when(repository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()))
                .thenReturn(List.of(calendar, routing));
        var service = new AgentVariableService(repository, mock(AgentRepository.class));

        var resolved = service.resolvePrompt(
                agent,
                "The selected calendar destination is {{calendar_provider}} using {{routing_policy}} routing."
        );

        assertThat(resolved).isEqualTo(
                "The selected calendar destination is Google Calendar using Fixed calendar routing."
        );
    }

    @Test
    void internalStructuredVariableUpdatesKeepTheAgentSettingInSync() {
        var repository = mock(AgentVariableRepository.class);
        var agent = new Agent(new Tenant("Demo Clinic", "owner@example.com", "KE"), "Amina", "Hello", "Prompt");
        var calendar = new AgentVariable(agent, "calendar_provider", "Calendar destination", null, false);
        calendar.updateValue("Set up later");
        when(repository.findByAgentIdAndKey(agent.getId(), "calendar_provider"))
                .thenReturn(Optional.of(calendar));
        var service = new AgentVariableService(repository, mock(AgentRepository.class));

        service.updateIfPresent(agent.getId(), "calendar_provider", "Google Calendar");

        assertThat(calendar.getValue()).isEqualTo("Google Calendar");
        assertThat(agent.getCalendarProvider()).isEqualTo("Google Calendar");
    }
}
