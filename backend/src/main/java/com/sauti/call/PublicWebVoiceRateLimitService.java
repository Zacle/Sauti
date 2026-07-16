package com.sauti.call;

import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class PublicWebVoiceRateLimitService {
    private final RedisRateLimiter rateLimiter;

    public PublicWebVoiceRateLimitService(RedisRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public void checkSessionStart(String publicAgentId, String clientAddress) {
        if (!rateLimiter.tryAcquire(
                "web-voice:start",
                publicAgentId + ":" + clientAddress,
                10,
                Duration.ofMinutes(1)
        )) {
            throw new PublicWebVoiceRateLimitExceededException();
        }
    }
}
