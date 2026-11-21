package com.sauti.business.domain;

import java.util.Optional;
import java.util.UUID;

/** Storage port for business profiles; one per workspace. */
public interface BusinessProfileRepository {
    Optional<BusinessProfile> findById(UUID workspaceId);

    BusinessProfile save(BusinessProfile profile);
}
