package com.sauti.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage port for refresh tokens and the sessions (families) they belong to. */
public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByFamilyIdAndRevokedAtIsNull(UUID familyId);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
