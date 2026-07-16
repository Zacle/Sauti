package com.sauti.whatsapp;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppSignatureValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppSignatureValidator.class);
    private final String appSecret;
    private final boolean validationEnabled;

    public WhatsAppSignatureValidator(
            @Value("${sauti.whatsapp.app-secret:}") String appSecret,
            @Value("${sauti.whatsapp.validate-signature:true}") boolean validationEnabled
    ) {
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.validationEnabled = validationEnabled;
    }

    @PostConstruct
    void logAvailability() {
        if (validationEnabled && appSecret.isBlank()) {
            LOGGER.info("WhatsApp webhook receiver is disabled because no Meta App Secret is configured");
        }
    }

    public boolean isValid(String payload, String signatureHeader) {
        if (!validationEnabled) {
            return true;
        }
        if (appSecret.isBlank() || signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        var supplied = signatureHeader.substring("sha256=".length()).toLowerCase(java.util.Locale.ROOT);
        var expected = hmacHex(payload == null ? "" : payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String hmacHex(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to validate WhatsApp webhook signature", exception);
        }
    }
}
