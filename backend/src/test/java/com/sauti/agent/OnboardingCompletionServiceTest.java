package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.AgentDtos.AgentRequest;
import com.sauti.agent.OnboardingDtos.CompleteOnboardingRequest;
import com.sauti.tenant.Tenant;
import com.sauti.tool.DefaultToolSeeder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingCompletionServiceTest {
    @Mock AgentService agentService;
    @Mock AgentRepository agentRepository;
    @Mock AgentVariableService agentVariableService;
    @Mock DefaultToolSeeder defaultToolSeeder;

    @Test
    void persistsSelectionsAndBuildsAnExecutableDraft() {
        var tenantId = UUID.randomUUID();
        var tenant = new Tenant("Kinshasa Health", "owner@example.com", "CD");
        when(agentService.create(eq(tenantId), any(AgentRequest.class))).thenAnswer(invocation -> {
            AgentRequest draft = invocation.getArgument(1);
            var agent = new Agent(tenant, draft.name(), draft.greetingMessage(), draft.systemPrompt());
            agent.update(
                    draft.name(), draft.description(), draft.greetingMessage(), draft.systemPrompt(),
                    draft.defaultLanguage(), draft.supportedLanguages(), draft.ttsVoiceId(),
                    draft.humanTransferNumber(), draft.escalationPhrases(), draft.bookingEnabled(),
                    draft.timezone(), draft.knowledgeBase(), draft.operatingHours(),
                    draft.maxCallDurationSeconds(), draft.saveTranscript(), draft.recordCalls()
            );
            return agent;
        });
        var service = new OnboardingCompletionService(
                agentService, agentRepository, agentVariableService, defaultToolSeeder
        );
        var request = new CompleteOnboardingRequest(
                "Healthcare",
                "Appointment booking",
                "https://kinshasa-health.example",
                List.of("Consultation", "Follow-up"),
                "Africa/Kinshasa",
                "Google Calendar",
                "Fixed calendar",
                "Amina",
                "fr",
                List.of("fr", "en"),
                "voice-123",
                "Aminata"
        );

        var agent = service.complete(tenantId, request);

        assertThat(agent.getBusinessType()).isEqualTo("Healthcare");
        assertThat(agent.getPrimaryUseCase()).isEqualTo("Appointment booking");
        assertThat(agent.getBusinessWebsite()).isEqualTo("https://kinshasa-health.example");
        assertThat(agent.getBookableServices()).containsExactly("Consultation", "Follow-up");
        assertThat(agent.getCalendarProvider()).isEqualTo("Google Calendar");
        assertThat(agent.getRoutingPolicy()).isEqualTo("Fixed calendar");
        assertThat(agent.getTtsVoiceId()).isEqualTo("voice-123");
        assertThat(agent.getSystemPrompt()).contains("{{bookable_services}}", "{{calendar_provider}}");

        var draftCaptor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentService).create(eq(tenantId), draftCaptor.capture());
        assertThat(draftCaptor.getValue().bookingEnabled()).isTrue();
        assertThat(draftCaptor.getValue().sttVocabularyDomain()).isEqualTo("medical");
        assertThat(draftCaptor.getValue().defaultLanguage()).isEqualTo("fr");
        verify(defaultToolSeeder).configureOnboardingDraft(agent);
        verify(agentVariableService).create(
                eq(tenantId), eq(agent.getId()), eq("bookable_services"), any(), any(),
                eq("Consultation, Follow-up"), eq(true)
        );
    }

    @Test
    void acceptsCurrentOnboardingHealthcareLabel() {
        var tenantId = UUID.randomUUID();
        var tenant = new Tenant("Clinic", "owner@example.com", "KE");
        when(agentService.create(eq(tenantId), any(AgentRequest.class))).thenAnswer(invocation -> {
            AgentRequest draft = invocation.getArgument(1);
            var agent = new Agent(tenant, draft.name(), draft.greetingMessage(), draft.systemPrompt());
            agent.update(
                    draft.name(), draft.description(), draft.greetingMessage(), draft.systemPrompt(),
                    draft.defaultLanguage(), draft.supportedLanguages(), draft.ttsVoiceId(),
                    draft.humanTransferNumber(), draft.escalationPhrases(), draft.bookingEnabled(),
                    draft.timezone(), draft.knowledgeBase(), draft.operatingHours(),
                    draft.maxCallDurationSeconds(), draft.saveTranscript(), draft.recordCalls()
            );
            return agent;
        });
        var service = new OnboardingCompletionService(
                agentService, agentRepository, agentVariableService, defaultToolSeeder
        );
        var request = new CompleteOnboardingRequest(
                "Clinics & healthcare",
                "Appointment booking",
                "",
                List.of("Consultation"),
                "Africa/Nairobi",
                "Set up later",
                "Set up later",
                "Amina",
                "en",
                List.of("en"),
                null,
                "Provider default"
        );

        var agent = service.complete(tenantId, request);

        assertThat(agent.getBusinessType()).isEqualTo("Clinics & healthcare");
        var draftCaptor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentService).create(eq(tenantId), draftCaptor.capture());
        assertThat(draftCaptor.getValue().sttVocabularyDomain()).isEqualTo("medical");
    }
}
