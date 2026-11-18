package com.sauti.auth.application.port;

/** Google sign-in. Returns only profiles whose email Google has verified. */
public interface GoogleIdentityProvider {
    boolean isConfigured();

    String authorizationUrl(String businessName, String countryCode, String returnPath);

    GoogleProfile verifyIdToken(String idToken);

    GoogleAuthResult exchangeCode(String code, String state);

    /** {@code subject} is Google's permanent account id; email can change. */
    record GoogleProfile(String subject, String email, String name) {
    }

    record GoogleAuthContext(String businessName, String countryCode, String returnPath) {
    }

    record GoogleAuthResult(GoogleProfile profile, GoogleAuthContext context) {
    }
}
