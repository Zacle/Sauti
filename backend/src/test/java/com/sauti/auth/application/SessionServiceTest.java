package com.sauti.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sauti.auth.InvalidSessionException;
import com.sauti.auth.application.port.AccessTokens;
import com.sauti.auth.domain.RefreshToken;
import com.sauti.auth.domain.RefreshTokenRepository;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionServiceTest {
    private final InMemoryTokens tokens = new InMemoryTokens();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-18T12:00:00Z"));
    private final User user = User.withPassword("owner@example.com", "hash");
    private SessionService sessions;

    @BeforeEach
    void setUp() {
        var users = new SingleUser(user);
        var clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        sessions = new SessionService(tokens, users, new FakeAccessTokens(), new NoAdmins(), event -> { },
                Duration.ofDays(30), Duration.ofSeconds(30), clock);
    }

    @Test
    void refreshRotatesTheTokenWithinTheSameSession() {
        var started = sessions.start(user, "browser");
        var refreshed = sessions.refresh(started.refreshToken(), "browser");

        assertThat(refreshed.refreshToken()).isNotEqualTo(started.refreshToken());
        assertThat(tokens.saved).extracting(RefreshToken::getFamilyId).containsOnly(tokens.saved.get(0).getFamilyId());
    }

    @Test
    void reusingAnOldRefreshTokenEndsTheWholeSession() {
        var started = sessions.start(user, "browser");
        var current = sessions.refresh(started.refreshToken(), "browser");
        now.set(now.get().plusSeconds(60));

        assertThatThrownBy(() -> sessions.refresh(started.refreshToken(), "attacker"))
                .isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> sessions.refresh(current.refreshToken(), "browser"))
                .isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void signingOutEverywhereEndsEverySession() {
        var laptop = sessions.start(user, "laptop");
        var phone = sessions.start(user, "phone");

        sessions.endAll(user.getId());

        assertThatThrownBy(() -> sessions.refresh(laptop.refreshToken(), "laptop")).isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> sessions.refresh(phone.refreshToken(), "phone")).isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void storesOnlyAHashOfTheRefreshToken() {
        var started = sessions.start(user, "browser");

        assertThat(tokens.findByTokenHash(started.refreshToken())).isEmpty();
        assertThat(tokens.findByTokenHash(SessionService.hash(started.refreshToken()))).isPresent();
    }

    private static final class InMemoryTokens implements RefreshTokenRepository {
        final List<RefreshToken> saved = new ArrayList<>();
        final List<String> hashes = new ArrayList<>();

        @Override
        public RefreshToken save(RefreshToken token) {
            if (!saved.contains(token)) {
                saved.add(token);
                hashes.add(hashOf(token));
            }
            return token;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            var index = hashes.indexOf(tokenHash);
            return index < 0 ? Optional.empty() : Optional.of(saved.get(index));
        }

        @Override
        public List<RefreshToken> findAllByFamilyIdAndRevokedAtIsNull(UUID familyId) {
            return saved.stream().filter(t -> t.getFamilyId().equals(familyId) && !t.isRevoked()).toList();
        }

        @Override
        public List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId) {
            return saved.stream().filter(t -> t.getUserId().equals(userId) && !t.isRevoked()).toList();
        }

        private static String hashOf(RefreshToken token) {
            try {
                var field = RefreshToken.class.getDeclaredField("tokenHash");
                field.setAccessible(true);
                return (String) field.get(token);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private record SingleUser(User user) implements UserRepository {
        @Override public User save(User saved) { return saved; }
        @Override public Optional<User> findById(UUID id) { return user.getId().equals(id) ? Optional.of(user) : Optional.empty(); }
        @Override public Optional<User> findByEmail(String email) { return Optional.of(user); }
        @Override public boolean existsByEmail(String email) { return true; }
        @Override public List<User> findAllById(Iterable<UUID> ids) { return List.of(user); }
    }

    private static final class NoAdmins implements com.sauti.auth.domain.PlatformAdminRepository {
        @Override public com.sauti.auth.domain.PlatformAdmin save(com.sauti.auth.domain.PlatformAdmin admin) { return admin; }
        @Override public Optional<com.sauti.auth.domain.PlatformAdmin> findById(UUID userId) { return Optional.empty(); }
        @Override public boolean existsById(UUID userId) { return false; }
        @Override public void deleteById(UUID userId) { }
    }

    private static final class FakeAccessTokens implements AccessTokens {
        @Override
        public IssuedAccessToken issue(AccessClaims claims) {
            return new IssuedAccessToken("access-" + UUID.randomUUID(), 900);
        }

        @Override
        public AccessClaims verify(String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String issueAdminChallenge(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID verifyAdminChallenge(String challenge) {
            throw new UnsupportedOperationException();
        }
    }
}
