package com.sauti.call;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallRepository extends JpaRepository<Call, UUID> {
    boolean existsByAgent_Id(UUID agentId);

    List<Call> findAllByTenantIdOrderByStartedAtDesc(UUID tenantId);

    Optional<Call> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Call> findByTwilioCallSid(String twilioCallSid);

    Optional<Call> findByTwilioCallSidAndTenantId(String twilioCallSid, UUID tenantId);

    Optional<Call> findFirstByAgent_IdAndDirectionAndCallerNumberAndOutcomeOrderByStartedAtDesc(
            UUID agentId,
            String direction,
            String callerNumber,
            String outcome
    );

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndOutcome(UUID tenantId, String outcome);

    @Query("select coalesce(avg(c.durationSeconds), 0) from Call c where c.tenant.id = :tenantId and c.durationSeconds is not null")
    double averageDurationSeconds(@Param("tenantId") UUID tenantId);

    // ---- date-windowed variants ----

    long countByTenantIdAndStartedAtBetween(UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    long countByTenantIdAndOutcomeAndStartedAtBetween(UUID tenantId, String outcome, OffsetDateTime from, OffsetDateTime to);

    @Query("""
            select coalesce(avg(c.durationSeconds), 0) from Call c
            where c.tenant.id = :tenantId
              and c.startedAt between :from and :to
              and c.durationSeconds is not null
            """)
    double avgDurationBetween(@Param("tenantId") UUID tenantId,
                              @Param("from") OffsetDateTime from,
                              @Param("to") OffsetDateTime to);

    // ---- breakdown queries ----

    @Query("""
            select c.languageDetected as language, count(c) as callCount
            from Call c
            where c.tenant.id = :tenantId and c.languageDetected is not null
            group by c.languageDetected
            order by count(c) desc
            """)
    List<LanguageStat> languageDistribution(@Param("tenantId") UUID tenantId);

    @Query("""
            select c.agent.id as agentId, c.agent.name as agentName,
                   count(c) as totalCalls,
                   coalesce(avg(c.durationSeconds), 0) as avgDuration
            from Call c
            where c.tenant.id = :tenantId
            group by c.agent.id, c.agent.name
            order by count(c) desc
            """)
    List<AgentStat> agentSummary(@Param("tenantId") UUID tenantId);

    @Query("""
            select c.agent.id as agentId,
                   count(c) as totalCalls,
                   sum(case when c.outcome = 'booking_made' then 1 else 0 end) as bookingCalls
            from Call c
            where c.tenant.id = :tenantId
            group by c.agent.id
            """)
    List<AgentOutcomeStat> agentOutcomeStats(@Param("tenantId") UUID tenantId);

    List<Call> findAllByTenantIdAndStartedAtAfterOrderByStartedAtAsc(UUID tenantId, OffsetDateTime since);

    interface LanguageStat {
        String getLanguage();
        Long getCallCount();
    }

    interface AgentStat {
        UUID getAgentId();
        String getAgentName();
        Long getTotalCalls();
        Double getAvgDuration();
    }

    interface AgentOutcomeStat {
        UUID getAgentId();
        Long getTotalCalls();
        Long getBookingCalls();
    }
}
