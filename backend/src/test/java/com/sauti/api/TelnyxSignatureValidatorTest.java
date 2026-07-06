package com.sauti.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TelnyxSignatureValidatorTest {

    @Test
    void validatesAnEd25519WebhookSignature() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        var timestamp = Long.toString(Instant.now().getEpochSecond());
        var payload = "{\"data\":{\"event_type\":\"call.initiated\"}}";
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(keys.getPrivate());
        signature.update((timestamp + "|" + payload).getBytes(StandardCharsets.UTF_8));
        var encodedSignature = Base64.getEncoder().encodeToString(signature.sign());
        var validator = new TelnyxSignatureValidator(publicKey, true, 300);

        assertThat(validator.isValid(payload, timestamp, encodedSignature)).isTrue();
        assertThat(validator.isValid(payload + " ", timestamp, encodedSignature)).isFalse();
    }

    @Test
    void rejectsStaleAndMissingSignatures() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        var validator = new TelnyxSignatureValidator(publicKey, true, 60);

        assertThat(validator.isValid("{}", Long.toString(Instant.now().minusSeconds(61).getEpochSecond()), "invalid"))
                .isFalse();
        assertThat(validator.isValid("{}", null, null)).isFalse();
    }

    @Test
    void allowsLocalTestingWhenValidationIsExplicitlyDisabled() {
        var validator = new TelnyxSignatureValidator("", false, 300);

        assertThat(validator.isValid("{}", null, null)).isTrue();
    }
}
