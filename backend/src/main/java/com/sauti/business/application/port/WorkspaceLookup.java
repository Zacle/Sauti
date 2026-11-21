package com.sauti.business.application.port;

import java.util.UUID;

/** The few workspace facts the business profile needs: its name and the country phone numbers default to. */
public interface WorkspaceLookup {
    Workspace require(UUID workspaceId);

    record Workspace(String name, String countryCode) {
    }
}
