package com.sauti.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    List<KnowledgeDocument> findAllByTenantIdAndAgentIdOrderByCreatedAtDesc(UUID tenantId, UUID agentId);
    Optional<KnowledgeDocument> findByIdAndTenantIdAndAgentId(UUID id, UUID tenantId, UUID agentId);
    long countByTenantIdAndAgentId(UUID tenantId, UUID agentId);
}
