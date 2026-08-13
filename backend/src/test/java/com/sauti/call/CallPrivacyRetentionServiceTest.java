package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CallPrivacyRetentionServiceTest {
    @Test
    void redactsConversationContentButPreservesOperationalMetrics() {
        var tenants = mock(TenantRepository.class);
        var calls = mock(CallRepository.class);
        var turns = mock(CallTurnRepository.class);
        var recordings = mock(CallRecordingService.class);
        var service = new CallPrivacyRetentionService(tenants, calls, turns, recordings);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var agent = new Agent(tenant, "Amina", "Hello", "Prompt");
        var call = new Call(tenant, agent, "call-retention", "+44123456789", "inbound");
        call.appendTurn("en", "My name is Sam", "Hello Sam");
        call.complete("completed");
        var turn = new CallTurn(call, 1, "My name is Sam", "Hello Sam", "en", 80, 150, 90);
        var cutoff = OffsetDateTime.now().plusDays(1);
        when(calls.findTop250ByTenantIdAndEndedAtBeforeAndPrivacyRedactedAtIsNullOrderByEndedAtAsc(
                tenant.getId(), cutoff)).thenReturn(List.of(call));
        when(turns.findByCall_IdAndTenant_Id(call.getId(), tenant.getId())).thenReturn(List.of(turn));

        assertThat(service.redactExpired(tenant.getId(), cutoff)).isEqualTo(1);

        assertThat(call.getCallerNumber()).isNull();
        assertThat(call.getTranscript()).isEmpty();
        assertThat(call.getOutcome()).isEqualTo("completed");
        assertThat(call.getDurationSeconds()).isNotNull();
        assertThat(call.getPrivacyRedactedAt()).isNotNull();
        assertThat(turn.getCallerTranscript()).isEmpty();
        assertThat(turn.getAgentResponse()).isEmpty();
        assertThat(turn.getLlmLatencyMs()).isEqualTo(150);
        verify(turns).saveAll(List.of(turn));
        verify(calls).saveAll(List.of(call));
    }
}
