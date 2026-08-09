package com.sauti.api;

import com.sauti.reliability.ReliabilityMonitoringService;
import com.sauti.reliability.ReliabilityMonitoringService.IncidentView;
import com.sauti.reliability.QueueHealthContributor.QueueState;
import com.sauti.reliability.QueueHealthService;
import com.sauti.reliability.SloEvaluationService;
import com.sauti.reliability.SloEvaluationService.SloView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reliability")
public class AdminReliabilityController {
    private final ReliabilityMonitoringService monitoring;
    private final QueueHealthService queues;
    private final SloEvaluationService slos;

    public AdminReliabilityController(ReliabilityMonitoringService monitoring, QueueHealthService queues,
                                      SloEvaluationService slos) {
        this.monitoring = monitoring;
        this.queues = queues;
        this.slos = slos;
    }

    @GetMapping("/incidents")
    List<IncidentView> incidents() {
        return monitoring.recentIncidents();
    }

    @GetMapping("/queues")
    List<QueueState> queues() {
        return queues.snapshot();
    }

    @GetMapping("/slos")
    List<SloView> slos() {
        return slos.snapshot();
    }
}
