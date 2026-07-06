package com.sauti.webhook;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findTop50BySuccessFalseAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(OffsetDateTime dueAt);
}
