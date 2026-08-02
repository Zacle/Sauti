package com.sauti.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LemonSqueezyWebhookInbox {
    private final BillingProviderEventRepository events;
    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public LemonSqueezyWebhookInbox(BillingProviderEventRepository events, ObjectMapper objectMapper,
                                   @Value("${sauti.billing.lemon-squeezy.webhook-secret:}") String secret) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.secret = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void receive(String payload, String signature) {
        if (secret.length == 0) throw new IllegalStateException("Lemon Squeezy webhooks are not configured");
        if (!valid(payload, signature)) throw new SecurityException("Invalid Lemon Squeezy signature");
        var hash = sha256(payload);
        if (events.findByPayloadHash(hash).isPresent()) return;
        try {
            var root = objectMapper.readTree(payload);
            var eventName = root.path("meta").path("event_name").asText("").trim();
            if (eventName.isBlank()) throw new IllegalArgumentException("Billing event name is required");
            events.saveAndFlush(new BillingProviderEvent(hash, eventName, payload));
        } catch (DataIntegrityViolationException duplicate) {
            if (events.findByPayloadHash(hash).isEmpty()) throw duplicate;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Billing event payload is invalid", exception);
        }
    }

    private boolean valid(String payload, String supplied) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            var expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return supplied != null && MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), supplied.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not validate Lemon Squeezy signature", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not fingerprint billing event", exception);
        }
    }
}
