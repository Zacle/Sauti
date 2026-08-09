package com.sauti.webhook;

import com.sauti.reliability.QueueHealthContributor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WebhookQueueHealthContributor implements QueueHealthContributor {
    private final WebhookDeliveryRepository deliveries;

    public WebhookQueueHealthContributor(WebhookDeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    public List<QueueState> snapshot() {
        var oldest = deliveries.findFirstBySuccessFalseAndNextAttemptAtIsNotNullOrderByCreatedAtAsc()
                .map(WebhookDelivery::getCreatedAt).orElse(null);
        return List.of(new QueueState("webhook_delivery", "Webhook delivery",
                deliveries.countBySuccessFalseAndAttemptCount(0),
                deliveries.countBySuccessFalseAndAttemptCountGreaterThanAndNextAttemptAtIsNotNull(0),
                deliveries.countBySuccessFalseAndNextAttemptAtIsNull(), oldest));
    }
}
