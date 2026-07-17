package com.sauti.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.agent.AgentVariableService;
import com.sauti.tool.AgentToolRepository;
import com.sauti.tool.CalendarCredential;
import com.sauti.tool.CalendarCredentialRepository;
import com.sauti.tool.CredentialEncryption;
import com.sauti.integration.IntegrationService;
import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarIntegrationService {
    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final long STATE_TTL_SECONDS = 600;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final AgentRepository agentRepository;
    private final TenantRepository tenantRepository;
    private final CalendarCredentialRepository credentialRepository;
    private final AgentToolRepository toolRepository;
    private final AgentVariableService variableService;
    private final CredentialEncryption encryption;
    private final IntegrationService integrationService;
    private final GoogleCalendarApiClient apiClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final byte[] stateSecret;

    public GoogleCalendarIntegrationService(
            ObjectMapper objectMapper,
            AgentRepository agentRepository,
            TenantRepository tenantRepository,
            CalendarCredentialRepository credentialRepository,
            AgentToolRepository toolRepository,
            AgentVariableService variableService,
            CredentialEncryption encryption,
            IntegrationService integrationService,
            GoogleCalendarApiClient apiClient,
            @Value("${sauti.calendar.google.client-id:}") String clientId,
            @Value("${sauti.calendar.google.client-secret:}") String clientSecret,
            @Value("${sauti.calendar.google.redirect-uri:}") String redirectUri,
            @Value("${sauti.jwt.secret}") String stateSecret
    ) {
        this.objectMapper = objectMapper;
        this.agentRepository = agentRepository;
        this.tenantRepository = tenantRepository;
        this.credentialRepository = credentialRepository;
        this.toolRepository = toolRepository;
        this.variableService = variableService;
        this.encryption = encryption;
        this.integrationService = integrationService;
        this.apiClient = apiClient;
        this.clientId = blank(clientId);
        this.clientSecret = blank(clientSecret);
        this.redirectUri = blank(redirectUri);
        this.stateSecret = blank(stateSecret).getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public String authorizationUrl(UUID tenantId, UUID agentId) {
        ensureConfigured();
        agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        return AUTHORIZE_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode("https://www.googleapis.com/auth/calendar.events https://www.googleapis.com/auth/calendar.freebusy")
                + "&access_type=offline"
                + "&include_granted_scopes=true"
                + "&prompt=consent"
                + "&state=" + encode(createState(tenantId, agentId));
    }

    @Transactional
    public UUID complete(String code, String state) {
        ensureConfigured();
        var context = verifyState(state);
        var tenant = tenantRepository.findById(context.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        var agent = agentRepository.findByIdAndTenantId(context.agentId(), context.tenantId())
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        var token = exchange(code);
        var existing = credentialRepository
                .findAllByTenant_IdAndProviderOrderByCreatedAtDesc(context.tenantId(), "google")
                .stream()
                .findFirst()
                .orElse(null);
        CalendarCredential credential;
        if (existing == null) {
            credential = new CalendarCredential(
                    tenant,
                    "google",
                    encryption.encrypt(token.accessToken()),
                    encryption.encrypt(token.refreshToken()),
                    OffsetDateTime.now().plusSeconds(token.expiresIn()),
                    "primary"
            );
        } else {
            existing.updateTokens(
                    encryption.encrypt(token.accessToken()),
                    token.refreshToken().isBlank() ? null : encryption.encrypt(token.refreshToken()),
                    OffsetDateTime.now().plusSeconds(token.expiresIn())
            );
            existing.selectCalendar("primary");
            credential = existing;
        }
        credentialRepository.save(credential);
        integrationService.connectOAuth(context.tenantId(), agent.getId(), "google_calendar", java.util.Map.of(
                "accessToken", token.accessToken(),
                "refreshToken", token.refreshToken()
        ), java.util.Map.of("calendarId", "primary"));
        toolRepository.findByAgent_IdOrderByDisplayOrderAsc(agent.getId()).stream()
                .filter(tool -> Set.of("check_availability", "book_slot", "reschedule_booking", "cancel_booking")
                        .contains(tool.getToolName()))
                .forEach(tool -> tool.connectCalendar("google", credential.getId()));
        agent.updateCalendarProvider("Google Calendar");
        variableService.updateIfPresent(agent.getId(), "calendar_provider", "Google Calendar");
        return agent.getId();
    }

    @Transactional(readOnly = true)
    public Status status(UUID tenantId, UUID agentId) {
        var agent = agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        var tool = toolRepository.findByAgent_IdOrderByDisplayOrderAsc(agentId).stream()
                .filter(candidate -> "google".equals(candidate.getCalendarType())
                        && candidate.getCalendarCredentialId() != null)
                .findFirst()
                .orElse(null);
        return new Status(
                isConfigured(),
                tool != null,
                agentId,
                tool == null ? null : tool.getCalendarCredentialId(),
                tool == null ? null : "primary"
        );
    }

    @Transactional
    public Status selectCalendar(UUID tenantId, UUID agentId, String calendarId) {
        var normalized = calendarId == null || calendarId.isBlank() ? "primary" : calendarId.trim();
        var tool = connectedTool(tenantId, agentId);
        var credential = credentialRepository.findByIdAndTenant_Id(tool.getCalendarCredentialId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Calendar credential not found"));
        credential.selectCalendar(normalized);
        credentialRepository.save(credential);
        apiClient.test(credential, tool.getAgent().getTimezone());
        return status(tenantId, agentId);
    }

    @Transactional(readOnly = true)
    public void test(UUID tenantId, UUID agentId) {
        var tool = connectedTool(tenantId, agentId);
        var credential = credentialRepository.findByIdAndTenant_Id(tool.getCalendarCredentialId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Calendar credential not found"));
        apiClient.test(credential, tool.getAgent().getTimezone());
    }

    private com.sauti.tool.AgentTool connectedTool(UUID tenantId, UUID agentId) {
        agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        return toolRepository.findByAgent_IdOrderByDisplayOrderAsc(agentId).stream()
                .filter(tool -> "google".equals(tool.getCalendarType()) && tool.getCalendarCredentialId() != null)
                .findFirst().orElseThrow(() -> new IllegalStateException("Google Calendar is not connected for this agent"));
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    private TokenResponse exchange(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Google authorization code is required");
        var body = "code=" + encode(code)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(redirectUri)
                + "&grant_type=authorization_code";
        try {
            var response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(TOKEN_URL))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() / 100 != 2) {
                throw new IllegalArgumentException("Google Calendar authorization failed");
            }
            var node = objectMapper.readTree(response.body());
            return new TokenResponse(
                    node.path("access_token").asText(""),
                    node.path("refresh_token").asText(""),
                    node.path("expires_in").asLong(3600)
            );
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Google Calendar authorization response could not be read", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Calendar authorization was interrupted", exception);
        }
    }

    private String createState(UUID tenantId, UUID agentId) {
        try {
            var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(
                    new StatePayload(OffsetDateTime.now().toEpochSecond(), tenantId, agentId, UUID.randomUUID())
            ));
            return payload + "." + sign(payload);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Google Calendar OAuth state could not be created", exception);
        }
    }

    private StatePayload verifyState(String state) {
        if (state == null || state.isBlank()) throw new IllegalArgumentException("Google Calendar OAuth state is required");
        var parts = state.split("\\.", 2);
        if (parts.length != 2 || !MessageDigest.isEqual(
                sign(parts[0]).getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("Google Calendar OAuth state is invalid");
        }
        try {
            var payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), StatePayload.class);
            if (OffsetDateTime.now().toEpochSecond() - payload.issuedAt() > STATE_TTL_SECONDS) {
                throw new IllegalArgumentException("Google Calendar OAuth state has expired");
            }
            return payload;
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Google Calendar OAuth state is invalid", exception);
        }
    }

    private String sign(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Google Calendar OAuth state could not be signed", exception);
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) throw new IllegalStateException("Google Calendar OAuth is not configured");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    public record Status(boolean configured, boolean connected, UUID agentId, UUID credentialId, String calendarId) {
    }

    private record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
    }

    private record StatePayload(long issuedAt, UUID tenantId, UUID agentId, UUID nonce) {
    }
}
