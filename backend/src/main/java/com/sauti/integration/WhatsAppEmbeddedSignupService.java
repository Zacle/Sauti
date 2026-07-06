package com.sauti.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppEmbeddedSignupService {
    private final ObjectMapper objectMapper;
    private final IntegrationService integrations;
    private final IntegrationConnectionRepository connections;
    private final AgentRepository agents;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String appId;
    private final String appSecret;
    private final String configurationId;
    private final String graphBaseUrl;
    private final String graphVersion;

    public WhatsAppEmbeddedSignupService(
            ObjectMapper objectMapper,
            IntegrationService integrations,
            IntegrationConnectionRepository connections,
            AgentRepository agents,
            @Value("${sauti.integrations.whatsapp.app-id:}") String appId,
            @Value("${sauti.integrations.whatsapp.app-secret:}") String appSecret,
            @Value("${sauti.integrations.whatsapp.configuration-id:}") String configurationId,
            @Value("${sauti.whatsapp.graph-api-base-url:https://graph.facebook.com/v23.0}") String graphBaseUrl
    ) {
        this.objectMapper = objectMapper;
        this.integrations = integrations;
        this.connections = connections;
        this.agents = agents;
        this.appId = trim(appId);
        this.appSecret = trim(appSecret);
        this.configurationId = trim(configurationId);
        this.graphBaseUrl = graphBaseUrl.replaceFirst("/+$", "");
        var path = URI.create(this.graphBaseUrl).getPath();
        this.graphVersion = path == null || path.isBlank() ? "v23.0" : path.replace("/", "");
    }

    public SignupConfiguration configuration() {
        return new SignupConfiguration(configured(), appId, configurationId, graphVersion);
    }

    public boolean configured() {
        return !appId.isBlank() && !appSecret.isBlank() && !configurationId.isBlank();
    }

    @Transactional
    public ConnectionResult complete(UUID tenantId, CompleteRequest request) {
        ensureConfigured();
        var agent = agents.findByIdAndTenantId(request.agentId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found"));
        if (blank(request.code())) throw new IllegalArgumentException("Meta authorization code is required");
        if (blank(request.wabaId())) throw new IllegalArgumentException("Meta did not return a WhatsApp Business Account");

        var accessToken = exchangeCode(request.code());
        var waba = get("/" + encodePath(request.wabaId())
                + "?fields=id,name,account_review_status,business_verification_status", accessToken);
        if (!request.wabaId().equals(waba.path("id").asText())) {
            throw new IllegalArgumentException("Meta returned an unexpected WhatsApp Business Account");
        }
        var phone = selectPhone(request.wabaId(), request.phoneNumberId(), accessToken);
        subscribeApp(request.wabaId(), accessToken);
        var templates = templates(request.wabaId(), accessToken);
        var selectedTemplate = templates.isEmpty() ? null : templates.get(0);

        var configuration = new LinkedHashMap<String, Object>();
        configuration.put("wabaId", request.wabaId());
        configuration.put("wabaName", waba.path("name").asText(""));
        configuration.put("phoneNumberId", phone.id());
        configuration.put("displayPhoneNumber", phone.displayPhoneNumber());
        configuration.put("verifiedName", phone.verifiedName());
        configuration.put("qualityRating", phone.qualityRating());
        if (selectedTemplate != null) {
            configuration.put("templateName", selectedTemplate.name());
            configuration.put("templateLanguage", selectedTemplate.language());
        }
        integrations.connectOAuth(
                tenantId,
                request.agentId(),
                "whatsapp",
                Map.of("accessToken", accessToken),
                configuration
        );
        agent.configureWhatsApp(true, phone.id());
        agents.save(agent);
        var connection = connections.findFirstByTenantIdAndProviderOrderByCreatedAtDesc(tenantId, "whatsapp")
                .orElseThrow(() -> new IllegalStateException("WhatsApp connection was not saved"));
        return new ConnectionResult(connection.getId(), request.wabaId(), phone.id(),
                phone.displayPhoneNumber(), phone.verifiedName(), templates);
    }

    @Transactional(readOnly = true)
    public List<TemplateOption> templates(UUID tenantId, UUID connectionId) {
        var connection = connections.findByIdAndTenantId(connectionId, tenantId)
                .filter(item -> "whatsapp".equals(item.getProvider()))
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp connection not found"));
        var config = parseConfiguration(connection.getConfiguration());
        var wabaId = String.valueOf(config.getOrDefault("wabaId", ""));
        if (wabaId.isBlank()) throw new IllegalStateException("WhatsApp connection has no WABA ID");
        var token = String.valueOf(integrations.credentials(connection).getOrDefault("accessToken", ""));
        if (token.isBlank()) throw new IllegalStateException("WhatsApp connection has no access token");
        return templates(wabaId, token);
    }

    private String exchangeCode(String code) {
        var uri = graphBaseUrl + "/oauth/access_token"
                + "?client_id=" + encodeQuery(appId)
                + "&client_secret=" + encodeQuery(appSecret)
                + "&code=" + encodeQuery(code);
        var response = get(uri, null, true);
        var token = response.path("access_token").asText("");
        if (token.isBlank()) throw new IllegalStateException("Meta returned no WhatsApp access token");
        return token;
    }

    private PhoneOption selectPhone(String wabaId, String requestedPhoneId, String accessToken) {
        var response = get("/" + encodePath(wabaId)
                + "/phone_numbers?fields=id,display_phone_number,verified_name,quality_rating,status&limit=100",
                accessToken);
        var phones = new java.util.ArrayList<PhoneOption>();
        response.withArray("data").forEach(node -> phones.add(new PhoneOption(
                node.path("id").asText(""),
                node.path("display_phone_number").asText(""),
                node.path("verified_name").asText(""),
                node.path("quality_rating").asText("UNKNOWN"),
                node.path("status").asText("")
        )));
        if (!blank(requestedPhoneId)) {
            return phones.stream().filter(phone -> requestedPhoneId.equals(phone.id())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The selected phone number does not belong to the connected WABA"));
        }
        if (phones.size() == 1) return phones.get(0);
        if (phones.isEmpty()) throw new IllegalArgumentException("The connected WABA has no phone numbers");
        throw new IllegalArgumentException("Meta returned multiple phone numbers but no selection");
    }

    private List<TemplateOption> templates(String wabaId, String accessToken) {
        var response = get("/" + encodePath(wabaId)
                + "/message_templates?fields=id,name,language,status,category&limit=100", accessToken);
        var templates = new java.util.ArrayList<TemplateOption>();
        response.withArray("data").forEach(node -> {
            if ("APPROVED".equalsIgnoreCase(node.path("status").asText())) {
                templates.add(new TemplateOption(
                        node.path("id").asText(""),
                        node.path("name").asText(""),
                        node.path("language").asText(""),
                        node.path("category").asText("")
                ));
            }
        });
        return templates.stream()
                .sorted(java.util.Comparator.comparing(TemplateOption::name)
                        .thenComparing(TemplateOption::language))
                .toList();
    }

    private void subscribeApp(String wabaId, String accessToken) {
        request("POST", "/" + encodePath(wabaId) + "/subscribed_apps", accessToken);
    }

    private JsonNode get(String pathOrUrl, String accessToken) {
        return get(pathOrUrl, accessToken, false);
    }

    private JsonNode get(String pathOrUrl, String accessToken, boolean absolute) {
        var url = absolute ? pathOrUrl : graphBaseUrl + pathOrUrl;
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET();
        if (!blank(accessToken)) builder.header("Authorization", "Bearer " + accessToken);
        return send(builder.build());
    }

    private JsonNode request(String method, String path, String accessToken) {
        return send(HttpRequest.newBuilder(URI.create(graphBaseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build());
    }

    private JsonNode send(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Meta Graph API returned HTTP " + response.statusCode()
                        + ": " + safeError(response.body()));
            }
            return response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Meta Graph API request was interrupted", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Meta Graph API request failed", exception);
        }
    }

    private Map<String, Object> parseConfiguration(String value) {
        try {
            return objectMapper.readValue(value == null || value.isBlank() ? "{}" : value,
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Stored WhatsApp configuration is invalid", exception);
        }
    }

    private void ensureConfigured() {
        if (!configured()) throw new IllegalStateException("Meta WhatsApp Embedded Signup is not configured");
    }

    private static String safeError(String body) {
        if (body == null) return "";
        return body.length() <= 500 ? body : body.substring(0, 500);
    }
    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }

    public record SignupConfiguration(boolean configured, String appId, String configurationId, String graphVersion) {}
    public record CompleteRequest(UUID agentId, String code, String wabaId, String phoneNumberId) {}
    public record ConnectionResult(UUID connectionId, String wabaId, String phoneNumberId,
                                   String displayPhoneNumber, String verifiedName,
                                   List<TemplateOption> templates) {}
    public record TemplateOption(String id, String name, String language, String category) {}
    private record PhoneOption(String id, String displayPhoneNumber, String verifiedName,
                               String qualityRating, String status) {}
}
