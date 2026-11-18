package com.sauti.audit.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Storage port for the activity log. */
public interface AuditEventRepository {
    AuditEvent save(AuditEvent event);

    List<AuditEvent> findTop50ByWorkspaceIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID workspaceId, OffsetDateTime before);

    List<AuditEvent> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
