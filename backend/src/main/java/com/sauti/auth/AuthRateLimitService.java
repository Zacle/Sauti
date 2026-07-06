package com.sauti.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimitService {
    private final StringRedisTemplate redisTemplate;

    public AuthRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkLogin(HttpServletRequest request) {
        check("auth:rate:login:" + clientIp(request), 5, Duration.ofMinutes(1));
    }

    public void checkForgotPassword(String email) {
        check("auth:rate:forgot:" + normalize(email), 3, Duration.ofMinutes(5));
    }

    public void checkVerifyEmail(String email) {
        check("auth:rate:verify:" + normalize(email), 10, Duration.ofMinutes(10));
    }

    public void checkResendVerification(String email) {
        check("auth:rate:resend:" + normalize(email), 3, Duration.ofMinutes(5));
    }

    private void check(String key, int limit, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > limit) {
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
