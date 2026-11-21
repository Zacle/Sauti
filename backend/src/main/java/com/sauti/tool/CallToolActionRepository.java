package com.sauti.tool;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Storage for actions taken during calls. */
public interface CallToolActionRepository extends JpaRepository<CallToolAction, UUID> {
    Optional<CallToolAction> findByCallIdAndInvocationId(UUID callId, String invocationId);

    List<CallToolAction> findByTenantIdAndCallIdOrderByCreatedAtAsc(UUID tenantId, UUID callId);
}
