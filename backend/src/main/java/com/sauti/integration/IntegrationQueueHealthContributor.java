package com.sauti.integration;

import com.sauti.reliability.QueueHealthContributor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IntegrationQueueHealthContributor implements QueueHealthContributor {
    private final PostCallJobRepository jobs;
    private final IntegrationDeliveryRepository deliveries;

    public IntegrationQueueHealthContributor(PostCallJobRepository jobs,
                                             IntegrationDeliveryRepository deliveries) {
        this.jobs = jobs;
        this.deliveries = deliveries;
    }

    @Override
    public List<QueueState> snapshot() {
        var activeJobs = List.of("pending_analysis", "ready");
        var oldestJob = jobs.findFirstByStatusInOrderByCreatedAtAsc(activeJobs)
                .map(PostCallJob::getCreatedAt).orElse(null);
        var oldestDelivery = deliveries.findFirstByStatusInOrderByCreatedAtAsc(List.of("pending", "retrying"))
                .map(IntegrationDelivery::getCreatedAt).orElse(null);
        return List.of(
                new QueueState("post_call", "Post-call processing",
                        jobs.countByStatus("pending_analysis") + jobs.countByStatus("ready")
                                - jobs.countByStatusAndAttemptsGreaterThan("ready", 0),
                        jobs.countByStatusAndAttemptsGreaterThan("ready", 0), jobs.countByStatus("failed"), oldestJob),
                new QueueState("integration_delivery", "Integration delivery",
                        deliveries.countByStatus("pending"), deliveries.countByStatus("retrying"),
                        deliveries.countByStatus("failed"), oldestDelivery)
        );
    }
}
