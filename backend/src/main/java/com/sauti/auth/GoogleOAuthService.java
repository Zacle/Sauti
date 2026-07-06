package com.sauti.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleOAuthService {
    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";
    private static final long STATE_TTL_SECONDS = 600;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final byte[] stateSecret;

    @Autowired
    public GoogleOAuthService(
            ObjectMapper objectMapper,
            @Value("${sauti.auth.google.client-id:}") String clientId,
            @Value("${sauti.auth.google.client-secret:}") String clientSecret,
            @Value("${sauti.auth.google.redirect-uri:}") String redirectUri,
            @Value("${sauti.jwt.secret}") String stateSecret
    ) {
        this(HttpClient.newHttpClient(), objectMapper, Clock.systemUTC(), clientId, clientSecret, redirectUri, stateSecret);
    }

    GoogleOAuthService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock,
            String clientId,
            String clientSecret,
            String redirectUri,
            String stateSecret
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.clientId = nullToBlank(clientId);
        this.clientSecret = nullToBlank(clientSecret);
        this.redirectUri = nullToBlank(redirectUri);
        this.stateSecret = nullToBlank(stateSecret).getBytes(StandardCharsets.UTF_8);
    }

    public String authorizationUrl() {
        return authorizationUrl("", "", "/dashboard");
    }

    public String authorizationUrl(String businessName, String countryCode, String returnPath) {
        ensureRedirectConfigured();
        return AUTHORIZE_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode("openid email profile")
                + "&prompt=select_account"
                + "&state=" + encode(createState(new GoogleAuthContext(
                        nullToBlank(businessName),
                        nullToBlank(countryCode),
                        allowedReturnPath(returnPath)
                )));
    }

    public GoogleProfile exchangeCode(String code, String state) {
        return exchangeCodeWithContext(code, state).profile();
    }

    public GoogleAuthResult exchangeCodeWithContext(String code, String state) {
        ensureRedirectConfigured();
        var context = verifyState(state);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Google authorization code is required");
        }
        var body = "code=" + encode(code)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(redirectUri)
                + "&grant_type=authorization_code";
        var request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalArgumentException("Google authorization code could not be exchanged");
            }
            var idToken = objectMapper.readTree(response.body()).path("id_token").asText("");
            return new GoogleAuthResult(verifyIdToken(idToken), context);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Google authorization response could not be read", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google authorization was interrupted", exception);
        }
    }

    public GoogleProfile verifyIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Google ID token is required");
        }
        var request = HttpRequest.newBuilder(URI.create(TOKEN_INFO_URL + encode(idToken))).GET().build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalArgumentException("Google ID token is invalid");
            }
            return parseProfile(objectMapper.readTree(response.body()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Google ID token could not be verified", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google ID token verification was interrupted", exception);
        }
    }

    private GoogleProfile parseProfile(JsonNode node) {
        var audience = node.path("aud").asText("");
        if (!clientId.isBlank() && !clientId.equals(audience)) {
            throw new IllegalArgumentException("Google ID token audience is not allowed");
        }
        if (!node.path("email_verified").asBoolean(false)) {
            throw new IllegalArgumentException("Google account email is not verified");
        }
        var email = node.path("email").asText("").toLowerCase();
        if (email.isBlank()) {
            throw new IllegalArgumentException("Google account email is required");
        }
        return new GoogleProfile(email, node.path("name").asText(""));
    }

    private String createState(GoogleAuthContext context) {
        try {
            var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(
                    new StatePayload(
                            Instant.now(clock).getEpochSecond(),
                            UUID.randomUUID().toString(),
                            context.businessName(),
                            context.countryCode(),
                            context.returnPath()
                    )
            ));
            return payload + "." + sign(payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Google OAuth state could not be created", exception);
        }
    }

    private GoogleAuthContext verifyState(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Google OAuth state is required");
        }
        var parts = state.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Google OAuth state is invalid");
        }
        var payload = parts[0];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Google OAuth state signature is invalid");
        }
        try {
            var decoded = Base64.getUrlDecoder().decode(payload);
            var statePayload = objectMapper.readValue(decoded, StatePayload.class);
            if (Instant.now(clock).getEpochSecond() - statePayload.issuedAt() > STATE_TTL_SECONDS) {
                throw new IllegalArgumentException("Google OAuth state has expired");
            }
            return new GoogleAuthContext(
                    statePayload.businessName(),
                    statePayload.countryCode(),
                    allowedReturnPath(statePayload.returnPath())
            );
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException argumentException) throw argumentException;
            throw new IllegalArgumentException("Google OAuth state is invalid", exception);
        }
    }

    private String sign(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Google OAuth state could not be signed", exception);
        }
    }

    private void ensureRedirectConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) {
            throw new IllegalStateException("Google OAuth redirect flow is not configured");
        }
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    private String allowedReturnPath(String path) {
        return "/onboarding".equals(path) ? "/onboarding" : "/dashboard";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    public record GoogleProfile(String email, String name) {
    }

    public record GoogleAuthContext(String businessName, String countryCode, String returnPath) {
    }

    public record GoogleAuthResult(GoogleProfile profile, GoogleAuthContext context) {
    }

    private record StatePayload(
            long issuedAt,
            String nonce,
            String businessName,
            String countryCode,
            String returnPath
    ) {
    }
}
