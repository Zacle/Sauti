package com.sauti.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentTemplateRepository extends JpaRepository<AgentTemplate, UUID> {
    @Query("""
            select template from AgentTemplate template
            where (template.tenant is null and template.published = true)
               or template.tenant.id = :tenantId
            order by template.category asc, template.name asc
            """)
    List<AgentTemplate> findAllAccessible(@Param("tenantId") UUID tenantId);

    @Query("""
            select template from AgentTemplate template
            where template.id = :id
              and ((template.tenant is null and template.published = true)
                   or template.tenant.id = :tenantId)
            """)
    Optional<AgentTemplate> findAccessibleById(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<AgentTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<AgentTemplate> findByTenantIsNullAndName(String name);

    List<AgentTemplate> findAllByTenantIsNull();

    List<AgentTemplate> findAllByTenantIsNullAndPublishedTrueOrderByCategoryAscNameAsc();

    Optional<AgentTemplate> findByIdAndTenantIsNullAndPublishedTrue(UUID id);
}
