package com.sauti.auth.domain;

import java.util.Optional;

/** Storage port for linked external sign-in identities. */
public interface UserIdentityRepository {
    UserIdentity save(UserIdentity identity);

    Optional<UserIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    void deleteAllByUserId(java.util.UUID userId);
}
