package com.sauti.billing;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingPaymentEmailWorker {
    private final BillingPaymentNotificationRepository notifications;
    private final BillingPaymentEmailService emailService;

    public BillingPaymentEmailWorker(BillingPaymentNotificationRepository notifications,
                                     BillingPaymentEmailService emailService) {
        this.notifications = notifications;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelayString = "${sauti.billing.payment-email-worker-delay-ms:5000}")
    @Transactional
    public void processDue() {
        var due = notifications.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                List.of("pending", "retrying"), OffsetDateTime.now());
        for (var notification : due) {
            try {
                emailService.send(notification);
                notification.sent();
            } catch (RuntimeException exception) {
                notification.retry(exception.getMessage());
            }
            notifications.save(notification);
        }
    }
}
