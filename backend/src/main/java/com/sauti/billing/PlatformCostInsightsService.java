package com.sauti.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformCostInsightsService {
    private final CommunicationLedgerRepository ledger;
    private final ProviderCostReconciliationRepository reconciliation;

    public PlatformCostInsightsService(CommunicationLedgerRepository ledger,
                                       ProviderCostReconciliationRepository reconciliation) {
        this.ledger = ledger;
        this.reconciliation = reconciliation;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(OffsetDateTime from) {
        var entries = ledger.findAllByCreatedAtGreaterThanEqual(from);
        var monetary = entries.stream()
                .filter(entry -> entry.getAmount() != null && entry.getCurrency() != null)
                .toList();
        return new Snapshot(costTotals(monetary), dailyCosts(monetary), unpriced(entries),
                reconciliation(reconciliation.findAllByCreatedAtGreaterThanEqual(from)));
    }

    private List<CostTotal> costTotals(List<CommunicationLedgerEntry> entries) {
        var totals = new LinkedHashMap<String, BigDecimal>();
        entries.forEach(entry -> totals.merge(
                entry.getCurrency() + "|" + entry.getCostBasis() + "|" + entry.getCategory(),
                signed(entry), BigDecimal::add));
        return totals.entrySet().stream().map(entry -> {
            var parts = entry.getKey().split("\\|", 3);
            return new CostTotal(parts[0], parts[1], parts[2], entry.getValue());
        }).sorted(Comparator.comparing(CostTotal::currency)
                .thenComparing(CostTotal::costBasis).thenComparing(CostTotal::category)).toList();
    }

    private List<DailyCost> dailyCosts(List<CommunicationLedgerEntry> entries) {
        var totals = new LinkedHashMap<String, BigDecimal>();
        entries.forEach(entry -> {
            var date = entry.getCreatedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
            totals.merge(date + "|" + entry.getCurrency(), signed(entry), BigDecimal::add);
        });
        return totals.entrySet().stream().map(entry -> {
            var parts = entry.getKey().split("\\|", 2);
            return new DailyCost(LocalDate.parse(parts[0]).toString(), parts[1], entry.getValue());
        }).sorted(Comparator.comparing(DailyCost::date).thenComparing(DailyCost::currency)).toList();
    }

    private List<UnpricedUsage> unpriced(List<CommunicationLedgerEntry> entries) {
        var totals = new LinkedHashMap<String, BigDecimal>();
        entries.stream().filter(entry -> "unpriced".equals(entry.getCostBasis())).forEach(entry ->
                totals.merge(entry.getCategory() + "|" + entry.getUnit(), signedQuantity(entry), BigDecimal::add));
        return totals.entrySet().stream().filter(entry -> entry.getValue().signum() != 0).map(entry -> {
            var parts = entry.getKey().split("\\|", 2);
            return new UnpricedUsage(parts[0], parts[1], entry.getValue());
        }).sorted(Comparator.comparing(UnpricedUsage::category).thenComparing(UnpricedUsage::unit)).toList();
    }

    private List<ReconciliationHealth> reconciliation(List<ProviderCostReconciliationJob> jobs) {
        var grouped = jobs.stream().collect(java.util.stream.Collectors.groupingBy(
                ProviderCostReconciliationJob::getProvider));
        return grouped.entrySet().stream().map(entry -> {
            Map<String, Long> statuses = entry.getValue().stream().collect(java.util.stream.Collectors.groupingBy(
                    ProviderCostReconciliationJob::getStatus, java.util.stream.Collectors.counting()));
            return new ReconciliationHealth(entry.getKey(), statuses.getOrDefault("pending", 0L),
                    statuses.getOrDefault("retrying", 0L), statuses.getOrDefault("reconciled", 0L),
                    statuses.getOrDefault("estimated", 0L), statuses.getOrDefault("unavailable", 0L));
        }).sorted(Comparator.comparing(ReconciliationHealth::provider)).toList();
    }

    private BigDecimal signed(CommunicationLedgerEntry entry) {
        return "credit".equals(entry.getDirection()) ? entry.getAmount().negate() : entry.getAmount();
    }

    private BigDecimal signedQuantity(CommunicationLedgerEntry entry) {
        return "credit".equals(entry.getDirection()) ? entry.getQuantity().negate() : entry.getQuantity();
    }

    public record Snapshot(List<CostTotal> costTotals, List<DailyCost> dailyCosts,
                           List<UnpricedUsage> unpricedUsage,
                           List<ReconciliationHealth> reconciliation) { }
    public record CostTotal(String currency, String costBasis, String category, BigDecimal amount) { }
    public record DailyCost(String date, String currency, BigDecimal amount) { }
    public record UnpricedUsage(String category, String unit, BigDecimal quantity) { }
    public record ReconciliationHealth(String provider, long pending, long retrying, long reconciled,
                                       long estimated, long unavailable) { }
}
