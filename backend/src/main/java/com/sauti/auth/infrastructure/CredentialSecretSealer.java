package com.sauti.auth.infrastructure;

import com.sauti.auth.application.port.SecretSealer;
import com.sauti.tool.CredentialEncryption;
import org.springframework.stereotype.Component;

/** Reuses the platform credential encryption, so there is a single key to manage and rotate. */
@Component
public class CredentialSecretSealer implements SecretSealer {
    private final CredentialEncryption encryption;

    public CredentialSecretSealer(CredentialEncryption encryption) {
        this.encryption = encryption;
    }

    @Override
    public String seal(String plaintext) {
        return encryption.encrypt(plaintext);
    }

    @Override
    public String open(String sealed) {
        return encryption.decrypt(sealed);
    }
}
