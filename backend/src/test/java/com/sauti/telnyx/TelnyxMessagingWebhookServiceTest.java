package com.sauti.telnyx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.billing.ProviderCostReconciliationService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TelnyxMessagingWebhookServiceTest {
    @Test
    void reconcilesOnlyTheFinalizedOutboundMessageCost() {
        var events = mock(TelnyxWebhookEventRepository.class);
        var reconciliation = mock(ProviderCostReconciliationService.class);
        when(events.existsByProviderEventId("event-1")).thenReturn(false);
        when(events.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.findByProviderEventId("event-1"))
                .thenReturn(Optional.of(new TelnyxWebhookEvent("event-1", "message.finalized", "msg-1", null)));
        var service = new TelnyxMessagingWebhookService(new ObjectMapper(), events, reconciliation);

        service.accept("""
                {"data":{"id":"event-1","event_type":"message.finalized",
                 "occurred_at":"2026-08-02T12:00:00Z","payload":{"id":"msg-1","direction":"outbound",
                 "cost":{"amount":"0.0051","currency":"USD"}}}}
                """);

        verify(reconciliation).reconcileTelnyxFinalizedMessage(eq("msg-1"), any());
    }
}
