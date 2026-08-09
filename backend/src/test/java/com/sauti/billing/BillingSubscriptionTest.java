package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingSubscriptionTest {
    @Test
    void rejectsOlderAndUndatedUpdatesAfterProviderTimestampIsKnown() {
        var subscription = new BillingSubscription(UUID.randomUUID(), "whop", "mem_1");
        var updatedAt = OffsetDateTime.parse("2026-08-09T12:00:00Z");
        subscription.synchronize("user_1", "mem_1", "prod_1", "plan_1",
                "launch", "monthly", "active", true, null, null, null,
                updatedAt, "", "", "");

        assertThat(subscription.isNewerThan(updatedAt.minusMinutes(1))).isFalse();
        assertThat(subscription.isNewerThan(null)).isFalse();
        assertThat(subscription.isNewerThan(updatedAt)).isTrue();
        assertThat(subscription.isNewerThan(updatedAt.plusMinutes(1))).isTrue();
    }
}
