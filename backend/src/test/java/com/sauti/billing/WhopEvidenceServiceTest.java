package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WhopEvidenceServiceTest {
    private final BillingProviderEvidenceRepository evidence = mock(BillingProviderEvidenceRepository.class);
    private final BillingSubscriptionRepository subscriptions = mock(BillingSubscriptionRepository.class);
    private final BillingAddOnSubscriptionRepository addOns = mock(BillingAddOnSubscriptionRepository.class);
    private final BillingPaymentNotificationRepository paymentNotifications =
            mock(BillingPaymentNotificationRepository.class);
    private final WhopEvidenceService service = new WhopEvidenceService(
            evidence, subscriptions, addOns, paymentNotifications, new ObjectMapper());

    @Test
    void normalizesPaymentWithoutCopyingCustomerData() throws Exception {
        var tenantId = UUID.randomUUID();
        var subscription = subscription(tenantId, "mem_1");
        var event = event("payment.succeeded", """
                {"id":"msg_1","timestamp":"2026-08-11T08:00:00Z","data":{
                  "id":"pay_1","status":"paid","substatus":"succeeded",
                  "total":149.00,"currency":"usd","membership":{"id":"mem_1"},
                  "plan":{"id":"plan_growth"},"user":{"email":"private@example.com"}}}
                """);
        when(subscriptions.findByProviderAndProviderSubscriptionId("whop", "mem_1"))
                .thenReturn(Optional.of(subscription));

        service.record(event, true);

        var captured = ArgumentCaptor.forClass(BillingProviderEvidence.class);
        verify(evidence).save(captured.capture());
        var record = captured.getValue();
        assertThat(record.getTenantId()).isEqualTo(tenantId);
        assertThat(record.getRecordType()).isEqualTo("payment");
        assertThat(record.getProviderResourceId()).isEqualTo("pay_1");
        assertThat(record.getProviderMembershipId()).isEqualTo("mem_1");
        assertThat(record.getNormalizedStatus()).isEqualTo("succeeded");
        assertThat(record.getAmount()).isEqualByComparingTo("149.00");
        assertThat(record.getCurrency()).isEqualTo("USD");
        assertThat(record.isTestMode()).isTrue();
        assertThat(record.getOccurredAt()).isEqualTo(OffsetDateTime.parse("2026-08-11T08:00:00Z"));
    }

    @Test
    void resolvesDisputeThroughPreviouslyNormalizedPaymentOwnership() throws Exception {
        var tenantId = UUID.randomUUID();
        var payment = new BillingProviderEvidence(UUID.randomUUID(), tenantId, "whop", "payment",
                "payment.succeeded", "pay_1", "pay_1", "mem_old", "plan_growth",
                "succeeded", new java.math.BigDecimal("149.00"), "USD", true,
                OffsetDateTime.parse("2026-08-10T08:00:00Z"));
        var event = event("dispute.created", """
                {"id":"msg_2","timestamp":"2026-08-11T09:00:00Z","data":{
                  "id":"dspt_1","amount":149.00,"currency":"usd",
                  "status":"needs_response","payment":{"id":"pay_1"}}}
                """);
        when(evidence.findFirstByProviderAndRecordTypeAndProviderResourceIdOrderByOccurredAtDesc(
                "whop", "payment", "pay_1")).thenReturn(Optional.of(payment));

        service.record(event, true);

        var captured = ArgumentCaptor.forClass(BillingProviderEvidence.class);
        verify(evidence).save(captured.capture());
        assertThat(captured.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captured.getValue().getRecordType()).isEqualTo("dispute");
        assertThat(captured.getValue().getProviderPaymentId()).isEqualTo("pay_1");
    }

    @Test
    void backfillsAnOldPaymentThroughItsIdempotentEmailOwnership() throws Exception {
        var tenantId = UUID.randomUUID();
        var notification = mock(BillingPaymentNotification.class);
        when(notification.getTenantId()).thenReturn(tenantId);
        when(paymentNotifications.findByProviderAndProviderPaymentId("whop", "pay_old"))
                .thenReturn(Optional.of(notification));
        var event = event("payment.succeeded", """
                {"id":"msg_old","timestamp":"2026-08-01T08:00:00Z","data":{
                  "id":"pay_old","substatus":"succeeded","total":49.00,
                  "currency":"usd","membership":{"id":"mem_replaced"}}}
                """);

        service.record(event, true);

        var captured = ArgumentCaptor.forClass(BillingProviderEvidence.class);
        verify(evidence).save(captured.capture());
        assertThat(captured.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void ignoresAnAlreadyNormalizedProviderEvent() throws Exception {
        var event = event("refund.created", "{}");
        when(evidence.findBySourceEventId(event.getId()))
                .thenReturn(Optional.of(mock(BillingProviderEvidence.class)));

        service.record(event, true);

        verify(evidence, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static BillingProviderEvent event(String name, String payload) {
        return new BillingProviderEvent("whop", UUID.randomUUID().toString().replace("-", ""), name, payload);
    }

    private static BillingSubscription subscription(UUID tenantId, String membershipId) {
        var subscription = new BillingSubscription(tenantId, "whop", membershipId);
        subscription.synchronize("user_1", membershipId, "prod_1", "plan_growth",
                "growth", "monthly", "active", true, null, null, null,
                OffsetDateTime.parse("2026-08-11T08:00:00Z"), "", "", "");
        return subscription;
    }
}
