package com.sauti.tenant.application;

import java.util.UUID;

/**
 * Published inside the creating transaction. Listeners that must hold for every workspace, such
 * as the deny-by-default spending policy, run synchronously so they commit or roll back with it.
 */
public record WorkspaceCreated(UUID workspaceId, UUID ownerUserId) {
}
