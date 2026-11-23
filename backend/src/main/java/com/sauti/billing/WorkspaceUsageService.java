package com.sauti.billing;

import com.sauti.call.CallRepository;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How many call minutes a workspace may use, and has used, in its current cycle.
 *
 * <ul>
 *   <li>A trial has a one-off allowance (60 minutes) counted from the day the workspace was created;
 *       it never renews.</li>
 *   <li>A paid plan has a monthly allowance (100, 750 or 2,500 minutes) that renews on the day of the
 *       month its subscription renews, for annual plans too. Without a known renewal date, cycles
 *       follow calendar months.</li>
 *   <li>Extra minutes granted by a platform admin are added to either.</li>
 *   <li>Outbound SMS and WhatsApp messages share a second allowance on the same cycle: 50 on a trial,
 *       then 250, 1,000 or 5,000 a month, plus any extra messages an admin grants.</li>
 * </ul>
 *
 * Minutes used are summed from the calls that ended in the cycle, each rounded up to whole minutes,
 * and messages from the billing ledger, so there is no counter to drift or to reset.
 */
@Service
public class WorkspaceUsageService {
    private static final OffsetDateTime FAR_FUTURE = OffsetDateTime.of(9999, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final TenantRepository tenants;
    private final BillingSubscriptionRepository subscriptions;
    private final CallRepository calls;
    private final CommunicationLedgerRepository ledger;
    private final Clock clock;

    @Autowired
    public WorkspaceUsageService(TenantRepository tenants, BillingSubscriptionRepository subscriptions,
                                 CallRepository calls, CommunicationLedgerRepository ledger) {
        this(tenants, subscriptions, calls, ledger, Clock.systemUTC());
    }

    WorkspaceUsageService(TenantRepository tenants, BillingSubscriptionRepository subscriptions,
                          CallRepository calls, CommunicationLedgerRepository ledger, Clock clock) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.calls = calls;
        this.ledger = ledger;
        this.clock = clock;
    }

    /** Outbound messages each cycle for a plan; a plan without a set allowance gets the one its minutes match. */
    public static int planMessages(String plan, int planMinutes) {
        return switch (plan == null ? "" : plan) {
            case "trial" -> 50;
            case "launch" -> 250;
            case "growth" -> 1_000;
            case "scale" -> 5_000;
            default -> planMinutes <= 60 ? 50 : planMinutes <= 100 ? 250 : planMinutes <= 750 ? 1_000 : 5_000;
        };
    }

    /**
     * @param cycleEndsAt null for a trial, whose allowance does not renew
     */
    public record Usage(boolean trial, int planMinutes, int extraMinutes, int usedMinutes,
                        OffsetDateTime cycleStartsAt, OffsetDateTime cycleEndsAt,
                        int planMessages, int extraMessages, int usedMessages) {
        /** Minutes only, with the plan's message allowance and no messages used. */
        public Usage(boolean trial, int planMinutes, int extraMinutes, int usedMinutes,
                     OffsetDateTime cycleStartsAt, OffsetDateTime cycleEndsAt) {
            this(trial, planMinutes, extraMinutes, usedMinutes, cycleStartsAt, cycleEndsAt,
                    WorkspaceUsageService.planMessages(trial ? "trial" : "", planMinutes), 0, 0);
        }

        public int includedMessages() { return planMessages + extraMessages; }
        public int remainingMessages() { return Math.max(0, includedMessages() - usedMessages); }
        public boolean messagesExhausted() { return usedMessages >= includedMessages(); }

        public int includedMinutes() { return planMinutes + extraMinutes; }
        public int remainingMinutes() { return Math.max(0, includedMinutes() - usedMinutes); }
        public boolean exhausted() { return usedMinutes >= includedMinutes(); }
        public int percentUsed() {
            return includedMinutes() <= 0 ? 100 : (int) Math.min(100, Math.round(usedMinutes * 100.0 / includedMinutes()));
        }
    }

    @Transactional(readOnly = true)
    public Usage current(UUID tenantId) {
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        return current(tenant);
    }

    @Transactional(readOnly = true)
    public Usage current(Tenant tenant) {
        var now = OffsetDateTime.now(clock);
        OffsetDateTime start;
        OffsetDateTime end;
        if (tenant.isOnTrial()) {
            start = tenant.getCreatedAt() == null ? OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    : tenant.getCreatedAt();
            end = null;
        } else {
            var anchor = subscriptions.findByTenantId(tenant.getId())
                    .map(BillingSubscription::getRenewsAt)
                    .orElse(null);
            start = cycleStart(anchor, now);
            end = start.plusMonths(1);
            // Keep the anchor's day of month (31 Jan, 28 Feb, 31 Mar…) rather than drifting.
            if (anchor != null) end = monthsFrom(anchor, monthsBetween(anchor, start) + 1);
        }
        var until = end == null ? FAR_FUTURE : end;
        var used = calls.billedMinutesBetween(tenant.getId(), start, until);
        var messages = ledger.messagesBetween(tenant.getId(), start, until);
        return new Usage(tenant.isOnTrial(), tenant.getMonthlyMinutesLimit(), tenant.getExtraMinutes(),
                used == null ? 0 : used.intValue(), start, end,
                planMessages(tenant.getPlan(), tenant.getMonthlyMinutesLimit()), tenant.getExtraMessages(),
                messages == null ? 0 : messages.intValue());
    }

    /** The start of the monthly cycle containing {@code now}, on the anchor's day and time of month. */
    static OffsetDateTime cycleStart(OffsetDateTime anchor, OffsetDateTime now) {
        if (anchor == null) {
            return now.withOffsetSameInstant(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        }
        var months = monthsBetween(anchor, now);
        var start = monthsFrom(anchor, months);
        while (start.isAfter(now)) start = monthsFrom(anchor, --months);
        while (!monthsFrom(anchor, months + 1).isAfter(now)) start = monthsFrom(anchor, ++months);
        return start;
    }

    private static long monthsBetween(OffsetDateTime anchor, OffsetDateTime other) {
        return ChronoUnit.MONTHS.between(anchor, other);
    }

    private static OffsetDateTime monthsFrom(OffsetDateTime anchor, long months) {
        return anchor.plusMonths(months);
    }
}
