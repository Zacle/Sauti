package com.sauti.tool;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * How long a named service takes, from the workspace's business profile. The business module
 * provides it, so booking tools don't depend on how services are stored.
 */
public interface ServiceDurations {
    ServiceDurations NONE = (workspaceId, service) -> OptionalInt.empty();

    /** The configured minutes for the service the caller asked for, when it is known and unambiguous. */
    OptionalInt minutesFor(UUID workspaceId, String service);
}
