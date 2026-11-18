package com.sauti.tenant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage port for workspace API keys. */
public interface ApiKeyRepository {
    ApiKey save(ApiKey key);

    Optional<ApiKey> findByKeyHash(String keyHash);

    Optional<ApiKey> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<ApiKey> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    long countByWorkspaceIdAndRevokedAtIsNull(UUID workspaceId);
}
