package com.sauti.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhopWebhookInbox {
    private static final String PROVIDER = "whop";
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    private final BillingProviderEventRepository events;
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final String companyId;
    private final Clock clock;

    @Autowired
    public WhopWebhookInbox(BillingProviderEventRepository events, ObjectMapper objectMapper,
                            @Value("${sauti.billing.whop.webhook-secret:}") String webhookSecret,
                            @Value("${sauti.billing.whop.company-id:}") String companyId) {
        this(events, objectMapper, webhookSecret, companyId, Clock.systemUTC());
    }

    WhopWebhookInbox(BillingProviderEventRepository events, ObjectMapper objectMapper,
                     String webhookSecret, String companyId, Clock clock) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.secret = clean(webhookSecret).getBytes(StandardCharsets.UTF_8);
        this.companyId = clean(companyId);
        this.clock = clock;
    }

    @Transactional
    public void receive(String payload, String webhookId, String timestamp, String signature) {
        requireConfigured();
        validate(payload, webhookId, timestamp, signature);
        try {
            var root = objectMapper.readTree(payload);
            if (!webhookId.equals(required(root.path("id"), "event id"))) {
                throw new SecurityException("Whop event id does not match its signed header");
            }
            var eventCompany = root.path("company_id").asText("").trim();
            if (!companyId.equals(eventCompany)) throw new SecurityException("Whop company does not match Sauti configuration");
            var type = required(root.path("type"), "event type");
            var hash = sha256(PROVIDER + ":" + webhookId);
            if (events.findByProviderAndPayloadHash(PROVIDER, hash).isEmpty()) {
                try {
                    events.saveAndFlush(new BillingProviderEvent(PROVIDER, hash, type, payload));
                } catch (DataIntegrityViolationException duplicate) {
                    if (events.findByProviderAndPayloadHash(PROVIDER, hash).isEmpty()) throw duplicate;
                }
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Whop webhook payload is invalid", exception);
        }
    }

    private void validate(String payload, String webhookId, String timestamp, String signature) {
        if (payload == null || payload.isBlank() || clean(webhookId).isBlank()
                || clean(timestamp).isBlank() || clean(signature).isBlank()) {
            throw new SecurityException("Whop webhook signature headers are required");
        }
        long seconds;
        try { seconds = Long.parseLong(timestamp); }
        catch (NumberFormatException exception) { throw new SecurityException("Whop webhook timestamp is invalid"); }
        var age = Duration.between(Instant.ofEpochSecond(seconds), clock.instant()).abs();
        if (age.compareTo(MAX_AGE) > 0) throw new SecurityException("Whop webhook timestamp is outside the replay window");
        var expected = hmac(webhookId + "." + timestamp + "." + payload);
        var matched = false;
        for (var candidate : signature.trim().split("\\s+")) {
            var parts = candidate.split(",", 2);
            if (parts.length == 2 && "v1".equals(parts[0])) {
                matched |= MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        parts[1].getBytes(StandardCharsets.UTF_8));
            }
        }
        if (!matched) throw new SecurityException("Invalid Whop webhook signature");
    }

    private String hmac(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not validate Whop webhook", exception);
        }
    }

    private static String required(com.fasterxml.jackson.databind.JsonNode node, String label) {
        var value = node.asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException("Whop " + label + " is required");
        return value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not fingerprint Whop event", exception);
        }
    }

    private void requireConfigured() {
        if (secret.length == 0 || companyId.isBlank()) throw new IllegalStateException("Whop webhooks are not configured");
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
