package com.sauti.call;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WebVoiceTokenService {
    private final Algorithm algorithm;
    private final long tokenMinutes;

    public WebVoiceTokenService(
            @Value("${sauti.web-voice.token-secret}") String secret,
            @Value("${sauti.web-voice.token-minutes:10}") long tokenMinutes
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.tokenMinutes = tokenMinutes;
    }

    public String issue(String callSid, String publicAgentId) {
        return issue(callSid, publicAgentId, tokenMinutes * 60);
    }

    public String issue(String callSid, String publicAgentId, long minimumLifetimeSeconds) {
        var now = Instant.now();
        var lifetimeSeconds = Math.max(tokenMinutes * 60, minimumLifetimeSeconds);
        return JWT.create()
                .withIssuer("sauti-web-voice")
                .withSubject(callSid)
                .withClaim("agent", publicAgentId)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(lifetimeSeconds, ChronoUnit.SECONDS))
                .sign(algorithm);
    }

    public WebVoicePrincipal verify(String token) {
        var jwt = JWT.require(algorithm).withIssuer("sauti-web-voice").build().verify(token);
        return new WebVoicePrincipal(jwt.getSubject(), jwt.getClaim("agent").asString());
    }

    public record WebVoicePrincipal(String callSid, String publicAgentId) {
    }
}
