package com.sauti.tenant;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenant from Tenant tenant where tenant.id = :id")
    Optional<Tenant> findByIdForBillingUpdate(@Param("id") UUID id);

    @Query("""
            select tenant from Tenant tenant
            where :query = ''
               or lower(tenant.businessName) like lower(concat('%', :query, '%'))
               or lower(tenant.email) like lower(concat('%', :query, '%'))
               or lower(tenant.countryCode) like lower(concat('%', :query, '%'))
            order by tenant.createdAt desc
            """)
    Page<Tenant> searchForPlatformAdmin(@Param("query") String query, Pageable pageable);
}
