package com.sauti.demo;

import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class PublicDemoVoiceQuotaService {
    private static final DefaultRedisScript<Long> RELEASE_SLOT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final RedisRateLimiter rateLimiter;
    private final StringRedisTemplate redis;
    private final int sessionsPerIpPerDay;
    private final int sessionsPerDevicePerDay;
    private final int dailySeconds;
    private final int maxDurationSeconds;
    private final int maxConcurrent;

    public PublicDemoVoiceQuotaService(
            RedisRateLimiter rateLimiter,
            StringRedisTemplate redis,
            @Value("${sauti.public-demo.sessions-per-ip-per-day:2}") int sessionsPerIpPerDay,
            @Value("${sauti.public-demo.sessions-per-device-per-day:2}") int sessionsPerDevicePerDay,
            @Value("${sauti.public-demo.daily-seconds:1800}") int dailySeconds,
            @Value("${sauti.public-demo.max-duration-seconds:60}") int maxDurationSeconds,
            @Value("${sauti.public-demo.max-concurrent:3}") int maxConcurrent
    ) {
        this.rateLimiter = rateLimiter;
        this.redis = redis;
        this.sessionsPerIpPerDay = Math.max(1, sessionsPerIpPerDay);
        this.sessionsPerDevicePerDay = Math.max(1, sessionsPerDevicePerDay);
        this.dailySeconds = Math.max(1, dailySeconds);
        this.maxDurationSeconds = Math.max(15, maxDurationSeconds);
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    public void reserve(String sessionId, String clientAddress, String deviceId) {
        var slot = acquireSlot(sessionId);
        var day = Duration.ofDays(1);
        if (!rateLimiter.tryAcquire("public-demo:ip", clientAddress, sessionsPerIpPerDay, day)
                || !rateLimiter.tryAcquire("public-demo:device", deviceId, sessionsPerDevicePerDay, day)
                || !rateLimiter.tryAcquire(
                        "public-demo:daily-cap",
                        "global",
                        Math.max(1, dailySeconds / maxDurationSeconds),
                        day
                )) {
            releaseSlot(slot, sessionId);
            throw PublicDemoVoiceLimitExceededException.visitorLimit();
        }
    }

    private int acquireSlot(String sessionId) {
        var slotTtl = Duration.ofSeconds(maxDurationSeconds + 45L);
        for (int slot = 0; slot < maxConcurrent; slot += 1) {
            var acquired = redis.opsForValue().setIfAbsent(slotKey(slot), sessionId, slotTtl);
            if (Boolean.TRUE.equals(acquired)) return slot;
        }
        throw PublicDemoVoiceLimitExceededException.atCapacity();
    }

    public void release(String sessionId) {
        for (int slot = 0; slot < maxConcurrent; slot += 1) {
            releaseSlot(slot, sessionId);
        }
    }

    private void releaseSlot(int slot, String sessionId) {
        redis.execute(RELEASE_SLOT, List.of(slotKey(slot)), sessionId);
    }

    private String slotKey(int slot) {
        return "public-demo:active:" + slot;
    }
}
