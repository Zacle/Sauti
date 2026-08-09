package com.sauti.api;

import com.sauti.reliability.ReliabilityMonitoringService;
import com.sauti.reliability.ReliabilityMonitoringService.IncidentView;
import com.sauti.reliability.QueueHealthContributor.QueueState;
import com.sauti.reliability.QueueHealthService;
import com.sauti.reliability.SloEvaluationService;
import com.sauti.reliability.SloEvaluationService.SloView;
import com.sauti.reliability.ReliabilityDrillService;
import com.sauti.reliability.ReliabilityDrillService.DrillView;
import com.sauti.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reliability")
public class AdminReliabilityController {
    private final ReliabilityMonitoringService monitoring;
    private final QueueHealthService queues;
    private final SloEvaluationService slos;
    private final ReliabilityDrillService drills;

    public AdminReliabilityController(ReliabilityMonitoringService monitoring, QueueHealthService queues,
                                      SloEvaluationService slos, ReliabilityDrillService drills) {
        this.monitoring = monitoring;
        this.queues = queues;
        this.slos = slos;
        this.drills = drills;
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

    @GetMapping("/drills")
    List<DrillView> drills() {
        return drills.recent();
    }

    @PostMapping("/drills")
    DrillView startDrill(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestBody StartDrillRequest request) {
        return drills.start(user.email(), request.confirmation());
    }

    @PostMapping("/drills/{drillId}/acknowledge")
    DrillView acknowledgeDrill(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable UUID drillId) {
        return drills.acknowledge(drillId, user.email());
    }

    @PostMapping("/drills/{drillId}/resolve")
    DrillView resolveDrill(@AuthenticationPrincipal AuthenticatedUser user,
                           @PathVariable UUID drillId) {
        return drills.resolve(drillId, user.email());
    }

    record StartDrillRequest(String confirmation) { }
}
