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

    @Test
    void businessHoursVariableUpdatesTheRuntimeAvailabilitySchedule() {
        var repository = mock(AgentVariableRepository.class);
        var agent = new Agent(new Tenant("Hairy", "owner@example.com", "KE"), "Ailsa", "Hello", "Prompt");
        var hours = new AgentVariable(agent, "business_hours", "Business hours", null, true);
        when(repository.findByAgentIdAndKey(agent.getId(), "business_hours")).thenReturn(Optional.of(hours));
        var service = new AgentVariableService(repository, mock(AgentRepository.class));
        var schedule = "Mon 09:00-17:00; Tue 09:00-17:00; Wed 09:00-17:00; Thu 09:00-17:00; Fri 09:00-17:00";

        service.updateIfPresent(agent.getId(), "business_hours", schedule);

        assertThat(agent.getOperatingHours()).isEqualTo(schedule);
        assertThat(OperatingHoursSchedule.describe(agent.getOperatingHours()))
                .contains("Monday 09:00-17:00")
                .contains("Sunday closed");
    }

    @Test
    void afterHoursVariableUpdatesTheRuntimeCallBehavior() {
        var repository = mock(AgentVariableRepository.class);
        var agent = new Agent(new Tenant("Hairy", "owner@example.com", "KE"), "Ailsa", "Hello", "Prompt");
        var behavior = new AgentVariable(agent, "after_hours_behavior", "After-hours behavior", null, true);
        when(repository.findByAgentIdAndKey(agent.getId(), "after_hours_behavior")).thenReturn(Optional.of(behavior));
        var service = new AgentVariableService(repository, mock(AgentRepository.class));

        service.updateIfPresent(agent.getId(), "after_hours_behavior", "take_message");

        assertThat(agent.getAfterHoursBehavior()).isEqualTo("take_message");
    }

    @Test
    void legacySystemManagedFieldsUseRuntimeValuesAndDoNotBlockReadiness() {
        var repository = mock(AgentVariableRepository.class);
        var agent = new Agent(new Tenant("Demo Clinic", "owner@example.com", "KE"), "Amina", "Hello", "Prompt");
        agent.update(
                "Amina", "Hello", "Prompt", "en", List.of("en"),
                null, List.of(), true, "Africa/Cairo", ""
        );
        agent.configureOnboarding(
                "Healthcare", "Appointment booking", null, List.of("Consultation"),
                "Google Calendar", "Fixed calendar", "Provider default"
        );
        var staleTimezone = new AgentVariable(agent, "business_timezone", "Business timezone", null, true);
        agent.configureBookingWorkflow(
                List.of("caller_name", "caller_phone", "service_type", "appointment_at", "patient_date_of_birth"),
                List.of("dashboard", "email"),
                "owner@example.com"
        );
        var staleCalendar = new AgentVariable(agent, "calendar_system", "Calendar system", null, true);
        var staleFields = new AgentVariable(agent, "required_booking_fields", "Required booking fields", null, true);
        staleFields.updateValue("name, phone");
        when(repository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()))
                .thenReturn(List.of(staleTimezone, staleCalendar, staleFields));
        var service = new AgentVariableService(repository, mock(AgentRepository.class));

        assertThat(service.missingRequired(agent.getId())).isEmpty();
        assertThat(service.resolvePrompt(agent, "Hours use {{business_timezone}} with {{calendar_system}}."))
                .isEqualTo("Hours use Africa/Cairo with Google Calendar.");
        assertThat(service.resolvePrompt(agent, "Collect {{required_booking_fields}}; notify {{notification_channels}}."))
                .isEqualTo("Collect caller_name, caller_phone, service_type, appointment_at, patient_date_of_birth; notify dashboard, email.");
    }

    @Test
    void usesPromptBusinessInsteadOfWorkspaceNameWhenVariableIsEmpty() {
        var repository = mock(AgentVariableRepository.class);
        var agent = new Agent(
                new Tenant("Tranquil AI", "owner@example.com", "KE"),
                "Alec",
                "Hello",
                "You are Alec, the virtual assistant for X-Fit."
        );
        when(repository.findByAgentIdAndKey(agent.getId(), "business_name")).thenReturn(Optional.empty());
        var service = new AgentVariableService(repository, mock(AgentRepository.class));

        assertThat(service.businessName(agent)).isEqualTo("X-Fit");
    }

    @Test
    void exposesFilledRequiredAndOptionalBusinessFactsToConversationContext() {
        var repository = mock(AgentVariableRepository.class);
        var agent = new Agent(new Tenant("Clinic", "owner@example.com", "KE"), "Amina", "Hello", "Prompt");
        var required = new AgentVariable(agent, "business_address", "Business address", null, true);
        required.updateValue("12 Main Street");
        var optional = new AgentVariable(agent, "parking_instructions", "Parking instructions", null, false);
        optional.updateValue("Use the rear entrance");
        var emptyOptional = new AgentVariable(agent, "booking_link", "Booking link", null, false);
        when(repository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()))
                .thenReturn(List.of(required, optional, emptyOptional));
        var service = new AgentVariableService(repository, mock(AgentRepository.class));

        assertThat(service.conversationContext(agent))
                .contains("- Business address: 12 Main Street")
                .contains("- Parking instructions: Use the rear entrance")
                .doesNotContain("Booking link");
    }
}
