package com.sauti.auth.infrastructure;

import com.sauti.auth.application.port.OneTimeCodes;
import com.sauti.auth.domain.OneTimeCodePurpose;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Emailed codes in Redis. Codes come from {@link SecureRandom}, are stored as SHA-256 hashes,
 * compared in constant time, and discarded after {@link OneTimeCodePurpose#MAX_ATTEMPTS} wrong guesses.
 */
@Component
public class RedisOneTimeCodes implements OneTimeCodes {
    private final StringRedisTemplate redis;
    private final Duration lifetime;
    private final int length;
    private final SecureRandom random = new SecureRandom();

    public RedisOneTimeCodes(StringRedisTemplate redis,
                             @Value("${sauti.verification.code-expiry-minutes:15}") long expiryMinutes,
                             @Value("${sauti.verification.code-length:6}") int length) {
        this.redis = redis;
        this.lifetime = Duration.ofMinutes(expiryMinutes);
        this.length = length;
    }

    @Override
    public String issue(OneTimeCodePurpose purpose, String email) {
        var code = newCode();
        redis.opsForValue().set(codeKey(purpose, email), hash(code), lifetime);
        redis.delete(attemptsKey(purpose, email));
        return code;
    }

    @Override
    public boolean verify(OneTimeCodePurpose purpose, String email, String code) {
        var stored = redis.opsForValue().get(codeKey(purpose, email));
        if (stored == null || code == null) return false;
        if (MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), hash(code.trim()).getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        var attemptsKey = attemptsKey(purpose, email);
        var attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) redis.expire(attemptsKey, lifetime);
        if (attempts != null && attempts >= OneTimeCodePurpose.MAX_ATTEMPTS) discard(purpose, email);
        return false;
    }

    @Override
    public void discard(OneTimeCodePurpose purpose, String email) {
        redis.delete(codeKey(purpose, email));
        redis.delete(attemptsKey(purpose, email));
    }

    private String newCode() {
        var bound = (int) Math.pow(10, length);
        var floor = (int) Math.pow(10, length - 1);
        return String.valueOf(floor + random.nextInt(bound - floor));
    }

    private String codeKey(OneTimeCodePurpose purpose, String email) {
        return purpose.keyPrefix() + normalize(email);
    }

    private String attemptsKey(OneTimeCodePurpose purpose, String email) {
        return purpose.keyPrefix() + "attempts:" + normalize(email);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
