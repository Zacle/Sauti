package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CredentialEncryptionTest {
    @Test
    void acceptsAStandardSixtyFourCharacterHexKey() {
        var encryption = new CredentialEncryption(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );

        var ciphertext = encryption.encrypt("calendar-secret");

        assertThat(ciphertext).isNotEqualTo("calendar-secret");
        assertThat(encryption.decrypt(ciphertext)).isEqualTo("calendar-secret");
    }
}
