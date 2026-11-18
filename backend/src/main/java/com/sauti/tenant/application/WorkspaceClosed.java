package com.sauti.tenant.application;

import java.util.UUID;

/**
 * A workspace was closed by its Owner. Published inside the closing transaction. Modules that hold
 * running services for the workspace (agents, phone numbers, billing) react to stop them.
 */
public record WorkspaceClosed(UUID workspaceId) {
}
