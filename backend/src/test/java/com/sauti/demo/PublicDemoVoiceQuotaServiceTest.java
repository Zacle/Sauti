package com.sauti.demo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class PublicDemoVoiceQuotaServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void releasesTheConcurrencySlotWhenADailyLimitRejectsTheVisitor() {
        var limiter = mock(RedisRateLimiter.class);
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(limiter.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        var service = new PublicDemoVoiceQuotaService(limiter, redis, 2, 2, 1800, 60, 3);

        assertThatThrownBy(() -> service.reserve("demo-session", "203.0.113.5", "1234567890abcdef"))
                .isInstanceOf(PublicDemoVoiceLimitExceededException.class)
                .hasMessageContaining("today’s short voice demos");

        verify(redis).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of("public-demo:active:0")),
                org.mockito.ArgumentMatchers.eq("demo-session")
        );
    }
}
