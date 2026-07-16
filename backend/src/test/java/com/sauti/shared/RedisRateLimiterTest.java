package com.sauti.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRateLimiterTest {
    @Test
    void protectsIdentityAndUsesAtomicExpiryScript() {
        var redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), eq("60000"))).thenReturn(1L);
        var limiter = new RedisRateLimiter(redis);

        assertThat(limiter.tryAcquire("auth:login", "person@example.com", 5, Duration.ofMinutes(1)))
                .isTrue();

        verify(redis).execute(any(), argThat(keys -> {
            assertThat(keys).singleElement()
                    .asString()
                    .startsWith("rate:auth:login:")
                    .doesNotContain("person@example.com");
            return true;
        }), eq("60000"));
    }

    @Test
    void rejectsRequestsPastTheLimit() {
        var redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), eq("300000"))).thenReturn(4L);
        var limiter = new RedisRateLimiter(redis);

        assertThat(limiter.tryAcquire("auth:forgot", "person@example.com", 3, Duration.ofMinutes(5)))
                .isFalse();
    }
}
