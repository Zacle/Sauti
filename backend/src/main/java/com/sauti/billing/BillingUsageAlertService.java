package com.sauti.billing;

import com.sauti.call.CallRepository;
import com.sauti.notification.WorkspaceNotificationService;
import com.sauti.tenant.TenantRepository;
import com.sauti.tenant.domain.WorkspaceStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells a workspace owner when its call minutes, or its outbound messages, reach 80% and 100% of the
 * allowance: once by email and once in the console's notifications, per cycle and allowance.
 *
 * <p>Checks run for workspaces with a call that ended, or a message sent, recently, so every way usage
 * can grow is covered without hooking each one. Checking twice never alerts twice: an alert that exists
 * is not recreated, and the table's unique key backs that up.
 */
@Service
public class BillingUsageAlertService {
    static final int[] THRESHOLDS = {80, 100};
    /** Much longer than the scan interval, so a restart or a slow scan doesn't miss anything. */
    static final Duration LOOKBACK = Duration.ofHours(6);

    private final TenantRepository tenants;
    private final CallRepository calls;
    private final CommunicationLedgerRepository ledger;
    private final WorkspaceUsageService usage;
    private final BillingUsageAlertRepository alerts;
    private final WorkspaceNotificationService notifications;

    public BillingUsageAlertService(TenantRepository tenants, CallRepository calls, CommunicationLedgerRepository ledger,
                                    WorkspaceUsageService usage, BillingUsageAlertRepository alerts,
                                    WorkspaceNotificationService notifications) {
        this.tenants = tenants;
        this.calls = calls;
        this.ledger = ledger;
        this.usage = usage;
        this.alerts = alerts;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<UUID> recentlyActiveWorkspaces() {
        var since = OffsetDateTime.now().minus(LOOKBACK);
        var ids = new LinkedHashSet<UUID>(calls.tenantIdsWithCallsEndedSince(since));
        ids.addAll(ledger.tenantIdsWithMessagesSince(since));
        return List.copyOf(ids);
    }

    /** Records any threshold the workspace has newly reached; returns the alerts that will be sent. */
    @Transactional
    public List<BillingUsageAlert> check(UUID tenantId) {
        var tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null || tenant.workspaceStatus() != WorkspaceStatus.ACTIVE) return List.of();
        var current = usage.current(tenant);
        var created = new ArrayList<BillingUsageAlert>();
        for (var kind : List.of(BillingUsageAlert.MINUTES, BillingUsageAlert.MESSAGES)) {
            var messages = BillingUsageAlert.MESSAGES.equals(kind);
            var included = messages ? current.includedMessages() : current.includedMinutes();
            var used = messages ? current.usedMessages() : current.usedMinutes();
            if (included <= 0) continue;
            var reached = 0;
            for (var threshold : THRESHOLDS) {
                if ((long) used * 100 >= (long) threshold * included) reached = threshold;
            }
            for (var threshold : THRESHOLDS) {
                if (threshold > reached) break;
                if (alerts.existsByTenantIdAndKindAndCycleStartsAtAndIncludedAmountAndThresholdPercent(
                        tenantId, kind, current.cycleStartsAt(), included, threshold)) continue;
                var skipped = threshold < reached;
                var alert = alerts.save(new BillingUsageAlert(tenantId, kind, current, threshold,
                        tenant.getEmail(), tenant.getBusinessName(), skipped));
                if (skipped) continue;
                notifications.usageThresholdReached(tenant, alert.getId(), messages, threshold, used, included,
                        current.trial(), current.cycleEndsAt());
                created.add(alert);
            }
        }
        return created;
    }
}
