package com.sauti.tool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CredentialEncryption {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] key;

    public CredentialEncryption(@Value("${sauti.tools.encryption-key:dev-tool-encryption-key-32-bytes}") String rawKey) {
        var bytes = decodeKey(rawKey);
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "sauti.tools.encryption-key must be 32 UTF-8 bytes or 64 hexadecimal characters"
            );
        }
        this.key = bytes;
    }

    private byte[] decodeKey(String rawKey) {
        if (rawKey != null && rawKey.matches("[0-9a-fA-F]{64}")) {
            return HexFormat.of().parseHex(rawKey);
        }
        return rawKey == null ? new byte[0] : rawKey.getBytes(StandardCharsets.UTF_8);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        try {
            var iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt credential", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }
        try {
            var allBytes = Base64.getDecoder().decode(ciphertext);
            var buffer = ByteBuffer.wrap(allBytes);
            var iv = new byte[IV_BYTES];
            buffer.get(iv);
            var encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt credential", exception);
        }
    }
}
