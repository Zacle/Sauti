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
        var provisioner = mock(ManagedVoiceAgentProvisioner.class);
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
        when(blueprintFactory.create(call, "Hello")).thenReturn(blueprint);
        when(provisioner.provider()).thenReturn("retell");
        when(provisioner.isConfigured()).thenReturn(true);
        when(provisioner.synchronize(blueprint, null)).thenReturn(reference);
        when(repository.findByTenantIdAndAgentIdAndProvider(tenantId, agentId, "retell"))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        var service = new ManagedVoiceAgentProvisioningService(
                repository,
                blueprintFactory,
                new ObjectMapper(),
                List.of(provisioner)
        );

        assertThat(service.resolve("retell", call, "Hello")).isEqualTo(reference);
        assertThat(service.resolve("retell", call, "Hello")).isEqualTo(reference);

        verify(provisioner, times(1)).synchronize(blueprint, null);
        verify(repository, times(1)).save(any());
    }

    private ManagedVoiceAgentBlueprint blueprint(String greeting) {
        return new ManagedVoiceAgentBlueprint(
                "Sauti Test",
                greeting,
                "Be professional.",
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
