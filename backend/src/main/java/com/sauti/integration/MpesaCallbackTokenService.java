package com.sauti.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MpesaCallbackTokenService {
    private final byte[] secret;

    public MpesaCallbackTokenService(@Value("${sauti.webhooks.signing-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID connectionId) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(connectionId.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign M-Pesa callback", exception);
        }
    }

    public boolean isValid(UUID connectionId, String supplied) {
        if (supplied == null || supplied.isBlank()) return false;
        return MessageDigest.isEqual(
                issue(connectionId).getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }
}
