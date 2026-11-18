package com.sauti.auth.domain;

import java.util.Optional;
import java.util.UUID;

/** Storage port for platform administrators. */
public interface PlatformAdminRepository {
    PlatformAdmin save(PlatformAdmin admin);

    Optional<PlatformAdmin> findById(UUID userId);

    boolean existsById(UUID userId);

    void deleteById(UUID userId);
}
