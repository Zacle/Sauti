package com.sauti.provisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PilotProvisioningPolicyRepository extends JpaRepository<PilotProvisioningPolicy, UUID> {
    Optional<PilotProvisioningPolicy> findByTenantId(UUID tenantId);
}
