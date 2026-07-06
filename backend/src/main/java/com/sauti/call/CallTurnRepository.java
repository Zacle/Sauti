package com.sauti.call;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallTurnRepository extends JpaRepository<CallTurn, UUID> {
    int countByCall_Id(UUID callId);

    List<CallTurn> findByCall_IdOrderByTurnIndexAsc(UUID callId);

    Optional<CallTurn> findFirstByCall_IdOrderByTurnIndexDesc(UUID callId);

    long countByTenant_Id(UUID tenantId);

    long countByTenant_IdAndInterruptedTrue(UUID tenantId);

    @Query("""
            SELECT COALESCE(AVG(ct.sttLatencyMs), 0) as avgSttMs,
                   COALESCE(AVG(ct.llmLatencyMs), 0) as avgLlmMs,
                   COALESCE(AVG(ct.ttsLatencyMs), 0) as avgTtsMs
            FROM CallTurn ct WHERE ct.tenant.id = :tenantId
            """)
    LatencyStats avgLatencies(@Param("tenantId") UUID tenantId);

    interface LatencyStats {
        Double getAvgSttMs();
        Double getAvgLlmMs();
        Double getAvgTtsMs();
    }
}
