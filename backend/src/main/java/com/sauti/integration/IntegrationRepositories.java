package com.sauti.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, UUID> {
    List<IntegrationConnection> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<IntegrationConnection> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<IntegrationConnection> findFirstByTenantIdAndProviderOrderByCreatedAtDesc(UUID tenantId, String provider);
}

interface AgentIntegrationRepository extends JpaRepository<AgentIntegration, UUID> {
    List<AgentIntegration> findAllByTenantIdAndAgentIdOrderByProvider(UUID tenantId, UUID agentId);
    List<AgentIntegration> findAllByTenantIdAndAgentIdAndEnabledTrue(UUID tenantId, UUID agentId);
    Optional<AgentIntegration> findByTenantIdAndAgentIdAndProvider(UUID tenantId, UUID agentId, String provider);
    long countByConnectionId(UUID connectionId);
}

interface PostCallJobRepository extends JpaRepository<PostCallJob, UUID> {
    Optional<PostCallJob> findByCallId(UUID callId);
    List<PostCallJob> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            List<String> statuses, OffsetDateTime now);
}

interface IntegrationDeliveryRepository extends JpaRepository<IntegrationDelivery, UUID> {
    List<IntegrationDelivery> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<IntegrationDelivery> findFirstByAgentIntegrationIdOrderByCreatedAtDesc(UUID bindingId);
    boolean existsByAgentIntegrationIdAndCallId(UUID bindingId, UUID callId);
    List<IntegrationDelivery> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            List<String> statuses, OffsetDateTime now);
}

interface MpesaPaymentRequestRepository extends JpaRepository<MpesaPaymentRequest, UUID> {
    Optional<MpesaPaymentRequest> findByCheckoutRequestIdAndConnectionId(String checkoutRequestId, UUID connectionId);
}
