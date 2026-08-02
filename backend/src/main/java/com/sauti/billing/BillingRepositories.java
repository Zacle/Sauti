package com.sauti.billing;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BillingAccountRepository extends JpaRepository<BillingAccount, UUID> {
    Optional<BillingAccount> findByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from BillingAccount account where account.tenantId = :tenantId")
    Optional<BillingAccount> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}

interface CommunicationLedgerRepository extends JpaRepository<CommunicationLedgerEntry, UUID> {
    Optional<CommunicationLedgerEntry> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    List<CommunicationLedgerEntry> findTop50ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<CommunicationLedgerEntry> findAllByTenantId(UUID tenantId);
    List<CommunicationLedgerEntry> findAllByTenantIdAndCreatedAtGreaterThanEqual(UUID tenantId, OffsetDateTime from);
    Optional<CommunicationLedgerEntry> findFirstByTenantIdAndCategoryAndExternalReferenceOrderByCreatedAtDesc(
            UUID tenantId, String category, String externalReference);
    List<CommunicationLedgerEntry> findTop20ByTenantIdAndCategoryOrderByCreatedAtDesc(UUID tenantId, String category);

    @Query("""
            select coalesce(sum(case when entry.direction = 'credit' then -entry.quantity else entry.quantity end), 0)
            from CommunicationLedgerEntry entry
            where entry.tenantId = :tenantId
              and entry.category = :category
              and entry.externalReference = :externalReference
            """)
    java.math.BigDecimal netQuantity(@Param("tenantId") UUID tenantId,
                                     @Param("category") String category,
                                     @Param("externalReference") String externalReference);

    @Query("""
            select coalesce(sum(case when entry.direction = 'credit' then -entry.amount else entry.amount end), 0)
            from CommunicationLedgerEntry entry
            where entry.tenantId = :tenantId
              and entry.category = :category
              and entry.externalReference = :externalReference
              and entry.currency = :currency
            """)
    java.math.BigDecimal netAmount(@Param("tenantId") UUID tenantId,
                                   @Param("category") String category,
                                   @Param("externalReference") String externalReference,
                                   @Param("currency") String currency);
}
