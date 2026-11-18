package com.sauti.auth.application.port;

/** Reversible encryption for secrets Sauti must read back, such as authenticator keys. */
public interface SecretSealer {
    String seal(String plaintext);

    String open(String sealed);
}
