package com.sauti.billing;

import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingUsageAlertWorker {
    private static final Logger log = LoggerFactory.getLogger(BillingUsageAlertWorker.class);

    private final BillingUsageAlertService service;
    private final BillingUsageAlertRepository alerts;
    private final BillingUsageAlertEmailService email;

    public BillingUsageAlertWorker(BillingUsageAlertService service, BillingUsageAlertRepository alerts,
                                   BillingUsageAlertEmailService email) {
        this.service = service;
        this.alerts = alerts;
        this.email = email;
    }

    /** Each workspace is checked in its own transaction, so one failure doesn't hold up the others. */
    @Scheduled(fixedDelayString = "${sauti.billing.usage-alert-scan-delay-ms:300000}",
            initialDelayString = "${sauti.billing.usage-alert-scan-initial-delay-ms:60000}")
    public void scan() {
        for (var tenantId : service.recentlyActiveWorkspaces()) {
            try {
                service.check(tenantId);
            } catch (RuntimeException exception) {
                log.warn("Usage alert check failed for workspace {}: {}", tenantId, exception.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${sauti.billing.usage-alert-email-delay-ms:15000}")
    @Transactional
    public void sendDue() {
        var due = alerts.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                List.of("pending", "retrying"), OffsetDateTime.now());
        for (var alert : due) {
            try {
                email.send(alert);
                alert.sent();
            } catch (RuntimeException exception) {
                alert.retry(exception.getMessage());
            }
            alerts.save(alert);
        }
    }
}
