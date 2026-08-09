package com.sauti.billing;

import com.sauti.reliability.QueueHealthContributor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BillingQueueHealthContributor implements QueueHealthContributor {
    private final BillingProviderEventRepository events;
    private final ProviderCostReconciliationRepository reconciliation;

    public BillingQueueHealthContributor(BillingProviderEventRepository events,
                                         ProviderCostReconciliationRepository reconciliation) {
        this.events = events;
        this.reconciliation = reconciliation;
    }

    @Override
    public List<QueueState> snapshot() {
        var oldestEvent = events.findFirstByStatusInOrderByCreatedAtAsc(List.of("pending", "retrying"))
                .map(BillingProviderEvent::getCreatedAt).orElse(null);
        var oldestCost = reconciliation.findFirstByStatusInOrderByCreatedAtAsc(List.of("pending", "retrying"))
                .map(ProviderCostReconciliationJob::getCreatedAt).orElse(null);
        return List.of(
                new QueueState("billing_event", "Billing event processing",
                        events.countByStatus("pending"), events.countByStatus("retrying"),
                        events.countByStatus("failed"), oldestEvent),
                new QueueState("cost_reconciliation", "Provider cost reconciliation",
                        reconciliation.countByStatus("pending"), reconciliation.countByStatus("retrying"),
                        reconciliation.countByStatus("unavailable"), oldestCost)
        );
    }
}
