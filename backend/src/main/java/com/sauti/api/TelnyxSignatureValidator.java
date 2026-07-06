package com.sauti.api;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelnyxSignatureValidator {
    private static final byte[] ED25519_X509_PREFIX = java.util.HexFormat.of().parseHex("302a300506032b6570032100");
    private final String publicKey;
    private final boolean validationEnabled;
    private final long toleranceSeconds;

    public TelnyxSignatureValidator(
            @Value("${sauti.telnyx.public-key:}") String publicKey,
            @Value("${sauti.telnyx.validate-signature:true}") boolean validationEnabled,
            @Value("${sauti.telnyx.webhook-tolerance-seconds:300}") long toleranceSeconds
    ) {
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.validationEnabled = validationEnabled;
        this.toleranceSeconds = toleranceSeconds;
    }

    public boolean isValid(String payload, String timestamp, String signature) {
        if (!validationEnabled) return true;
        if (publicKey.isBlank() || timestamp == null || signature == null) return false;
        try {
            long sentAt = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - sentAt) > toleranceSeconds) return false;
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes())));
            verifier.update((timestamp + "|" + (payload == null ? "" : payload)).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception exception) {
            return false;
        }
    }

    private byte[] keyBytes() {
        var normalized = publicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        var decoded = Base64.getDecoder().decode(normalized);
        if (decoded.length != 32) return decoded;
        var x509 = new byte[ED25519_X509_PREFIX.length + decoded.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, x509, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(decoded, 0, x509, ED25519_X509_PREFIX.length, decoded.length);
        return x509;
    }
}
