package com.sauti.auth.domain;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time codes (RFC 6238: HMAC-SHA1, 30-second steps, 6 digits), the format every
 * authenticator app understands. One step of clock drift is accepted either side.
 */
public final class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;

    private Totp() {
    }

    /** A new 160-bit secret, Base32-encoded for authenticator apps. */
    public static String newSecret(SecureRandom random) {
        var bytes = new byte[20];
        random.nextBytes(bytes);
        return base32(bytes);
    }

    public static boolean verify(String base32Secret, String code, Instant now) {
        if (code == null || !code.trim().matches("\\d{6}")) return false;
        var step = now.getEpochSecond() / STEP_SECONDS;
        var key = decode(base32Secret);
        var expected = code.trim().getBytes(StandardCharsets.US_ASCII);
        var matched = false;
        for (long offset = -1; offset <= 1; offset++) {
            matched |= MessageDigest.isEqual(codeAt(key, step + offset).getBytes(StandardCharsets.US_ASCII), expected);
        }
        return matched;
    }

    /** The code for the 30-second step containing {@code now}. */
    public static String codeAt(String base32Secret, Instant now) {
        return codeAt(decode(base32Secret), now.getEpochSecond() / STEP_SECONDS);
    }

    public static String otpauthUri(String issuer, String accountName, String base32Secret) {
        return "otpauth://totp/" + encode(issuer) + ":" + encode(accountName)
                + "?secret=" + base32Secret + "&issuer=" + encode(issuer) + "&digits=6&period=30";
    }

    private static String codeAt(byte[] key, long step) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            var hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            var offset = hash[hash.length - 1] & 0x0f;
            var binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA1 is not available", exception);
        }
    }

    static String base32(byte[] bytes) {
        var out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    static byte[] decode(String base32) {
        var clean = base32.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        var out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char character : clean.toCharArray()) {
            var value = ALPHABET.indexOf(character);
            if (value < 0) throw new IllegalArgumentException("Invalid Base32 secret");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
