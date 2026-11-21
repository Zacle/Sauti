package com.sauti.business.application;

import java.util.UUID;

/** The facts every agent in this workspace may tell callers changed, so live assistants need refreshing. */
public record BusinessProfileChanged(UUID workspaceId) {
}
