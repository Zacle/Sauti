package com.sauti.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProviderOAuthService {
    private static final long STATE_TTL_SECONDS = 600;
    private final ObjectMapper objectMapper;
    private final AgentRepository agents;
    private final IntegrationService integrations;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final byte[] stateSecret;
    private final Map<String, Provider> providers;

    public ProviderOAuthService(
            ObjectMapper objectMapper,
            AgentRepository agents,
            IntegrationService integrations,
            @Value("${sauti.jwt.secret}") String stateSecret,
            @Value("${sauti.integrations.google-sheets.client-id:}") String sheetsClientId,
            @Value("${sauti.integrations.google-sheets.client-secret:}") String sheetsClientSecret,
            @Value("${sauti.integrations.google-sheets.redirect-uri:}") String sheetsRedirectUri,
            @Value("${sauti.integrations.hubspot.client-id:}") String hubspotClientId,
            @Value("${sauti.integrations.hubspot.client-secret:}") String hubspotClientSecret,
            @Value("${sauti.integrations.hubspot.redirect-uri:}") String hubspotRedirectUri,
            @Value("${sauti.integrations.salesforce.client-id:}") String salesforceClientId,
            @Value("${sauti.integrations.salesforce.client-secret:}") String salesforceClientSecret,
            @Value("${sauti.integrations.salesforce.redirect-uri:}") String salesforceRedirectUri,
            @Value("${sauti.integrations.cal-com.client-id:}") String calComClientId,
            @Value("${sauti.integrations.cal-com.client-secret:}") String calComClientSecret,
            @Value("${sauti.integrations.cal-com.redirect-uri:}") String calComRedirectUri,
            @Value("${sauti.integrations.cal-com.authorize-url:}") String calComAuthorizeUrl,
            @Value("${sauti.integrations.cal-com.token-url:}") String calComTokenUrl,
            @Value("${sauti.integrations.cal-com.scope:}") String calComScope,
            @Value("${sauti.integrations.calendly.client-id:}") String calendlyClientId,
            @Value("${sauti.integrations.calendly.client-secret:}") String calendlyClientSecret,
            @Value("${sauti.integrations.calendly.redirect-uri:}") String calendlyRedirectUri,
            @Value("${sauti.integrations.calendly.authorize-url:https://auth.calendly.com/oauth/authorize}") String calendlyAuthorizeUrl,
            @Value("${sauti.integrations.calendly.token-url:https://auth.calendly.com/oauth/token}") String calendlyTokenUrl,
            @Value("${sauti.integrations.calendly.scope:}") String calendlyScope
    ) {
        this.objectMapper = objectMapper;
        this.agents = agents;
        this.integrations = integrations;
        this.stateSecret = stateSecret.getBytes(StandardCharsets.UTF_8);
        this.providers = Map.of(
                "google_sheets", new Provider(sheetsClientId, sheetsClientSecret, sheetsRedirectUri,
                        "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
                        "https://www.googleapis.com/auth/spreadsheets", true),
                "hubspot", new Provider(hubspotClientId, hubspotClientSecret, hubspotRedirectUri,
                        "https://app.hubspot.com/oauth/authorize", "https://api.hubapi.com/oauth/v1/token",
                        "crm.objects.contacts.read crm.objects.contacts.write crm.objects.notes.write", false),
                "salesforce", new Provider(salesforceClientId, salesforceClientSecret, salesforceRedirectUri,
                        "https://login.salesforce.com/services/oauth2/authorize",
                        "https://login.salesforce.com/services/oauth2/token",
                        "api refresh_token", false),
                "cal_com", new Provider(calComClientId, calComClientSecret, calComRedirectUri,
                        calComAuthorizeUrl, calComTokenUrl, calComScope, false),
                "calendly", new Provider(calendlyClientId, calendlyClientSecret, calendlyRedirectUri,
                        calendlyAuthorizeUrl, calendlyTokenUrl, calendlyScope, false)
        );
    }

    public String authorizationUrl(UUID tenantId, UUID agentId, String providerName) {
        agents.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found"));
        var provider = requireConfigured(providerName);
        return provider.authorizeUrl() + "?client_id=" + encode(provider.clientId())
                + "&redirect_uri=" + encode(provider.redirectUri())
                + "&response_type=code"
                + (provider.scope().isBlank() ? "" : "&scope=" + encode(provider.scope()))
                + (provider.google() ? "&access_type=offline&prompt=consent" : "")
                + "&state=" + encode(state(providerName, tenantId, agentId));
    }

    public UUID complete(String providerName, String code, String state) {
        var provider = requireConfigured(providerName);
        var context = verifyState(state, providerName);
        try {
            var body = "grant_type=authorization_code&code=" + encode(code)
                    + "&client_id=" + encode(provider.clientId())
                    + "&client_secret=" + encode(provider.clientSecret())
                    + "&redirect_uri=" + encode(provider.redirectUri());
            var response = httpClient.send(HttpRequest.newBuilder(URI.create(provider.tokenUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalArgumentException("Provider authorization failed with HTTP " + response.statusCode());
            }
            var node = objectMapper.readTree(response.body());
            var credentials = new LinkedHashMap<String, Object>();
            credentials.put("accessToken", node.path("access_token").asText());
            credentials.put("refreshToken", node.path("refresh_token").asText(""));
            credentials.put("expiresIn", node.path("expires_in").asLong(3600));
            credentials.put("grantedAt", OffsetDateTime.now().toEpochSecond());
            credentials.put("tokenType", node.path("token_type").asText("Bearer"));
            if (node.hasNonNull("instance_url")) credentials.put("instanceUrl", node.get("instance_url").asText());
            integrations.connectOAuth(context.tenantId(), context.agentId(), providerName, credentials, Map.of());
            return context.agentId();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider authorization was interrupted", exception);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Provider authorization response could not be read", exception);
        }
    }

    public boolean configured(String providerName) {
        var provider = providers.get(providerName);
        return provider != null && !provider.clientId().isBlank()
                && !provider.clientSecret().isBlank() && !provider.redirectUri().isBlank()
                && !provider.authorizeUrl().isBlank() && !provider.tokenUrl().isBlank();
    }

    public synchronized String accessToken(UUID tenantId, UUID agentId, String providerName) {
        var provider = requireConfigured(providerName);
        var runtime = integrations.runtime(tenantId, agentId, providerName);
        var credentials = runtime.credentials();
        var accessToken = text(credentials.get("accessToken"));
        var refreshToken = text(credentials.get("refreshToken"));
        var grantedAt = number(credentials.get("grantedAt"), 0);
        var expiresIn = number(credentials.get("expiresIn"), 3600);
        var now = OffsetDateTime.now().toEpochSecond();
        if (!accessToken.isBlank() && grantedAt > 0 && now < grantedAt + expiresIn - 300) {
            return accessToken;
        }
        if (refreshToken.isBlank()) {
            throw new IllegalStateException(providerName + " authorization has expired; reconnect the provider");
        }
        try {
            var body = "grant_type=refresh_token"
                    + "&refresh_token=" + encode(refreshToken)
                    + "&client_id=" + encode(provider.clientId())
                    + "&client_secret=" + encode(provider.clientSecret());
            var response = httpClient.send(HttpRequest.newBuilder(URI.create(provider.tokenUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(providerName + " token refresh failed with HTTP " + response.statusCode());
            }
            var node = objectMapper.readTree(response.body());
            var refreshedToken = node.path("access_token").asText("");
            if (refreshedToken.isBlank()) {
                throw new IllegalStateException(providerName + " token refresh returned no access token");
            }
            var updates = new LinkedHashMap<String, Object>();
            updates.put("accessToken", refreshedToken);
            updates.put("expiresIn", node.path("expires_in").asLong(expiresIn));
            updates.put("grantedAt", now);
            updates.put("tokenType", node.path("token_type").asText(text(credentials.get("tokenType"))));
            if (node.hasNonNull("refresh_token")) updates.put("refreshToken", node.get("refresh_token").asText());
            if (node.hasNonNull("instance_url")) updates.put("instanceUrl", node.get("instance_url").asText());
            integrations.updateOAuthCredentials(tenantId, runtime.connectionId(), updates);
            return refreshedToken;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(providerName + " token refresh was interrupted", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(providerName + " token refresh failed", exception);
        }
    }

    private Provider requireConfigured(String providerName) {
        var provider = providers.get(providerName);
        if (provider == null) throw new IllegalArgumentException("Provider does not support OAuth");
        if (!configured(providerName)) throw new IllegalStateException(providerName + " OAuth is not configured");
        return provider;
    }

    private String state(String provider, UUID tenantId, UUID agentId) {
        try {
            var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(
                    new State(OffsetDateTime.now().toEpochSecond(), provider, tenantId, agentId, UUID.randomUUID())));
            return payload + "." + sign(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("OAuth state could not be created", exception);
        }
    }

    private State verifyState(String state, String expectedProvider) {
        if (state == null) throw new IllegalArgumentException("OAuth state is required");
        var parts = state.split("\\.", 2);
        if (parts.length != 2 || !MessageDigest.isEqual(
                sign(parts[0]).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("OAuth state is invalid");
        }
        try {
            var value = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), State.class);
            if (!expectedProvider.equals(value.provider())
                    || OffsetDateTime.now().toEpochSecond() - value.issuedAt() > STATE_TTL_SECONDS) {
                throw new IllegalArgumentException("OAuth state is invalid or expired");
            }
            return value;
        } catch (Exception exception) {
            throw new IllegalArgumentException("OAuth state is invalid", exception);
        }
    }

    private String sign(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("OAuth state could not be signed", exception); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(text(value)); } catch (Exception ignored) { return fallback; }
    }
    private record Provider(String clientId, String clientSecret, String redirectUri,
                            String authorizeUrl, String tokenUrl, String scope, boolean google) {}
    private record State(long issuedAt, String provider, UUID tenantId, UUID agentId, UUID nonce) {}
}
