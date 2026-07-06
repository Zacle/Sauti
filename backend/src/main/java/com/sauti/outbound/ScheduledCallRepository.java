package com.sauti.outbound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledCallRepository extends JpaRepository<ScheduledCall, UUID> {
    boolean existsByAgent_Id(UUID agentId);

    List<ScheduledCall> findTop25ByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(String status, OffsetDateTime dueAt);
}
