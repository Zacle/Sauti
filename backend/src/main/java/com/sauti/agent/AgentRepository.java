package com.sauti.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AgentRepository extends JpaRepository<Agent, UUID> {
    long countByTenantId(UUID tenantId);

    List<Agent> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Agent> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select agent from Agent agent where agent.id = :id and agent.tenant.id = :tenantId")
    Optional<Agent> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    Optional<Agent> findByTwilioPhoneNumber(String twilioPhoneNumber);

    Optional<Agent> findByWebVoicePublicId(String webVoicePublicId);

    Optional<Agent> findByWhatsappPhoneNumberId(String whatsappPhoneNumberId);

    List<Agent> findAllByTwilioPhoneNumberIsNotNull();
}
