package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformCostInsightsServiceTest {
    @Test
    void separatesConfirmedEstimatedAndUnpricedCostEvidence() {
        var ledger = mock(CommunicationLedgerRepository.class);
        var reconciliation = mock(ProviderCostReconciliationRepository.class);
        var from = OffsetDateTime.now().minusDays(30);
        var confirmed = entry("debit", "voice_provider_cost", "provider_confirmed", "12.50", "USD", "1", "minute");
        var credit = entry("credit", "voice_provider_cost", "provider_confirmed", "2.50", "USD", "1", "minute");
        var unpriced = entry("debit", "llm_tokens", "unpriced", null, null, "4000", "token");
        when(ledger.findAllByCreatedAtGreaterThanEqual(from)).thenReturn(List.of(confirmed, credit, unpriced));
        var job = mock(ProviderCostReconciliationJob.class);
        when(job.getProvider()).thenReturn("telnyx");
        when(job.getStatus()).thenReturn("retrying");
        when(reconciliation.findAllByCreatedAtGreaterThanEqual(from)).thenReturn(List.of(job));

        var result = new PlatformCostInsightsService(ledger, reconciliation).snapshot(from);

        assertThat(result.costTotals()).singleElement().satisfies(total -> {
            assertThat(total.costBasis()).isEqualTo("provider_confirmed");
            assertThat(total.amount()).isEqualByComparingTo("10.00");
        });
        assertThat(result.unpricedUsage()).singleElement().satisfies(usage ->
                assertThat(usage.quantity()).isEqualByComparingTo("4000"));
        assertThat(result.reconciliation()).singleElement().satisfies(health ->
                assertThat(health.retrying()).isEqualTo(1));
    }

    private CommunicationLedgerEntry entry(String direction, String category, String basis,
                                            String amount, String currency, String quantity, String unit) {
        var entry = mock(CommunicationLedgerEntry.class);
        when(entry.getDirection()).thenReturn(direction);
        when(entry.getCategory()).thenReturn(category);
        when(entry.getCostBasis()).thenReturn(basis);
        when(entry.getAmount()).thenReturn(amount == null ? null : new BigDecimal(amount));
        when(entry.getCurrency()).thenReturn(currency);
        when(entry.getQuantity()).thenReturn(new BigDecimal(quantity));
        when(entry.getUnit()).thenReturn(unit);
        when(entry.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-08-02T12:00:00Z"));
        return entry;
    }
}
