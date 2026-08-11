package com.sauti.billing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds an observe-only financial projection from normalized provider evidence.
 * It never grants balance or changes access. The newest event for each provider
 * resource wins, so webhook retries and refund/dispute updates are idempotent.
 */
@Service
public class BillingFinancialReconciliationService {
    private static final String PROVIDER = "whop";
    private final BillingProviderEvidenceRepository evidence;

    public BillingFinancialReconciliationService(BillingProviderEvidenceRepository evidence) {
        this.evidence = evidence;
    }

    @Transactional(readOnly = true)
    public FinancialSummary summarize(boolean testMode) {
        return summarize(testMode,
                evidence.findAllByProviderAndTestModeOrderByOccurredAtAsc(PROVIDER, testMode));
    }

    FinancialSummary summarize(boolean testMode, List<BillingProviderEvidence> stored) {
        var grouped = new LinkedHashMap<String, List<BillingProviderEvidence>>();
        for (var item : stored) {
            if (item.getProviderPaymentId() == null) continue;
            grouped.computeIfAbsent(item.getProviderPaymentId(), ignored -> new ArrayList<>()).add(item);
        }
        var positions = grouped.entrySet().stream()
                .map(entry -> position(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(FinancialPosition::lastEvidenceAt).reversed())
                .toList();
        var totals = new LinkedHashMap<String, MutableTotals>();
        for (var position : positions) {
            if (position.currency() == null || position.unresolved()) continue;
            totals.computeIfAbsent(position.currency(), ignored -> new MutableTotals())
                    .accept(position);
        }
        return new FinancialSummary(testMode ? "sandbox" : "live", positions.size(),
                positions.stream().filter(item -> "paid".equals(item.state())).count(),
                positions.stream().filter(item -> "partially_refunded".equals(item.state())).count(),
                positions.stream().filter(item -> "refunded".equals(item.state())).count(),
                positions.stream().filter(item -> "disputed".equals(item.state())).count(),
                positions.stream().filter(item -> "dispute_lost".equals(item.state())).count(),
                positions.stream().filter(FinancialPosition::unresolved).count(),
                totals.entrySet().stream().map(entry -> entry.getValue().view(entry.getKey())).toList(),
                positions.stream().limit(10).toList(),
                positions.stream().map(FinancialPosition::lastEvidenceAt)
                        .max(Comparator.naturalOrder()).orElse(null));
    }

    private static FinancialPosition position(String paymentId, List<BillingProviderEvidence> items) {
        var payment = latest(items, "payment");
        var refunds = latestByResource(items, "refund");
        var disputes = latestByResource(items, "dispute");
        var gross = payment == null || payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        var currency = payment == null ? null : payment.getCurrency();
        var unresolved = payment == null || currency == null;
        var refunded = BigDecimal.ZERO;
        var openDispute = BigDecimal.ZERO;
        var disputeLoss = BigDecimal.ZERO;
        for (var refund : refunds) {
            unresolved |= currencyMismatch(currency, refund);
            if (successfulRefund(refund.getNormalizedStatus()) && refund.getAmount() != null) {
                refunded = refunded.add(refund.getAmount());
            }
        }
        for (var dispute : disputes) {
            unresolved |= currencyMismatch(currency, dispute);
            if (lostDispute(dispute.getNormalizedStatus()) && dispute.getAmount() != null) {
                disputeLoss = disputeLoss.add(dispute.getAmount());
            } else if (openDispute(dispute.getNormalizedStatus()) && dispute.getAmount() != null) {
                openDispute = openDispute.add(dispute.getAmount());
            } else if (!resolvedDispute(dispute.getNormalizedStatus())) {
                unresolved = true;
            }
        }
        var paymentStatus = payment == null ? "" : clean(payment.getNormalizedStatus());
        if (fullRefund(paymentStatus) && refunded.signum() == 0) refunded = gross;
        if ("partially_refunded".equals(paymentStatus) && refunded.signum() == 0) unresolved = true;
        if (lostDispute(paymentStatus) && disputeLoss.signum() == 0) disputeLoss = gross;
        if (openDispute(paymentStatus) && openDispute.signum() == 0) openDispute = gross;
        if (refunded.add(disputeLoss).compareTo(gross) > 0) unresolved = true;
        var deductions = refunded.add(disputeLoss).min(gross);
        var net = gross.subtract(deductions).max(BigDecimal.ZERO);
        var state = state(payment, gross, refunded, openDispute, disputeLoss, unresolved);
        var lastAt = items.stream().map(BillingProviderEvidence::getOccurredAt)
                .max(Comparator.naturalOrder()).orElse(OffsetDateTime.now());
        return new FinancialPosition(mask(paymentId), state, currency, gross, refunded,
                disputeLoss, net, openDispute, unresolved, lastAt);
    }

    private static String state(BillingProviderEvidence payment, BigDecimal gross,
                                BigDecimal refunded, BigDecimal openDispute,
                                BigDecimal disputeLoss, boolean unresolved) {
        if (unresolved) return "unresolved";
        if (openDispute.signum() > 0) return "disputed";
        if (disputeLoss.signum() > 0) return "dispute_lost";
        if (gross.signum() > 0 && refunded.compareTo(gross) >= 0) return "refunded";
        if (refunded.signum() > 0) return "partially_refunded";
        return payment != null && successfulPayment(payment.getNormalizedStatus()) ? "paid" : "pending";
    }

    private static BillingProviderEvidence latest(List<BillingProviderEvidence> items, String type) {
        return items.stream().filter(item -> type.equals(item.getRecordType()))
                .max(Comparator.comparing(BillingProviderEvidence::getOccurredAt)).orElse(null);
    }

    private static List<BillingProviderEvidence> latestByResource(
            List<BillingProviderEvidence> items, String type) {
        var latest = new LinkedHashMap<String, BillingProviderEvidence>();
        items.stream().filter(item -> type.equals(item.getRecordType()))
                .sorted(Comparator.comparing(BillingProviderEvidence::getOccurredAt))
                .forEach(item -> latest.put(item.getProviderResourceId(), item));
        return List.copyOf(latest.values());
    }

    private static boolean currencyMismatch(String currency, BillingProviderEvidence item) {
        return item.getAmount() != null && (currency == null || item.getCurrency() == null
                || !currency.equals(item.getCurrency()));
    }

    private static boolean successfulPayment(String status) {
        return List.of("succeeded", "paid", "dispute_won", "resolution_won").contains(clean(status));
    }

    private static boolean successfulRefund(String status) {
        return List.of("succeeded", "completed", "refunded").contains(clean(status));
    }

    private static boolean lostDispute(String status) {
        return clean(status).contains("lost");
    }

    private static boolean resolvedDispute(String status) {
        return clean(status).contains("won");
    }

    private static boolean openDispute(String status) {
        var value = clean(status);
        return value.contains("needs_response") || value.contains("under_review")
                || value.equals("warning") || value.equals("dispute_warning")
                || value.equals("open") || value.equals("open_dispute")
                || value.equals("open_resolution");
    }

    private static boolean fullRefund(String status) {
        return List.of("refunded", "auto_refunded").contains(clean(status));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String mask(String value) {
        return value.length() <= 8 ? value : "…" + value.substring(value.length() - 8);
    }

    private static final class MutableTotals {
        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal refunded = BigDecimal.ZERO;
        private BigDecimal disputeLoss = BigDecimal.ZERO;
        private BigDecimal net = BigDecimal.ZERO;
        private BigDecimal openExposure = BigDecimal.ZERO;

        void accept(FinancialPosition position) {
            gross = gross.add(position.gross());
            refunded = refunded.add(position.refunded());
            disputeLoss = disputeLoss.add(position.disputeLoss());
            net = net.add(position.net());
            openExposure = openExposure.add(position.openDisputeExposure());
        }

        CurrencyTotals view(String currency) {
            return new CurrencyTotals(currency, gross, refunded, disputeLoss, net, openExposure);
        }
    }

    public record FinancialSummary(String environment, long payments, long paid,
                                   long partiallyRefunded, long refunded, long openDisputes,
                                   long disputeLost, long unresolved,
                                   List<CurrencyTotals> totals,
                                   List<FinancialPosition> recentPositions,
                                   OffsetDateTime lastReconciledAt) { }

    public record CurrencyTotals(String currency, BigDecimal gross, BigDecimal refunded,
                                 BigDecimal disputeLoss, BigDecimal net,
                                 BigDecimal openDisputeExposure) { }

    public record FinancialPosition(String paymentReference, String state, String currency,
                                    BigDecimal gross, BigDecimal refunded,
                                    BigDecimal disputeLoss, BigDecimal net,
                                    BigDecimal openDisputeExposure, boolean unresolved,
                                    OffsetDateTime lastEvidenceAt) { }
}
