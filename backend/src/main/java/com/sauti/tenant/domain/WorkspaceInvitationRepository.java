package com.sauti.tenant.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage port for workspace invitations. Implemented by Spring Data in the infrastructure layer. */
public interface WorkspaceInvitationRepository {
    WorkspaceInvitation save(WorkspaceInvitation invitation);

    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<WorkspaceInvitation> findAllByWorkspaceIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

    long countByWorkspaceIdAndCreatedAtAfter(UUID workspaceId, OffsetDateTime since);

    long countByWorkspaceIdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(UUID workspaceId, OffsetDateTime now);

    default List<WorkspaceInvitation> openInvitations(UUID workspaceId) {
        return findAllByWorkspaceIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(workspaceId);
    }

    default long pendingCount(UUID workspaceId, OffsetDateTime now) {
        return countByWorkspaceIdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(workspaceId, now);
    }
}
