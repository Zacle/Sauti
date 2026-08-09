package com.sauti.reliability;

import com.sauti.call.CallRepository;
import com.sauti.call.CallTurnRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SloEvaluationService {
    private final QueueHealthService queues;
    private final CallRepository calls;
    private final CallTurnRepository turns;
    private final int queueWarningMinutes;
    private final int queueCriticalMinutes;
    private final int callWindowMinutes;
    private final int minimumCallSample;
    private final double failureWarningPercent;
    private final double failureCriticalPercent;
    private final int minimumTurnSample;
    private final double responseWarningMs;
    private final double responseCriticalMs;

    public SloEvaluationService(
            QueueHealthService queues,
            CallRepository calls,
            CallTurnRepository turns,
            @Value("${sauti.reliability.slo.queue-warning-minutes:5}") int queueWarningMinutes,
            @Value("${sauti.reliability.slo.queue-critical-minutes:30}") int queueCriticalMinutes,
            @Value("${sauti.reliability.slo.call-window-minutes:15}") int callWindowMinutes,
            @Value("${sauti.reliability.slo.minimum-call-sample:5}") int minimumCallSample,
            @Value("${sauti.reliability.slo.failure-warning-percent:10}") double failureWarningPercent,
            @Value("${sauti.reliability.slo.failure-critical-percent:25}") double failureCriticalPercent,
            @Value("${sauti.reliability.slo.minimum-turn-sample:10}") int minimumTurnSample,
            @Value("${sauti.reliability.slo.response-warning-ms:2500}") double responseWarningMs,
            @Value("${sauti.reliability.slo.response-critical-ms:5000}") double responseCriticalMs) {
        this.queues = queues;
        this.calls = calls;
        this.turns = turns;
        this.queueWarningMinutes = Math.max(1, queueWarningMinutes);
        this.queueCriticalMinutes = Math.max(this.queueWarningMinutes + 1, queueCriticalMinutes);
        this.callWindowMinutes = Math.max(5, callWindowMinutes);
        this.minimumCallSample = Math.max(2, minimumCallSample);
        this.failureWarningPercent = Math.max(0, failureWarningPercent);
        this.failureCriticalPercent = Math.max(this.failureWarningPercent, failureCriticalPercent);
        this.minimumTurnSample = Math.max(2, minimumTurnSample);
        this.responseWarningMs = Math.max(1, responseWarningMs);
        this.responseCriticalMs = Math.max(this.responseWarningMs, responseCriticalMs);
    }

    @Transactional(readOnly = true)
    public List<SloView> snapshot() {
        return snapshot(OffsetDateTime.now(ZoneOffset.UTC));
    }

    List<SloView> snapshot(OffsetDateTime now) {
        var result = new ArrayList<SloView>();
        for (var queue : queues.snapshot()) {
            var ageMinutes = queue.oldestQueuedAt() == null ? 0
                    : Math.max(0, Duration.between(queue.oldestQueuedAt(), now).toMinutes());
            var status = queue.exhausted() > 0 || ageMinutes >= queueCriticalMinutes
                    ? "critical" : ageMinutes >= queueWarningMinutes ? "warning" : "healthy";
            var detail = queue.exhausted() > 0
                    ? "%d exhausted item(s) require operator action".formatted(queue.exhausted())
                    : queue.oldestQueuedAt() == null ? "No active work is waiting"
                    : "Oldest active item has waited %d minute(s)".formatted(ageMinutes);
            result.add(new SloView("queue:" + queue.key(), queue.label() + " delay", status,
                    ageMinutes, "minutes", queueWarningMinutes, queueCriticalMinutes,
                    queue.pending() + queue.retrying(), 0, detail));
        }

        var from = now.minusMinutes(callWindowMinutes);
        var callCount = calls.countCompletedProductionCallsStartedSince(from);
        var failedCount = calls.countFailedProductionCallsStartedSince(from);
        var failurePercent = callCount == 0 ? 0 : failedCount * 100.0 / callCount;
        var failureStatus = callCount < minimumCallSample ? "insufficient_data"
                : thresholdStatus(failurePercent, failureWarningPercent, failureCriticalPercent);
        result.add(new SloView("calls:failure_rate", "Production call failure rate", failureStatus,
                failurePercent, "percent", failureWarningPercent, failureCriticalPercent,
                callCount, callWindowMinutes, callCount < minimumCallSample
                ? "Needs at least %d completed production calls; browser tests and active calls are excluded".formatted(minimumCallSample)
                : "%d of %d completed production calls failed".formatted(failedCount, callCount)));

        var latency = turns.platformResponseLatencySince(from);
        var turnCount = latency == null || latency.getSampleSize() == null ? 0 : latency.getSampleSize();
        var responseMs = latency == null || latency.getAvgResponseMs() == null ? 0 : latency.getAvgResponseMs();
        var responseStatus = turnCount < minimumTurnSample ? "insufficient_data"
                : thresholdStatus(responseMs, responseWarningMs, responseCriticalMs);
        result.add(new SloView("voice:response_latency", "Agent response generation", responseStatus,
                responseMs, "milliseconds", responseWarningMs, responseCriticalMs,
                turnCount, callWindowMinutes, turnCount < minimumTurnSample
                ? "Needs at least %d production voice turns; browser tests are excluded".formatted(minimumTurnSample)
                : "Average stored LLM plus TTS time across %d production turns".formatted(turnCount)));
        return List.copyOf(result);
    }

    private String thresholdStatus(double value, double warning, double critical) {
        if (value >= critical) return "critical";
        if (value >= warning) return "warning";
        return "healthy";
    }

    public record SloView(String key, String label, String status, double actual, String unit,
                          double warningThreshold, double criticalThreshold, long sampleSize,
                          int windowMinutes, String detail) {
        boolean breached() {
            return "warning".equals(status) || "critical".equals(status);
        }
    }
}
