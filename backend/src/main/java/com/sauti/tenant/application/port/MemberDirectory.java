package com.sauti.tenant.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks up the people behind memberships. The workspace module owns this interface and the
 * identity module implements it, so workspaces never depend on how accounts are stored.
 */
public interface MemberDirectory {
    Map<UUID, MemberProfile> profiles(Collection<UUID> userIds);

    Optional<UUID> findUserIdByEmail(String email);

    record MemberProfile(UUID userId, String email) {
    }
}
