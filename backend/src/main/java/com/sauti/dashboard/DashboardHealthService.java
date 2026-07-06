package com.sauti.dashboard;

import com.sauti.call.CallRepository;
import com.sauti.call.CallTurnRepository;
import com.sauti.dashboard.DashboardDtos.DashboardHealthResponse;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardHealthService {
    private final CallRepository callRepository;
    private final CallTurnRepository callTurnRepository;
    private final StringRedisTemplate redisTemplate;

    public DashboardHealthService(
            CallRepository callRepository,
            CallTurnRepository callTurnRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.callRepository = callRepository;
        this.callTurnRepository = callTurnRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public DashboardHealthResponse health(UUID tenantId) {
        var totalCalls = callRepository.countByTenantId(tenantId);
        var activeCalls = activeSessionCount(tenantId);
        var turnCount = callTurnRepository.countByTenant_Id(tenantId);
        var interruptedTurns = callTurnRepository.countByTenant_IdAndInterruptedTrue(tenantId);
        var bargeInRate = turnCount == 0 ? 0.0 : (double) interruptedTurns / turnCount;
        var latency = callTurnRepository.avgLatencies(tenantId);
        var avgStt = latency == null || latency.getAvgSttMs() == null ? 0.0 : latency.getAvgSttMs();
        var avgLlm = latency == null || latency.getAvgLlmMs() == null ? 0.0 : latency.getAvgLlmMs();
        var avgTts = latency == null || latency.getAvgTtsMs() == null ? 0.0 : latency.getAvgTtsMs();
        return new DashboardHealthResponse(
                activeCalls,
                totalCalls,
                interruptedTurns,
                bargeInRate,
                avgStt,
                avgLlm,
                avgTts,
                avgStt + avgLlm + avgTts
        );
    }

    private long activeSessionCount(UUID tenantId) {
        try {
            var keys = redisTemplate.keys("call:session:*");
            if (keys != null) {
                return keys.stream()
                        .map(key -> redisTemplate.opsForValue().get(key))
                        .filter(json -> json != null && json.contains("\"tenantId\":\"" + tenantId + "\""))
                        .count();
            }
        } catch (Exception ignored) {
        }
        return callRepository.countByTenantIdAndOutcome(tenantId, "active");
    }
}
