package com.sauti.auth;

import jakarta.servlet.http.HttpServletRequest;
import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimitService {
    private final RedisRateLimiter rateLimiter;

    public AuthRateLimitService(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void checkLogin(HttpServletRequest request) {
        check("auth:login", clientIp(request), 5, Duration.ofMinutes(1));
    }

    public void checkForgotPassword(String email) {
        check("auth:forgot", normalize(email), 3, Duration.ofMinutes(5));
    }

    public void checkVerifyEmail(String email) {
        check("auth:verify", normalize(email), 10, Duration.ofMinutes(10));
    }

    public void checkResendVerification(String email) {
        check("auth:resend", normalize(email), 3, Duration.ofMinutes(5));
    }

    private void check(String namespace, String identity, int limit, Duration window) {
        if (!rateLimiter.tryAcquire(namespace, identity, limit, window)) {
            throw new RateLimitExceededException("Too many attempts. Please try again later.");
        }
    }

    private String normalize(String email) {
        return email == null ? "unknown" : email.toLowerCase().trim();
    }

    private String clientIp(HttpServletRequest request) {
        var forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
