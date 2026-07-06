package com.sauti.tool;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarCredentialRepository extends JpaRepository<CalendarCredential, UUID> {
    Optional<CalendarCredential> findByIdAndTenant_Id(UUID id, UUID tenantId);

    List<CalendarCredential> findAllByTenant_IdAndProviderOrderByCreatedAtDesc(UUID tenantId, String provider);
}
