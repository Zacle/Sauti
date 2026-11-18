package com.sauti.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.auth.domain.OneTimeCodePurpose;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisOneTimeCodesTest {
    private final Map<String, String> store = new HashMap<>();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisOneTimeCodes codes;

    @SuppressWarnings("unchecked")
    RedisOneTimeCodesTest() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> store.put(call.getArgument(0), call.getArgument(1)))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(call -> store.get(call.<String>getArgument(0)));
        when(values.increment(anyString())).thenAnswer(call -> {
            var next = Long.parseLong(store.getOrDefault(call.<String>getArgument(0), "0")) + 1;
            store.put(call.getArgument(0), Long.toString(next));
            return next;
        });
        when(redis.delete(anyString())).thenAnswer(call -> store.remove(call.<String>getArgument(0)) != null);
        codes = new RedisOneTimeCodes(redis, 15, 6);
    }

    @Test
    void storesAHashAndAcceptsTheIssuedCode() {
        var code = codes.issue(OneTimeCodePurpose.PASSWORD_RESET, "Owner@Example.com");

        assertThat(code).hasSize(6);
        assertThat(store.values()).doesNotContain(code);
        assertThat(codes.verify(OneTimeCodePurpose.PASSWORD_RESET, "owner@example.com", code)).isTrue();
        assertThat(codes.verify(OneTimeCodePurpose.EMAIL_VERIFICATION, "owner@example.com", code)).isFalse();
    }

    @Test
    void discardsTheCodeAfterTooManyWrongGuesses() {
        var code = codes.issue(OneTimeCodePurpose.EMAIL_VERIFICATION, "owner@example.com");
        var wrong = code.equals("111111") ? "222222" : "111111";

        for (int attempt = 0; attempt < OneTimeCodePurpose.MAX_ATTEMPTS; attempt++) {
            assertThat(codes.verify(OneTimeCodePurpose.EMAIL_VERIFICATION, "owner@example.com", wrong)).isFalse();
        }

        assertThat(codes.verify(OneTimeCodePurpose.EMAIL_VERIFICATION, "owner@example.com", code)).isFalse();
    }
}
