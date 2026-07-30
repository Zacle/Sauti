package com.sauti.call;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedVoiceAgentBindingRepository extends JpaRepository<ManagedVoiceAgentBinding, UUID> {
    Optional<ManagedVoiceAgentBinding> findByTenantIdAndAgentIdAndProviderAndLanguage(
            UUID tenantId,
            UUID agentId,
            String provider,
            String language
    );
}
