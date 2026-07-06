package com.sauti.dashboard;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record DashboardEvent(
            String type,
            UUID tenantId,
            OffsetDateTime occurredAt,
            Map<String, Object> payload
    ) {
        public static DashboardEvent of(String type, UUID tenantId, Map<String, Object> payload) {
            return new DashboardEvent(type, tenantId, OffsetDateTime.now(), payload);
        }
    }

    public record DashboardHealthResponse(
            long activeCalls,
            long totalCalls,
            long interruptedTurns,
            double bargeInRate,
            double avgSttLatencyMs,
            double avgLlmLatencyMs,
            double avgTtsLatencyMs,
            double avgRoundTripLatencyMs
    ) {
    }
}
