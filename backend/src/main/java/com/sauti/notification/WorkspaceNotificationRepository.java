package com.sauti.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceNotificationRepository extends JpaRepository<WorkspaceNotification, UUID> {
    List<WorkspaceNotification> findTop50ByTenant_IdOrderByCreatedAtDesc(UUID tenantId);
    Optional<WorkspaceNotification> findByIdAndTenant_Id(UUID id, UUID tenantId);
    long countByTenant_IdAndReadAtIsNull(UUID tenantId);
    List<WorkspaceNotification> findAllByTenant_IdAndReadAtIsNull(UUID tenantId);
}
