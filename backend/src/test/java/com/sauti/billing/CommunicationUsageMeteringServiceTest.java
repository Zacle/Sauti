package com.sauti.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.tenant.Tenant;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommunicationUsageMeteringServiceTest {
    @Test
    void keysOutboundMessagesByProviderReference() {
        var fixture = fixture();
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();

        fixture.service.meterOutboundMessage(
                tenantId, agentId, "whatsapp", "wamid.123", "fallback-1", "template");

        verify(fixture.ledger).recordDebit(
                eq(tenantId), eq("whatsapp_message"), eq(BigDecimal.ONE), eq("message"),
                eq(null), eq(null), eq("outbound-message:whatsapp:wamid.123"), eq("wamid.123"),
                eq("Provider-accepted outbound whatsapp message"), anyMap());
    }

    @Test
    void recordsExactCompletedVoiceMinutesOnceThroughTheLedger() {
        var fixture = fixture();
        var tenantId = UUID.randomUUID();
        var callId = UUID.randomUUID();
        var agent = mock(Agent.class);
        var call = mock(Call.class);
        when(agent.getId()).thenReturn(UUID.randomUUID());
        when(call.getAgent()).thenReturn(agent);
        when(call.getEndedAt()).thenReturn(OffsetDateTime.now());
        when(call.getDurationSeconds()).thenReturn(90);
        when(call.getDirection()).thenReturn("inbound");
        when(call.getOutcome()).thenReturn("completed");
        when(fixture.calls.findByIdAndTenantId(callId, tenantId)).thenReturn(Optional.of(call));
        when(fixture.ledger.quantityTotal(tenantId, "voice_call", callId.toString()))
                .thenReturn(BigDecimal.ZERO);

        fixture.service.meterCompletedCall(tenantId, callId);

        verify(fixture.ledger).recordDebit(
                eq(tenantId), eq("voice_call"), eq(new BigDecimal("1.5000")), eq("minute"),
                eq(null), eq(null), eq("voice-call:" + callId + ":snapshot:seconds-90"), eq(callId.toString()),
                eq("Completed voice call usage adjustment"), anyMap());
    }

    @Test
    void creditsTheDifferenceWhenAuthoritativeCallDurationDecreases() {
        var fixture = fixture();
        var tenantId = UUID.randomUUID();
        var callId = UUID.randomUUID();
        var agent = mock(Agent.class);
        var call = mock(Call.class);
        when(agent.getId()).thenReturn(UUID.randomUUID());
        when(call.getAgent()).thenReturn(agent);
        when(call.getEndedAt()).thenReturn(OffsetDateTime.now());
        when(call.getDurationSeconds()).thenReturn(90);
        when(call.getDirection()).thenReturn("outbound");
        when(call.getOutcome()).thenReturn("completed");
        when(fixture.calls.findByIdAndTenantId(callId, tenantId)).thenReturn(Optional.of(call));
        when(fixture.ledger.quantityTotal(tenantId, "voice_call", callId.toString()))
                .thenReturn(new BigDecimal("2.0000"));

        fixture.service.meterCompletedCall(tenantId, callId);

        verify(fixture.ledger).recordUnpricedCredit(
                eq(tenantId), eq("voice_call"), eq(new BigDecimal("0.5000")), eq("minute"),
                eq("voice-call:" + callId + ":snapshot:seconds-90"), eq(callId.toString()),
                eq("Authoritative voice call usage correction"), anyMap());
    }

    @Test
    void accruesKnownNumberRentalOncePerCalendarMonth() {
        var fixture = fixture();
        var tenantId = UUID.randomUUID();
        var tenant = mock(Tenant.class);
        var agent = mock(Agent.class);
        var purchase = mock(CommunicationLedgerEntry.class);
        when(tenant.getId()).thenReturn(tenantId);
        when(agent.getId()).thenReturn(UUID.randomUUID());
        when(agent.getTenant()).thenReturn(tenant);
        when(agent.getTwilioPhoneNumber()).thenReturn("+447911123456");
        when(purchase.getCreatedAt()).thenReturn(OffsetDateTime.now().minusMonths(2));
        when(purchase.getMetadataJson()).thenReturn("{\"monthlyCost\":\"2.50\"}");
        when(purchase.getCurrency()).thenReturn("USD");
        when(fixture.agents.findAllByTwilioPhoneNumberIsNotNull()).thenReturn(List.of(agent));
        when(fixture.ledger.latestPhoneNumberPurchase(tenantId, "+447911123456"))
                .thenReturn(purchase);

        fixture.service.accrueMonthlyNumberRentals();

        verify(fixture.ledger).recordDebit(
                eq(tenantId), eq("phone_number_rental"), eq(BigDecimal.ONE), eq("number_month"),
                eq(new BigDecimal("2.50")), eq("USD"), any(String.class), eq("+447911123456"),
                any(String.class), anyMap());
    }

    private Fixture fixture() {
        var ledger = mock(BillingLedgerService.class);
        var calls = mock(CallRepository.class);
        var agents = mock(AgentRepository.class);
        return new Fixture(ledger, calls, agents,
                new CommunicationUsageMeteringService(
                        ledger, calls, agents, new ObjectMapper(), mock(ProviderCostReconciliationService.class)));
    }

    private record Fixture(BillingLedgerService ledger, CallRepository calls, AgentRepository agents,
                           CommunicationUsageMeteringService service) { }
}
