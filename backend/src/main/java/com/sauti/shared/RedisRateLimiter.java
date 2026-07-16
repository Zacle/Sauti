package com.sauti.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** A distributed fixed-window limiter that avoids storing raw user identifiers in Redis keys. */
@Service
public class RedisRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String namespace, String identity, int limit, Duration window) {
        var key = "rate:" + safeNamespace(namespace) + ":" + hash(identity);
        Long count = redisTemplate.execute(
                INCREMENT_WITH_EXPIRY,
                List.of(key),
                String.valueOf(window.toMillis())
        );
        return count != null && count <= limit;
    }

    private String safeNamespace(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9:_-]", "_");
    }

    private String hash(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "unknown" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("Rate-limit identity could not be protected", exception);
        }
    }
}
