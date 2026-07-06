package com.sauti.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final String issuer;
    private final Algorithm algorithm;
    private final long accessTokenMinutes;

    public JwtService(
            @Value("${sauti.jwt.issuer}") String issuer,
            @Value("${sauti.jwt.secret}") String secret,
            @Value("${sauti.jwt.access-token-minutes}") long accessTokenMinutes
    ) {
        this.issuer = issuer;
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String issueAccessToken(User user) {
        var now = Instant.now();
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(user.getId().toString())
                .withClaim("tenant_id", user.getTenant().getId().toString())
                .withClaim("email", user.getEmail())
                .withClaim("role", user.getRole())
                .withIssuedAt(now)
                .withExpiresAt(now.plus(accessTokenMinutes, ChronoUnit.MINUTES))
                .sign(algorithm);
    }

    public AuthenticatedUser authenticate(String token) {
        DecodedJWT jwt = JWT.require(algorithm).withIssuer(issuer).build().verify(token);
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaim("tenant_id").asString()),
                jwt.getClaim("email").asString(),
                jwt.getClaim("role").asString()
        );
    }
}
