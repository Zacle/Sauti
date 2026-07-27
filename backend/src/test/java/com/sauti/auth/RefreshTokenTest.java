package com.sauti.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    @Test
    void permitsConcurrentRotationOnlyInsideTheBoundedGraceWindow() {
        var now = OffsetDateTime.parse("2026-07-27T12:00:00Z");
        var token = new RefreshToken(null, "hash", now.plusDays(30));

        assertThat(token.canRotate(ROTATION_GRACE, now)).isTrue();

        token.markRotated(now);

        assertThat(token.isActive()).isFalse();
        assertThat(token.canRotate(ROTATION_GRACE, now.plusSeconds(29))).isTrue();
        assertThat(token.canRotate(ROTATION_GRACE, now.plusSeconds(30))).isFalse();
    }

    @Test
    void explicitRevocationCannotBeRecoveredThroughRotationGrace() {
        var now = OffsetDateTime.parse("2026-07-27T12:00:00Z");
        var token = new RefreshToken(null, "hash", now.plusDays(30));

        token.markRotated(now);
        token.revoke();

        assertThat(token.canRotate(ROTATION_GRACE, now.plusSeconds(1))).isFalse();
    }
}
