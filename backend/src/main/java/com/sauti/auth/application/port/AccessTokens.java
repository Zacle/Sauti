package com.sauti.auth.application.port;

import java.util.UUID;

/** Short-lived, signed access tokens. They name the person and the session, never a workspace. */
public interface AccessTokens {
    IssuedAccessToken issue(AccessClaims claims);

    /** Throws when the token is malformed, expired, or not signed by Sauti. */
    AccessClaims verify(String token);

    record AccessClaims(UUID userId, String email, boolean platformAdmin, UUID sessionId) {
    }

    /** A five-minute token proving the password step of an admin sign-in, so the code step can follow. */
    String issueAdminChallenge(UUID userId);

    /** Returns the user id, or throws when the challenge is invalid or expired. */
    UUID verifyAdminChallenge(String challenge);

    record IssuedAccessToken(String value, long expiresInSeconds) {
    }
}
