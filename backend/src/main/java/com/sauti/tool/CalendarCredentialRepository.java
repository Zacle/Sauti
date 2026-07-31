package com.sauti.tool;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CalendarCredentialRepository extends JpaRepository<CalendarCredential, UUID> {
    Optional<CalendarCredential> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from CalendarCredential credential "
            + "where credential.id = :id and credential.tenant.id = :tenantId")
    Optional<CalendarCredential> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    List<CalendarCredential> findAllByTenant_IdAndProviderOrderByCreatedAtDesc(UUID tenantId, String provider);
}
