package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingFinancialReconciliationServiceTest {
    private final BillingFinancialReconciliationService service =
            new BillingFinancialReconciliationService(mock(BillingProviderEvidenceRepository.class));

    @Test
    void usesLatestRefundResourceStateWithoutDoubleCountingUpdates() {
        var result = service.summarize(true, List.of(
                evidence("payment", "pay_123456789", "pay_123456789", "succeeded", "149", "USD", "08:00"),
                evidence("refund", "refund_1", "pay_123456789", "pending", "40", "USD", "08:01"),
                evidence("refund", "refund_1", "pay_123456789", "succeeded", "40", "USD", "08:02")));

        assertThat(result.payments()).isEqualTo(1);
        assertThat(result.partiallyRefunded()).isEqualTo(1);
        assertThat(result.totals().get(0).refunded()).isEqualByComparingTo("40");
        assertThat(result.totals().get(0).net()).isEqualByComparingTo("109");
        assertThat(result.recentPositions().get(0).paymentReference()).isEqualTo("…23456789");
    }

    @Test
    void reportsOpenDisputeAsExposureWithoutRemovingCollectedAmount() {
        var result = service.summarize(false, List.of(
                evidence("payment", "pay_1", "pay_1", "succeeded", "49", "USD", "08:00"),
                evidence("dispute", "dispute_1", "pay_1", "needs_response", "49", "USD", "08:01")));

        assertThat(result.openDisputes()).isEqualTo(1);
        assertThat(result.totals().get(0).net()).isEqualByComparingTo("49");
        assertThat(result.totals().get(0).openDisputeExposure()).isEqualByComparingTo("49");
    }

    @Test
    void failsClosedOnCurrencyMismatch() {
        var result = service.summarize(false, List.of(
                evidence("payment", "pay_1", "pay_1", "succeeded", "49", "USD", "08:00"),
                evidence("refund", "refund_1", "pay_1", "succeeded", "10", "EUR", "08:01")));

        assertThat(result.unresolved()).isEqualTo(1);
        assertThat(result.recentPositions().get(0).state()).isEqualTo("unresolved");
    }

    @Test
    void treatsLostDisputeAsLossButWonDisputeAsResolved() {
        var lost = service.summarize(false, List.of(
                evidence("payment", "pay_1", "pay_1", "succeeded", "149", "USD", "08:00"),
                evidence("dispute", "dispute_1", "pay_1", "lost", "149", "USD", "08:01")));
        var won = service.summarize(false, List.of(
                evidence("payment", "pay_2", "pay_2", "succeeded", "149", "USD", "08:00"),
                evidence("dispute", "dispute_2", "pay_2", "won", "149", "USD", "08:01")));

        assertThat(lost.disputeLost()).isEqualTo(1);
        assertThat(lost.totals().get(0).net()).isEqualByComparingTo("0");
        assertThat(won.paid()).isEqualTo(1);
        assertThat(won.totals().get(0).net()).isEqualByComparingTo("149");
    }

    @Test
    void recognizesPaymentLevelRefundAndResolutionStatuses() {
        var refunded = service.summarize(false, List.of(
                evidence("payment", "pay_1", "pay_1", "auto_refunded", "49", "USD", "08:00")));
        var disputed = service.summarize(false, List.of(
                evidence("payment", "pay_2", "pay_2", "open_resolution", "49", "USD", "08:00")));
        var lost = service.summarize(false, List.of(
                evidence("payment", "pay_3", "pay_3", "resolution_lost", "49", "USD", "08:00")));

        assertThat(refunded.refunded()).isEqualTo(1);
        assertThat(refunded.totals().get(0).net()).isEqualByComparingTo("0");
        assertThat(disputed.openDisputes()).isEqualTo(1);
        assertThat(disputed.totals().get(0).openDisputeExposure()).isEqualByComparingTo("49");
        assertThat(lost.disputeLost()).isEqualTo(1);
    }

    private static BillingProviderEvidence evidence(String type, String resourceId, String paymentId,
                                                      String status, String amount, String currency,
                                                      String time) {
        return new BillingProviderEvidence(UUID.randomUUID(), UUID.randomUUID(), "whop", type,
                type + ".updated", resourceId, paymentId, "mem_1", "plan_1", status,
                new BigDecimal(amount), currency, true,
                OffsetDateTime.parse("2026-08-11T" + time + ":00Z"));
    }
}
