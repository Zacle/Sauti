package com.sauti.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, UUID> {
    List<Agent> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Agent> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Agent> findByTwilioPhoneNumber(String twilioPhoneNumber);

    Optional<Agent> findByWebVoicePublicId(String webVoicePublicId);

    Optional<Agent> findByWhatsappPhoneNumberId(String whatsappPhoneNumberId);
}
