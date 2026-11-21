package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.tenant.Tenant;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** Calls whose hang-up never arrived are closed, and billed only up to their maximum length. */
class StaleCallSweeperTest {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 21, 12, 0, 0, 0, ZoneOffset.UTC);
    private CallRepository calls;
    private CallPipelineService pipeline;
    private CallLiveness liveness;
    private StaleCallSweeper sweeper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        calls = mock(CallRepository.class);
        pipeline = mock(CallPipelineService.class);
        liveness = mock(CallLiveness.class);
        ObjectProvider<CallLiveness> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(liveness);
        sweeper = new StaleCallSweeper(calls, pipeline, provider, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void aPhoneCallTheProviderSaysEndedIsClosedAtItsMaximumLength() {
        var call = call("inbound", Duration.ofMinutes(20), 300);
        when(liveness.isAlive("v3:stale")).thenReturn(Optional.of(false));

        sweeper.sweep();

        verify(pipeline).closeStaleCall(call.getId(), call.getStartedAt().plusSeconds(300));
    }

    @Test
    void aPhoneCallStillConnectedIsLeftAlone() {
        call("inbound", Duration.ofMinutes(20), 300);
        when(liveness.isAlive("v3:stale")).thenReturn(Optional.of(true));

        sweeper.sweep();

        verify(pipeline, never()).closeStaleCall(any(), any());
    }

    @Test
    void whenTheProviderCannotBeAskedThePhoneCallGetsAnHourMore() {
        call("inbound", Duration.ofMinutes(20), 300);
        when(liveness.isAlive("v3:stale")).thenReturn(Optional.empty());

        sweeper.sweep();
        verify(pipeline, never()).closeStaleCall(any(), any());

        var older = call("inbound", Duration.ofMinutes(70), 300);
        sweeper.sweep();
        verify(pipeline).closeStaleCall(older.getId(), older.getStartedAt().plusSeconds(300));
    }

    @Test
    void aBrowserCallIsClosedAfterTheGraceWithoutAskingTheProvider() {
        var call = call("test", Duration.ofMinutes(11), 300);

        sweeper.sweep();

        verify(pipeline).closeStaleCall(call.getId(), call.getStartedAt().plusSeconds(300));
        verify(liveness, never()).isAlive(any());
    }

    @Test
    void aCallStillWithinItsMaximumLengthAndGraceIsLeftAlone() {
        call("test", Duration.ofMinutes(9), 300);

        sweeper.sweep();

        verify(pipeline, never()).closeStaleCall(any(), any());
    }

    @Test
    void closingLateBillsOnlyUpToTheGivenEnd() {
        var tenant = new Tenant("Clinic", "owner@example.com", "KE");
        var call = new Call(tenant, mock(Agent.class), "v3:late", "+254700000000", "inbound");
        var before = tenant.getMinutesUsedThisCycle();

        call.complete("completed", call.getStartedAt().plusMinutes(4));

        assertThat(call.getDurationSeconds()).isEqualTo(240);
        assertThat(tenant.getMinutesUsedThisCycle() - before).isEqualTo(4);
    }

    private Call call(String direction, Duration age, int maxSeconds) {
        var agent = mock(Agent.class);
        when(agent.getMaxCallDurationSeconds()).thenReturn(maxSeconds);
        var call = mock(Call.class);
        when(call.getId()).thenReturn(UUID.randomUUID());
        when(call.getAgent()).thenReturn(agent);
        when(call.getDirection()).thenReturn(direction);
        when(call.getTwilioCallSid()).thenReturn("v3:stale");
        when(call.getStartedAt()).thenReturn(NOW.minus(age));
        when(calls.findActiveVoiceCallsStartedBefore(any(), any())).thenReturn(List.of(call));
        return call;
    }
}
