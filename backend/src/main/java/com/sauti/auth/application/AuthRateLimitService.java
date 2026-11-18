package com.sauti.auth.application;

import com.sauti.auth.RateLimitExceededException;
import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Limits on sign-in and account-recovery attempts, per client address and per account. */
@Service
public class AuthRateLimitService {
    private final RedisRateLimiter rateLimiter;

    public AuthRateLimitService(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /** Both limits apply: one stops a single address trying many accounts, the other many addresses trying one. */
    public void checkLogin(String clientAddress, String email) {
        check("auth:login", clientAddress, 5, Duration.ofMinutes(1));
        check("auth:login-account", normalize(email), 10, Duration.ofMinutes(15));
    }

    public void checkGoogleLogin(String clientAddress) {
        check("auth:login", clientAddress, 5, Duration.ofMinutes(1));
    }

    public void checkForgotPassword(String email) {
        check("auth:forgot", normalize(email), 3, Duration.ofMinutes(5));
    }

    public void checkResetPassword(String email) {
        check("auth:reset", normalize(email), 10, Duration.ofMinutes(10));
    }

    public void checkVerifyEmail(String email) {
        check("auth:verify", normalize(email), 10, Duration.ofMinutes(10));
    }

    public void checkResendVerification(String email) {
        check("auth:resend", normalize(email), 3, Duration.ofMinutes(5));
    }

    /** Five wrong authenticator codes in five minutes locks the admin code step for that account. */
    public void checkAdminCode(java.util.UUID userId) {
        check("auth:admin-code", userId.toString(), 5, Duration.ofMinutes(5));
    }

    public void checkInvitationLink(String clientAddress) {
        check("auth:invitation", clientAddress, 20, Duration.ofMinutes(10));
    }

    private void check(String namespace, String identity, int limit, Duration window) {
        if (!rateLimiter.tryAcquire(namespace, identity, limit, window)) {
            throw new RateLimitExceededException("Too many attempts. Please try again later.");
        }
    }

    private String normalize(String email) {
        return email == null ? "unknown" : email.trim().toLowerCase(Locale.ROOT);
    }
}
