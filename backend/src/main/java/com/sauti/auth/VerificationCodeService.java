package com.sauti.auth;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class VerificationCodeService {
    private static final String EMAIL_VERIFY_PREFIX = "email:verify:";
    private static final String PASSWORD_RESET_PREFIX = "password:reset:";

    private final StringRedisTemplate redisTemplate;
    private final long expiryMinutes;
    private final int codeLength;

    public VerificationCodeService(
            StringRedisTemplate redisTemplate,
            @Value("${sauti.verification.code-expiry-minutes:15}") long expiryMinutes,
            @Value("${sauti.verification.code-length:6}") int codeLength
    ) {
        this.redisTemplate = redisTemplate;
        this.expiryMinutes = expiryMinutes;
        this.codeLength = codeLength;
    }

    public String generateAndStoreEmailVerificationCode(User user) {
        return generateAndStoreCode(emailVerificationKey(user.getEmail()));
    }

    public String generateAndStorePasswordResetCode(User user) {
        return generateAndStoreCode(passwordResetKey(user.getEmail()));
    }

    public boolean verifyEmailCode(User user, String code) {
        return code.equals(redisTemplate.opsForValue().get(emailVerificationKey(user.getEmail())));
    }

    public boolean verifyPasswordResetCode(User user, String code) {
        return code.equals(redisTemplate.opsForValue().get(passwordResetKey(user.getEmail())));
    }

    public void deleteEmailVerificationCode(User user) {
        redisTemplate.delete(emailVerificationKey(user.getEmail()));
    }

    public void deletePasswordResetCode(User user) {
        redisTemplate.delete(passwordResetKey(user.getEmail()));
    }

    private String generateAndStoreCode(String key) {
        var code = generateCode();
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(expiryMinutes));
        return code;
    }

    private String generateCode() {
        var start = (int) Math.pow(10, codeLength - 1);
        var bound = (int) Math.pow(10, codeLength);
        return String.valueOf(ThreadLocalRandom.current().nextInt(start, bound));
    }

    private String emailVerificationKey(String email) {
        return EMAIL_VERIFY_PREFIX + email.toLowerCase();
    }

    private String passwordResetKey(String email) {
        return PASSWORD_RESET_PREFIX + email.toLowerCase();
    }
}
