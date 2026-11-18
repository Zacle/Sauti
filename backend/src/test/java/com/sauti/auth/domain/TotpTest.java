package com.sauti.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {
    /** RFC 6238 appendix B uses the ASCII secret "12345678901234567890" with SHA-1. */
    private static final String RFC_SECRET = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfc6238TestVectors() {
        assertThat(Totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(Totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThat(Totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(2000000000))).isEqualTo("279037");
    }

    @Test
    void acceptsOneStepOfClockDriftButNotMore() {
        var now = Instant.ofEpochSecond(1_800_000_000L);
        var current = Totp.codeAt(RFC_SECRET, now);

        assertThat(Totp.verify(RFC_SECRET, current, now.plusSeconds(30))).isTrue();
        assertThat(Totp.verify(RFC_SECRET, current, now.minusSeconds(30))).isTrue();
        assertThat(Totp.verify(RFC_SECRET, current, now.plusSeconds(90))).isFalse();
        assertThat(Totp.verify(RFC_SECRET, "12345", now)).isFalse();
        assertThat(Totp.verify(RFC_SECRET, null, now)).isFalse();
    }

    @Test
    void newSecretsRoundTripThroughBase32() {
        var secret = Totp.newSecret(new SecureRandom());

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32(Totp.decode(secret))).isEqualTo(secret);
        assertThat(Totp.otpauthUri("Sauti Admin", "ops@sauti.uk", secret))
                .startsWith("otpauth://totp/Sauti%20Admin:ops%40sauti.uk?secret=" + secret);
    }
}
