package com.sauti.api;

import com.sauti.reliability.ReliabilityMonitoringService;
import com.sauti.reliability.ReliabilityMonitoringService.IncidentView;
import com.sauti.reliability.QueueHealthContributor.QueueState;
import com.sauti.reliability.QueueHealthService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reliability")
public class AdminReliabilityController {
    private final ReliabilityMonitoringService monitoring;
    private final QueueHealthService queues;

    public AdminReliabilityController(ReliabilityMonitoringService monitoring, QueueHealthService queues) {
        this.monitoring = monitoring;
        this.queues = queues;
    }

    @GetMapping("/incidents")
    List<IncidentView> incidents() {
        return monitoring.recentIncidents();
    }

    @GetMapping("/queues")
    List<QueueState> queues() {
        return queues.snapshot();
    }
}
