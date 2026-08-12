package com.sauti.dashboard;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sauti.auth.AuthenticatedUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardSocketTicketService {
    private final String issuer;
    private final Algorithm algorithm;

    public DashboardSocketTicketService(
            @Value("${sauti.jwt.issuer}") String issuer,
            @Value("${sauti.jwt.secret}") String secret
    ) {
        this.issuer = issuer + ":dashboard-socket";
        this.algorithm = Algorithm.HMAC256(secret);
    }

    public Ticket issue(AuthenticatedUser user) {
        var expiresAt = Instant.now().plus(60, ChronoUnit.SECONDS);
        var value = JWT.create()
                .withIssuer(issuer)
                .withSubject(user.userId().toString())
                .withClaim("tenant_id", user.tenantId().toString())
                .withClaim("purpose", "dashboard_socket")
                .withIssuedAt(Instant.now())
                .withExpiresAt(expiresAt)
                .sign(algorithm);
        return new Ticket(value, expiresAt);
    }

    public Principal verify(String ticket) {
        var jwt = JWT.require(algorithm)
                .withIssuer(issuer)
                .withClaim("purpose", "dashboard_socket")
                .build()
                .verify(ticket);
        return new Principal(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaim("tenant_id").asString())
        );
    }

    public record Ticket(String ticket, Instant expiresAt) { }
    public record Principal(UUID userId, UUID tenantId) { }
}
