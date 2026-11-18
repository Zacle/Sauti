package com.sauti.tenant.application;

import java.util.UUID;

/**
 * Something worth recording happened in a workspace. Published inside the transaction that made
 * the change, so the record is written or rolled back with it. {@code actorLabel} may be left null
 * for a person; the activity log fills in their email.
 */
public record WorkspaceActivity(
        UUID workspaceId,
        UUID actorUserId,
        UUID actorApiKeyId,
        String actorLabel,
        String action,
        String targetType,
        String targetId,
        String summary
) {
    public static WorkspaceActivity byPerson(UUID workspaceId, UUID actorUserId, String action,
                                             String targetType, String targetId, String summary) {
        return new WorkspaceActivity(workspaceId, actorUserId, null, null, action, targetType, targetId, summary);
    }
}
