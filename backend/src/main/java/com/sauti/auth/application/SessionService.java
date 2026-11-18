package com.sauti.auth.application;

import com.sauti.auth.InvalidSessionException;
import com.sauti.auth.application.port.AccessTokens;
import com.sauti.auth.application.port.AccessTokens.AccessClaims;
import com.sauti.auth.application.port.AccessTokens.IssuedAccessToken;
import com.sauti.auth.domain.RefreshToken;
import com.sauti.auth.domain.RefreshToken.RefreshDecision;
import com.sauti.auth.domain.PlatformAdminRepository;
import com.sauti.auth.domain.RefreshTokenRepository;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions: sign-in starts one, refresh rotates its token, sign-out ends it. Only SHA-256 hashes
 * of refresh tokens are stored.
 */
@Service
public class SessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);
    static final Duration ADMIN_SESSION_LIFETIME = Duration.ofHours(12);

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final AccessTokens accessTokens;
    private final PlatformAdminRepository platformAdmins;
    private final Duration refreshLifetime;
    private final Duration rotationGrace;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SessionService(RefreshTokenRepository tokens, UserRepository users, AccessTokens accessTokens,
                          PlatformAdminRepository platformAdmins, ApplicationEventPublisher events,
                          @Value("${sauti.jwt.refresh-token-days}") long refreshTokenDays,
                          @Value("${sauti.jwt.refresh-token-rotation-grace-seconds:30}") long graceSeconds) {
        this(tokens, users, accessTokens, platformAdmins, events, Duration.ofDays(refreshTokenDays),
                Duration.ofSeconds(Math.max(0, graceSeconds)), Clock.systemUTC());
    }

    SessionService(RefreshTokenRepository tokens, UserRepository users, AccessTokens accessTokens,
                   PlatformAdminRepository platformAdmins, ApplicationEventPublisher events, Duration refreshLifetime,
                   Duration rotationGrace, Clock clock) {
        this.events = events;
        this.tokens = tokens;
        this.users = users;
        this.accessTokens = accessTokens;
        this.platformAdmins = platformAdmins;
        this.refreshLifetime = refreshLifetime;
        this.rotationGrace = rotationGrace;
        this.clock = clock;
    }

    @Transactional
    public SessionGrant start(User user, String userAgent) {
        var now = now();
        var raw = newToken();
        var token = tokens.save(RefreshToken.startSession(user.getId(), hash(raw), now.plus(refreshLifetime), userAgent, now));
        events.publishEvent(new AccountActivity(user.getId(), "session.started", "Signed in"));
        return grant(user, token, raw);
    }

    /**
     * A platform-admin session. Callers must have verified the password and an authenticator code;
     * it lasts at most {@link #ADMIN_SESSION_LIFETIME}.
     */
    @Transactional
    public SessionGrant startElevated(User user, String userAgent) {
        var now = now();
        var raw = newToken();
        var token = tokens.save(RefreshToken.startElevatedSession(user.getId(), hash(raw), now.plus(ADMIN_SESSION_LIFETIME),
                userAgent, now));
        events.publishEvent(new AccountActivity(user.getId(), "admin_session.started", "Signed in to Sauti administration"));
        return grant(user, token, raw);
    }

    /**
     * Rotates the refresh token. If an old, already-rotated token is presented, the whole session
     * is ended: that revocation is committed even though the call fails.
     */
    @Transactional(noRollbackFor = InvalidSessionException.class)
    public SessionGrant refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw new InvalidSessionException();
        var now = now();
        var current = tokens.findByTokenHash(hash(rawRefreshToken)).orElseThrow(InvalidSessionException::new);
        var decision = current.decide(rotationGrace, now);
        if (decision == RefreshDecision.REUSE_DETECTED) {
            LOGGER.warn("Refresh token reuse detected; session ended");
            endFamily(current.getFamilyId(), now);
            events.publishEvent(new AccountActivity(current.getUserId(), "session.reuse_detected",
                    "An old sign-in token was used again, so that session was ended"));
            throw new InvalidSessionException();
        }
        if (decision == RefreshDecision.REJECT) throw new InvalidSessionException();
        var user = users.findById(current.getUserId()).orElseThrow(InvalidSessionException::new);
        current.markRotated(now);
        tokens.save(current);
        var raw = newToken();
        var lifetime = current.isElevated() ? ADMIN_SESSION_LIFETIME : refreshLifetime;
        var next = tokens.save(current.successor(hash(raw), now.plus(lifetime), userAgent, now));
        return grant(user, next, raw);
    }

    /** Signs out this browser. Unknown tokens are ignored so sign-out never fails. */
    @Transactional
    public void end(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        tokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> endFamily(token.getFamilyId(), now()));
    }

    /** Signs the person out everywhere, for example after a password change. */
    @Transactional
    public void endAll(UUID userId) {
        var now = now();
        events.publishEvent(new AccountActivity(userId, "session.ended_everywhere", "Signed out on every device"));
        tokens.findAllByUserIdAndRevokedAtIsNull(userId).forEach(token -> {
            token.revoke(now);
            tokens.save(token);
        });
    }

    private void endFamily(UUID familyId, OffsetDateTime now) {
        tokens.findAllByFamilyIdAndRevokedAtIsNull(familyId).forEach(token -> {
            token.revoke(now);
            tokens.save(token);
        });
    }

    private SessionGrant grant(User user, RefreshToken token, String rawRefreshToken) {
        var admin = token.isElevated() && platformAdmins.existsById(user.getId());
        var access = accessTokens.issue(new AccessClaims(user.getId(), user.getEmail(), admin, token.getFamilyId()));
        return new SessionGrant(user, access, rawRefreshToken, token.getExpiresAt(), admin);
    }

    private String newToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /** A started or refreshed session. The refresh token goes into a cookie, never a response body. */
    public record SessionGrant(User user, IssuedAccessToken accessToken, String refreshToken, OffsetDateTime refreshExpiresAt,
                               boolean platformAdminSession) {
    }
}
