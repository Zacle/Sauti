package com.sauti.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthRateLimitServiceTest {
    @Test
    void limitsResetCodeAttemptsByNormalizedEmail() {
        var limiter = mock(RedisRateLimiter.class);
        when(limiter.tryAcquire("auth:reset", "owner@example.com", 10, Duration.ofMinutes(10))).thenReturn(true);

        new AuthRateLimitService(limiter).checkResetPassword(" Owner@Example.com ");

        verify(limiter).tryAcquire("auth:reset", "owner@example.com", 10, Duration.ofMinutes(10));
    }
}
