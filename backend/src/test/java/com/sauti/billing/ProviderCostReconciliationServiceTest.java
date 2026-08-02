package com.sauti.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.CallRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderCostReconciliationServiceTest {
    @Test
    void reconcilesTheFullTelnyxVoiceSessionCostTree() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/session_analysis/call-session/session-1", exchange -> {
            var response = """
                    {"session_id":"session-1","cost":{"total":"0.056800","currency":"USD"},
                     "meta":{"products":["call-control","ai-voice-assistant","inference"]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var jobs = mock(ProviderCostReconciliationRepository.class);
            var ledger = mock(BillingLedgerService.class);
            var tenantId = UUID.randomUUID();
            var callId = UUID.randomUUID().toString();
            var job = new ProviderCostReconciliationJob(
                    tenantId, "telnyx", "voice_session", "session-1", callId, OffsetDateTime.now());
            when(jobs.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                    any(List.class), any(OffsetDateTime.class))).thenReturn(List.of(job));
            when(ledger.amountTotal(tenantId, "voice_provider_cost", callId, "USD"))
                    .thenReturn(BigDecimal.ZERO);
            var service = new ProviderCostReconciliationService(
                    jobs, ledger, new ProviderCostRateCard("USD", "0", "0", "0"),
                    mock(CallRepository.class), new ObjectMapper(), "test-key",
                    "http://127.0.0.1:" + server.getAddress().getPort(), 8);

            service.reconcileDue();

            verify(ledger).recordProviderCost(
                    eq(tenantId), eq("voice_provider_cost"), eq(new BigDecimal("0.056800")), eq("USD"),
                    any(String.class), eq(callId), eq("Telnyx-confirmed provider cost"), anyMap());
            org.assertj.core.api.Assertions.assertThat(job.getStatus()).isEqualTo("reconciled");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recordsFinalizedTelnyxMessageCostAsProviderConfirmed() throws Exception {
        var fixture = fixture(8);
        var tenantId = UUID.randomUUID();
        var job = new ProviderCostReconciliationJob(
                tenantId, "telnyx", "sms_message", "msg-1", "msg-1", OffsetDateTime.now());
        when(fixture.jobs.findFirstByProviderAndResourceTypeAndProviderResourceId(
                "telnyx", "sms_message", "msg-1")).thenReturn(Optional.of(job));
        when(fixture.ledger.amountTotal(tenantId, "sms_provider_cost", "msg-1", "USD"))
                .thenReturn(BigDecimal.ZERO);
        var payload = new ObjectMapper().readTree("""
                {"id":"msg-1","parts":2,"cost":{"amount":"0.0071","currency":"USD"},
                 "cost_breakdown":{"carrier_fee":{"amount":"0.0031"},"rate":{"amount":"0.0040"}}}
                """);

        fixture.service.reconcileTelnyxFinalizedMessage("msg-1", payload);

        verify(fixture.ledger).recordProviderCost(
                eq(tenantId), eq("sms_provider_cost"), eq(new BigDecimal("0.0071")), eq("USD"),
                any(String.class), eq("msg-1"), eq("Telnyx-confirmed provider cost"), anyMap());
        verify(fixture.jobs).save(job);
        org.assertj.core.api.Assertions.assertThat(job.getStatus()).isEqualTo("reconciled");
    }

    @Test
    void usesExplicitRateCardOnlyAfterReconciliationAttemptsAreExhausted() {
        var fixture = fixture(1);
        var tenantId = UUID.randomUUID();
        var callId = UUID.randomUUID().toString();
        var job = new ProviderCostReconciliationJob(
                tenantId, "telnyx", "voice_session", UUID.randomUUID().toString(), callId, OffsetDateTime.now());
        when(fixture.jobs.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                any(List.class), any(OffsetDateTime.class))).thenReturn(List.of(job));
        when(fixture.ledger.quantityTotal(tenantId, "voice_call", callId)).thenReturn(new BigDecimal("2.5000"));

        fixture.service.reconcileDue();

        verify(fixture.ledger).recordRateCardCost(
                eq(tenantId), eq("voice_provider_cost"), eq(new BigDecimal("2.5000")), eq("minute"),
                eq(new BigDecimal("0.250000")), eq("USD"), any(String.class), eq(callId),
                eq("Configured provider cost estimate after reconciliation timeout"), anyMap());
        org.assertj.core.api.Assertions.assertThat(job.getStatus()).isEqualTo("estimated");
    }

    private Fixture fixture(int maxAttempts) {
        var jobs = mock(ProviderCostReconciliationRepository.class);
        var ledger = mock(BillingLedgerService.class);
        var rateCard = new ProviderCostRateCard("USD", "0.10", "0.02", "0.03");
        var service = new ProviderCostReconciliationService(
                jobs, ledger, rateCard, mock(CallRepository.class), new ObjectMapper(), "", "http://localhost", maxAttempts);
        return new Fixture(jobs, ledger, service);
    }

    private record Fixture(ProviderCostReconciliationRepository jobs, BillingLedgerService ledger,
                           ProviderCostReconciliationService service) { }
}
