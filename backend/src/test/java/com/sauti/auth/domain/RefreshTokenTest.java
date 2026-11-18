package com.sauti.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sauti.auth.domain.RefreshToken.RefreshDecision;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {
    private static final Duration GRACE = Duration.ofSeconds(30);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-18T12:00:00Z");

    @Test
    void anUnusedTokenRotates() {
        assertThat(token().decide(GRACE, NOW)).isEqualTo(RefreshDecision.ROTATE);
    }

    @Test
    void aSecondTabRefreshingAtTheSameMomentIsAllowedInsideTheGraceWindow() {
        var token = token();
        token.markRotated(NOW);

        assertThat(token.decide(GRACE, NOW.plusSeconds(29))).isEqualTo(RefreshDecision.ROTATE_WITHIN_GRACE);
    }

    @Test
    void aRotatedTokenPresentedAfterTheGraceWindowIsTreatedAsStolen() {
        var token = token();
        token.markRotated(NOW);

        assertThat(token.decide(GRACE, NOW.plusSeconds(30))).isEqualTo(RefreshDecision.REUSE_DETECTED);
    }

    @Test
    void revokedOrExpiredTokensAreRejectedWithoutBeingTreatedAsTheft() {
        var revoked = token();
        revoked.markRotated(NOW);
        revoked.revoke(NOW);
        assertThat(revoked.decide(GRACE, NOW.plusSeconds(1))).isEqualTo(RefreshDecision.REJECT);

        assertThat(token().decide(GRACE, NOW.plusDays(31))).isEqualTo(RefreshDecision.REJECT);
    }

    @Test
    void aSuccessorStaysInTheSameSessionFamily() {
        var first = token();
        var next = first.successor("next-hash", NOW.plusDays(30), "agent", NOW);

        assertThat(next.getFamilyId()).isEqualTo(first.getFamilyId());
        assertThat(next.getUserId()).isEqualTo(first.getUserId());
    }

    private RefreshToken token() {
        return RefreshToken.startSession(UUID.randomUUID(), "hash", NOW.plusDays(30), "agent", NOW);
    }
}
