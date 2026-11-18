package com.sauti.auth.infrastructure;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sauti.auth.application.port.AccessTokens;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HS256 access tokens for the console. The audience claim keeps them from being accepted by any
 * other Sauti token consumer, and tokens from before the multi-workspace change are rejected.
 */
@Component
public class JwtAccessTokens implements AccessTokens {
    static final String AUDIENCE = "sauti-console";
    static final String ADMIN_CHALLENGE_AUDIENCE = "sauti-admin-challenge";

    private final String issuer;
    private final Algorithm algorithm;
    private final long accessTokenMinutes;
    private final Clock clock;

    @Autowired
    public JwtAccessTokens(@Value("${sauti.jwt.issuer}") String issuer,
                           @Value("${sauti.jwt.secret}") String secret,
                           @Value("${sauti.jwt.access-token-minutes}") long accessTokenMinutes) {
        this(issuer, secret, accessTokenMinutes, Clock.systemUTC());
    }

    JwtAccessTokens(String issuer, String secret, long accessTokenMinutes, Clock clock) {
        this.issuer = issuer;
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessTokenMinutes = accessTokenMinutes;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(AccessClaims claims) {
        var now = Instant.now(clock);
        var token = JWT.create()
                .withIssuer(issuer)
                .withAudience(AUDIENCE)
                .withSubject(claims.userId().toString())
                .withClaim("email", claims.email())
                .withClaim("adm", claims.platformAdmin())
                .withClaim("sid", claims.sessionId() == null ? null : claims.sessionId().toString())
                .withIssuedAt(now)
                .withExpiresAt(now.plus(accessTokenMinutes, ChronoUnit.MINUTES))
                .sign(algorithm);
        return new IssuedAccessToken(token, accessTokenMinutes * 60);
    }

    @Override
    public String issueAdminChallenge(UUID userId) {
        var now = Instant.now(clock);
        return JWT.create()
                .withIssuer(issuer)
                .withAudience(ADMIN_CHALLENGE_AUDIENCE)
                .withSubject(userId.toString())
                .withIssuedAt(now)
                .withExpiresAt(now.plus(5, ChronoUnit.MINUTES))
                .sign(algorithm);
    }

    @Override
    public UUID verifyAdminChallenge(String challenge) {
        var jwt = JWT.require(algorithm).withIssuer(issuer).withAudience(ADMIN_CHALLENGE_AUDIENCE).build().verify(challenge);
        return UUID.fromString(jwt.getSubject());
    }

    @Override
    public AccessClaims verify(String token) {
        var jwt = JWT.require(algorithm).withIssuer(issuer).withAudience(AUDIENCE).build().verify(token);
        var sessionId = jwt.getClaim("sid").asString();
        return new AccessClaims(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaim("email").asString(),
                Boolean.TRUE.equals(jwt.getClaim("adm").asBoolean()),
                sessionId == null ? null : UUID.fromString(sessionId));
    }
}
