package com.sauti.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class WhopTenantReference {
    private static final String PREFIX = "sauti_";
    private final byte[] secret;

    WhopTenantReference(String secret) {
        this.secret = clean(secret).getBytes(StandardCharsets.UTF_8);
    }

    String create(UUID tenantId) {
        requireConfigured();
        var id = tenantId.toString().replace("-", "");
        return PREFIX + id + "_" + signature(id);
    }

    UUID verify(String reference) {
        requireConfigured();
        var value = clean(reference);
        if (!value.startsWith(PREFIX)) throw new SecurityException("Invalid Whop workspace reference");
        var parts = value.substring(PREFIX.length()).split("_", -1);
        if (parts.length != 2 || parts[0].length() != 32) throw new SecurityException("Invalid Whop workspace reference");
        var expected = signature(parts[0]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid Whop workspace reference");
        }
        var id = parts[0];
        return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-"
                + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20));
    }

    private String signature(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not protect Whop workspace reference", exception);
        }
    }

    private void requireConfigured() {
        if (secret.length == 0) throw new IllegalStateException("Whop checkout is not configured");
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
