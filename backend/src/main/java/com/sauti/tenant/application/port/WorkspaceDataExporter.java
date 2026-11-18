package com.sauti.tenant.application.port;

import java.util.UUID;

/**
 * One module's part of a workspace data export. Each module that stores customer data implements
 * this for its own data, so the export grows with the system without the workspace module knowing
 * about agents, calls or bookings.
 */
public interface WorkspaceDataExporter {
    /** The key this data appears under in the export, such as "calls". */
    String section();

    /** Plain values only (maps, lists, strings, numbers), never entities. */
    Object export(UUID workspaceId);
}
