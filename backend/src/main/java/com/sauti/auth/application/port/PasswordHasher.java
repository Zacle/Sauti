package com.sauti.auth.application.port;

/** One-way password hashing. */
public interface PasswordHasher {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);

    /**
     * Spends the same time as a real check when there is no account or no password, so response
     * timing does not reveal which emails have accounts.
     */
    void spendEquivalentTime(String rawPassword);
}
