package com.sauti.tenant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage port for memberships. Implemented by Spring Data in the infrastructure layer. */
public interface MembershipRepository {
    Membership save(Membership membership);

    void delete(Membership membership);

    Optional<Membership> findByIdTenantIdAndIdUserId(UUID workspaceId, UUID userId);

    List<Membership> findAllByIdUserId(UUID userId);

    List<Membership> findAllByIdTenantIdOrderByCreatedAtAsc(UUID workspaceId);

    long countByIdTenantIdAndRole(UUID workspaceId, WorkspaceRole role);

    default Optional<Membership> find(UUID workspaceId, UUID userId) {
        return findByIdTenantIdAndIdUserId(workspaceId, userId);
    }

    default long ownerCount(UUID workspaceId) {
        return countByIdTenantIdAndRole(workspaceId, WorkspaceRole.OWNER);
    }
}
