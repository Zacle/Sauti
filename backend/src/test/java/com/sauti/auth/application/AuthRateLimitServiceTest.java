package com.sauti.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.auth.RateLimitExceededException;
import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthRateLimitServiceTest {
    private final RedisRateLimiter limiter = mock(RedisRateLimiter.class);
    private final AuthRateLimitService limits = new AuthRateLimitService(limiter);

    @Test
    void limitsResetCodeAttemptsByNormalizedEmail() {
        when(limiter.tryAcquire("auth:reset", "owner@example.com", 10, Duration.ofMinutes(10))).thenReturn(true);

        limits.checkResetPassword(" Owner@Example.com ");

        verify(limiter).tryAcquire("auth:reset", "owner@example.com", 10, Duration.ofMinutes(10));
    }

    @Test
    void loginIsLimitedPerAddressAndPerAccount() {
        when(limiter.tryAcquire(eq("auth:login"), anyString(), anyInt(), eq(Duration.ofMinutes(1)))).thenReturn(true);
        when(limiter.tryAcquire("auth:login-account", "owner@example.com", 10, Duration.ofMinutes(15))).thenReturn(false);

        assertThatThrownBy(() -> limits.checkLogin("203.0.113.9", "Owner@example.com"))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
