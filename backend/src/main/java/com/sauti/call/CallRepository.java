package com.sauti.call;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallRepository extends JpaRepository<Call, UUID> {
    boolean existsByAgent_Id(UUID agentId);

    boolean existsByTenantIdAndDirectionAndEndedAtIsNotNull(UUID tenantId, String direction);

    List<Call> findAllByTenantIdOrderByStartedAtDesc(UUID tenantId);

    Optional<Call> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Call> findByTwilioCallSid(String twilioCallSid);

    Optional<Call> findByTwilioCallSidAndTenantId(String twilioCallSid, UUID tenantId);

    List<Call> findTop25ByRecordingUrlIsNullAndRecordingSidStartingWithAndEndedAtIsNotNullOrderByEndedAtAsc(
            String recordingSidPrefix
    );

    Optional<Call> findFirstByAgent_IdAndDirectionAndCallerNumberAndOutcomeOrderByStartedAtDesc(
            UUID agentId,
            String direction,
            String callerNumber,
            String outcome
    );

    @Modifying
    @Query(
            value = """
                    update calls
                    set booking_id = null
                    where booking_id = :bookingId
                      and tenant_id = :tenantId
                    """,
            nativeQuery = true
    )
    int clearLegacyBookingReference(
            @Param("tenantId") UUID tenantId,
            @Param("bookingId") UUID bookingId
    );

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndCallerNumber(UUID tenantId, String callerNumber);

    List<Call> findTop25ByTenantIdAndCallerNumberOrderByStartedAtDesc(UUID tenantId, String callerNumber);

    @Query("""
            select c.tenant.id as tenantId,
                   c.tenant.businessName as businessName,
                   c.callerNumber as phone,
                   count(c) as callCount,
                   max(c.startedAt) as lastContactAt
            from Call c
            where c.callerNumber is not null and c.callerNumber <> ''
            group by c.tenant.id, c.tenant.businessName, c.callerNumber
            order by max(c.startedAt) desc
            """)
    List<PlatformCustomerSummary> findPlatformCustomerSummaries();

    long countByTenantIdAndOutcome(UUID tenantId, String outcome);

    @Query("select count(distinct c.callerNumber) from Call c where c.callerNumber is not null and c.callerNumber <> ''")
    long countDistinctCustomerNumbers();

    @Query("select count(distinct c.callerNumber) from Call c where c.tenant.id = :tenantId and c.callerNumber is not null and c.callerNumber <> ''")
    long countDistinctCustomerNumbersByTenantId(@Param("tenantId") UUID tenantId);

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

    List<Call> findAllByTenantIdAndStartedAtBetweenOrderByStartedAtAsc(
            UUID tenantId,
            OffsetDateTime from,
            OffsetDateTime to
    );

    List<Call> findAllByStartedAtBetweenOrderByStartedAtAsc(OffsetDateTime from, OffsetDateTime to);

    List<Call> findAllByTenantIdAndAgent_IdAndStartedAtBetweenOrderByStartedAtAsc(
            UUID tenantId,
            UUID agentId,
            OffsetDateTime from,
            OffsetDateTime to
    );

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

    interface PlatformCustomerSummary {
        UUID getTenantId();
        String getBusinessName();
        String getPhone();
        Long getCallCount();
        OffsetDateTime getLastContactAt();
    }
}
