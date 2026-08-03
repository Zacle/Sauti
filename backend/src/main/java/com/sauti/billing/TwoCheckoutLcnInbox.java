package com.sauti.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TwoCheckoutLcnInbox {
    private static final String PROVIDER = "2checkout";
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final BillingProviderEventRepository events;
    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public TwoCheckoutLcnInbox(BillingProviderEventRepository events, ObjectMapper objectMapper,
                              @Value("${sauti.billing.2checkout.secret-key:}") String secretKey) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.secret = clean(secretKey).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public String receive(String rawPayload) {
        requireConfigured();
        var form = parse(rawPayload);
        validate(form);
        var hash = sha256(rawPayload);
        if (events.findByProviderAndPayloadHash(PROVIDER, hash).isEmpty()) {
            try {
                var payload = objectMapper.writeValueAsString(form.values());
                var eventName = first(form.values(), "DISPATCH_REASON", "STATUS", "LICENSE_CODE");
                events.saveAndFlush(new BillingProviderEvent(PROVIDER, hash, eventName, payload));
            } catch (DataIntegrityViolationException duplicate) {
                if (events.findByProviderAndPayloadHash(PROVIDER, hash).isEmpty()) throw duplicate;
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalArgumentException("2Checkout notification payload is invalid", exception);
            }
        }
        return receipt(form.values());
    }

    private void validate(FormPayload form) {
        var supplied = form.values().getOrDefault("SIGNATURE_SHA2_256", "");
        if (supplied.isBlank()) throw new SecurityException("2Checkout SHA-256 signature is required");
        var source = new StringBuilder();
        for (var entry : form.ordered()) {
            if (isSignature(entry.name())) continue;
            append(source, entry.value());
        }
        var expected = hmac(source.toString());
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                supplied.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid 2Checkout signature");
        }
    }

    private String receipt(Map<String, String> values) {
        var license = required(values, "LICENSE_CODE");
        var expiration = required(values, "EXPIRATION_DATE");
        var date = RECEIPT_DATE.format(OffsetDateTime.now(ZoneOffset.UTC));
        var source = new StringBuilder();
        append(source, license);
        append(source, expiration);
        append(source, date);
        return "<sig algo=\"sha256\" date=\"" + date + "\">" + hmac(source.toString()) + "</sig>";
    }

    static FormPayload parse(String payload) {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("2Checkout payload is required");
        var ordered = new ArrayList<FormEntry>();
        var values = new LinkedHashMap<String, String>();
        for (var pair : payload.split("&", -1)) {
            var parts = pair.split("=", 2);
            var name = decode(parts[0]).trim().toUpperCase(Locale.ROOT);
            var value = decode(parts.length == 2 ? parts[1] : "");
            if (name.isBlank()) continue;
            ordered.add(new FormEntry(name, value));
            values.put(name, value);
        }
        return new FormPayload(List.copyOf(ordered), Map.copyOf(values));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean isSignature(String name) {
        return "HASH".equals(name) || "SIGNATURE_SHA2_256".equals(name)
                || "SIGNATURE_SHA3_256".equals(name);
    }

    private static void append(StringBuilder target, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        target.append(bytes.length).append(value);
    }

    private String hmac(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not validate 2Checkout notification", exception);
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

    private static String first(Map<String, String> values, String... names) {
        for (var name : names) {
            var value = values.getOrDefault(name, "").trim();
            if (!value.isBlank()) return value;
        }
        throw new IllegalArgumentException("2Checkout event name is required");
    }

    private static String required(Map<String, String> values, String name) {
        var value = values.getOrDefault(name, "").trim();
        if (value.isBlank()) throw new IllegalArgumentException("2Checkout " + name + " is required");
        return value;
    }

    private void requireConfigured() {
        if (secret.length == 0) throw new IllegalStateException("2Checkout webhooks are not configured");
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    record FormEntry(String name, String value) { }
    record FormPayload(List<FormEntry> ordered, Map<String, String> values) { }
}
