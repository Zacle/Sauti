package com.sauti.call;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallRecordJobRepository extends JpaRepository<CallRecordJob, UUID> {
    @Query("""
            select j.callId from CallRecordJob j
            where j.stage in ('transcript', 'analysis') and j.nextAttemptAt <= :now
            order by j.nextAttemptAt
            """)
    List<UUID> findDue(@Param("now") OffsetDateTime now, Pageable page);

    List<CallRecordJob> findAllByTenantIdAndStageIn(UUID tenantId, Collection<String> stages);

    boolean existsByCallIdAndStageIn(UUID callId, Collection<String> stages);
}
