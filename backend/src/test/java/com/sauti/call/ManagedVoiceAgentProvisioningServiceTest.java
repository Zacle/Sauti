package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.tenant.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ManagedVoiceAgentProvisioningServiceTest {

    @Test
    void createsAndThenReusesTheTenantScopedProviderBindingForAnUnchangedBlueprint() {
        var repository = mock(ManagedVoiceAgentBindingRepository.class);
        var blueprintFactory = mock(ManagedVoiceAgentBlueprintFactory.class);
        var provisioner = mock(TelnyxManagedVoiceAgentProvisioner.class);
        var call = mock(Call.class);
        var tenant = mock(Tenant.class);
        var agent = mock(Agent.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var stored = new AtomicReference<ManagedVoiceAgentBinding>();
        var blueprint = blueprint("Hello");
        var reference = new ManagedVoiceAgentReference("external-agent", "main", "{}");

        when(call.getTenant()).thenReturn(tenant);
        when(call.getAgent()).thenReturn(agent);
        when(tenant.getId()).thenReturn(tenantId);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getDefaultLanguage()).thenReturn("en");
        when(agent.getSupportedLanguages()).thenReturn(List.of("en", "fr"));
        when(call.getLanguageDetected()).thenReturn("en");
        when(blueprintFactory.create(call, "Hello")).thenReturn(blueprint);
        when(provisioner.isConfigured()).thenReturn(true);
        when(provisioner.configurationVersion()).thenReturn("1");
        when(provisioner.synchronize(blueprint, null)).thenReturn(reference);
        when(repository.findByTenantIdAndAgentIdAndProviderAndLanguage(
                tenantId, agentId, "telnyx", "en"
        ))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        var service = new ManagedVoiceAgentProvisioningService(
                repository,
                blueprintFactory,
                new ObjectMapper(),
                provisioner
        );

        assertThat(service.resolve(call, "Hello")).isEqualTo(reference);
        assertThat(service.resolve(call, "Hello")).isEqualTo(reference);

        verify(provisioner, times(1)).synchronize(blueprint, null);
        verify(repository, times(1)).save(any());
    }

    @Test
    void resynchronizesAnExistingBindingWhenProviderConfigurationChanges() {
        var repository = mock(ManagedVoiceAgentBindingRepository.class);
        var blueprintFactory = mock(ManagedVoiceAgentBlueprintFactory.class);
        var provisioner = mock(TelnyxManagedVoiceAgentProvisioner.class);
        var call = mock(Call.class);
        var tenant = mock(Tenant.class);
        var agent = mock(Agent.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var stored = new AtomicReference<ManagedVoiceAgentBinding>();
        var blueprint = blueprint("Hello");
        var reference = new ManagedVoiceAgentReference("external-agent", "main", "{}");

        when(call.getTenant()).thenReturn(tenant);
        when(call.getAgent()).thenReturn(agent);
        when(tenant.getId()).thenReturn(tenantId);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getDefaultLanguage()).thenReturn("en");
        when(agent.getSupportedLanguages()).thenReturn(List.of("en", "fr"));
        when(call.getLanguageDetected()).thenReturn("en");
        when(blueprintFactory.create(call, "Hello")).thenReturn(blueprint);
        when(provisioner.isConfigured()).thenReturn(true);
        when(provisioner.configurationVersion()).thenReturn("1", "2");
        when(provisioner.synchronize(any(), any())).thenReturn(reference);
        when(repository.findByTenantIdAndAgentIdAndProviderAndLanguage(
                tenantId, agentId, "telnyx", "en"
        ))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        var service = new ManagedVoiceAgentProvisioningService(
                repository,
                blueprintFactory,
                new ObjectMapper(),
                provisioner
        );

        assertThat(service.resolve(call, "Hello")).isEqualTo(reference);
        assertThat(service.resolve(call, "Hello")).isEqualTo(reference);

        verify(provisioner).synchronize(blueprint, null);
        verify(provisioner).synchronize(blueprint, reference);
        verify(repository, times(2)).save(any());
    }

    @Test
    void keepsIndependentBindingsForEachSupportedLanguage() {
        var repository = mock(ManagedVoiceAgentBindingRepository.class);
        var blueprintFactory = mock(ManagedVoiceAgentBlueprintFactory.class);
        var provisioner = mock(TelnyxManagedVoiceAgentProvisioner.class);
        var tenant = mock(Tenant.class);
        var agent = mock(Agent.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var stored = new java.util.HashMap<String, ManagedVoiceAgentBinding>();
        var english = blueprint("Hello");
        var french = new ManagedVoiceAgentBlueprint(
                "Sauti Test [fr]",
                "Bonjour",
                "Soyez professionnel.",
                "Telnyx.NaturalHD.amarante",
                "fr",
                List.of("en", "fr"),
                List.of(),
                300,
                0.7,
                300,
                List.of("Sauti")
        );

        when(tenant.getId()).thenReturn(tenantId);
        when(agent.getTenant()).thenReturn(tenant);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getDefaultLanguage()).thenReturn("en");
        when(agent.getSupportedLanguages()).thenReturn(List.of("en", "fr"));
        when(blueprintFactory.create(agent, "Hello", "en")).thenReturn(english);
        when(blueprintFactory.create(agent, "Bonjour", "fr")).thenReturn(french);
        when(provisioner.isConfigured()).thenReturn(true);
        when(provisioner.configurationVersion()).thenReturn("1");
        when(provisioner.synchronize(english, null))
                .thenReturn(new ManagedVoiceAgentReference("assistant-en", "main", "{}"));
        when(provisioner.synchronize(french, null))
                .thenReturn(new ManagedVoiceAgentReference("assistant-fr", "main", "{}"));
        when(repository.findByTenantIdAndAgentIdAndProviderAndLanguage(
                tenantId, agentId, "telnyx", "en"
        )).thenAnswer(ignored -> Optional.ofNullable(stored.get("en")));
        when(repository.findByTenantIdAndAgentIdAndProviderAndLanguage(
                tenantId, agentId, "telnyx", "fr"
        )).thenAnswer(ignored -> Optional.ofNullable(stored.get("fr")));
        when(repository.save(any())).thenAnswer(invocation -> {
            ManagedVoiceAgentBinding binding = invocation.getArgument(0);
            stored.put(binding.getLanguage(), binding);
            return binding;
        });

        var service = new ManagedVoiceAgentProvisioningService(
                repository,
                blueprintFactory,
                new ObjectMapper(),
                provisioner
        );

        var references = service.synchronizeAll(
                agent,
                language -> "fr".equals(language) ? "Bonjour" : "Hello"
        );

        assertThat(references)
                .extracting(ManagedVoiceAgentReference::externalAgentId)
                .containsExactly("assistant-en", "assistant-fr");
        assertThat(stored).containsOnlyKeys("en", "fr");
        verify(provisioner).synchronize(english, null);
        verify(provisioner).synchronize(french, null);
    }

    private ManagedVoiceAgentBlueprint blueprint(String greeting) {
        return new ManagedVoiceAgentBlueprint(
                "Sauti Test",
                greeting,
                "Be professional.",
                "Telnyx.NaturalHD.astra",
                "en",
                List.of("en"),
                List.of(),
                300,
                0.7,
                300,
                List.of("Sauti")
        );
    }
}
