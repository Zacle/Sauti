package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingPaymentEmailWorkerTest {
    @Test
    void marksNotificationSentOnlyAfterEmailDelivery() {
        var repository = mock(BillingPaymentNotificationRepository.class);
        var email = mock(BillingPaymentEmailService.class);
        var notification = notification();
        when(repository.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                anyList(), any(OffsetDateTime.class))).thenReturn(List.of(notification));

        new BillingPaymentEmailWorker(repository, email).processDue();

        verify(email).send(notification);
        verify(repository).save(notification);
        assertThat(notification.getStatus()).isEqualTo("sent");
    }

    @Test
    void retriesWithoutMarkingSentWhenDeliveryFails() {
        var repository = mock(BillingPaymentNotificationRepository.class);
        var email = mock(BillingPaymentEmailService.class);
        var notification = notification();
        when(repository.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                anyList(), any(OffsetDateTime.class))).thenReturn(List.of(notification));
        doThrow(new IllegalStateException("SMTP unavailable")).when(email).send(notification);

        new BillingPaymentEmailWorker(repository, email).processDue();

        assertThat(notification.getStatus()).isEqualTo("retrying");
        assertThat(notification.getAttempts()).isEqualTo(1);
    }

    private BillingPaymentNotification notification() {
        return new BillingPaymentNotification(UUID.randomUUID(), "whop", "pay_1",
                "owner@example.com", "Clinic", "Growth plan (monthly)",
                new BigDecimal("149.00"), "USD", OffsetDateTime.now(), "4242", true);
    }
}
