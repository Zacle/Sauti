package com.sauti.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GoogleSheetsApiClient {
    private static final String DEFAULT_BASE_URL = "https://sheets.googleapis.com/v4";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final List<TabTemplate> SAUTI_TABS = List.of(
            new TabTemplate("Customers", List.of("Phone", "Name", "Email")),
            new TabTemplate("Calls", List.of(
                    "Call ID", "Started At", "Caller Phone", "Outcome", "Summary", "Sentiment"
            ))
    );

    private final ObjectMapper objectMapper;
    private final ProviderOAuthService oauth;
    private final HttpClient httpClient;
    private final String baseUrl;

    @Autowired
    public GoogleSheetsApiClient(ObjectMapper objectMapper, ProviderOAuthService oauth) {
        this(objectMapper, oauth, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                DEFAULT_BASE_URL);
    }

    GoogleSheetsApiClient(
            ObjectMapper objectMapper,
            ProviderOAuthService oauth,
            HttpClient httpClient,
            String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.oauth = oauth;
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.replaceFirst("/+$", "");
    }

    public JsonNode values(UUID tenantId, UUID agentId, String spreadsheetId, String range) {
        var endpoint = valuesEndpoint(spreadsheetId, range) + "?majorDimension=ROWS";
        return responseJson(sendAuthorized(tenantId, agentId, token -> HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build()));
    }

    public int updateValues(
            UUID tenantId,
            UUID agentId,
            String spreadsheetId,
            String range,
            List<?> values
    ) {
        var endpoint = valuesEndpoint(spreadsheetId, range) + "?valueInputOption=USER_ENTERED";
        return sendAuthorized(tenantId, agentId, token -> jsonRequest(endpoint, token, "PUT", values)).statusCode();
    }

    public int appendValues(
            UUID tenantId,
            UUID agentId,
            String spreadsheetId,
            String range,
            List<?> values
    ) {
        var endpoint = valuesEndpoint(spreadsheetId, range)
                + ":append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS";
        return sendAuthorized(tenantId, agentId, token -> jsonRequest(endpoint, token, "POST", values)).statusCode();
    }

    public void test(UUID tenantId, UUID agentId, String spreadsheetId, String range) {
        values(tenantId, agentId, spreadsheetId, range);
    }

    public InitializationResult initialize(UUID tenantId, UUID agentId, String spreadsheetId) {
        var existingTabs = spreadsheetTabs(tenantId, agentId, spreadsheetId);
        var createdTabs = new ArrayList<String>();
        SAUTI_TABS.stream().map(TabTemplate::name)
                .filter(tab -> !existingTabs.contains(tab)).forEach(createdTabs::add);
        if (!createdTabs.isEmpty()) {
            var requests = createdTabs.stream()
                    .map(tab -> Map.of("addSheet", Map.of("properties", Map.of("title", tab))))
                    .toList();
            var endpoint = spreadsheetEndpoint(spreadsheetId) + ":batchUpdate";
            sendAuthorized(tenantId, agentId, token -> jsonRequest(
                    endpoint, token, "POST", Map.of("requests", requests)
            ));
        }

        var initializedHeaders = new ArrayList<String>();
        var preservedHeaders = new ArrayList<String>();
        SAUTI_TABS.forEach(template -> {
            var tab = template.name();
            var headers = template.headers();
            var row = values(tenantId, agentId, spreadsheetId, tab + "!1:1").path("values");
            if (row.isEmpty() || rowIsBlank(row.path(0))) {
                updateValues(tenantId, agentId, spreadsheetId,
                        tab + "!A1:" + columnName(headers.size()) + "1", headers);
                initializedHeaders.add(tab);
            } else {
                preservedHeaders.add(tab);
            }
        });
        return new InitializationResult(createdTabs, initializedHeaders, preservedHeaders);
    }

    private Set<String> spreadsheetTabs(UUID tenantId, UUID agentId, String spreadsheetId) {
        var endpoint = spreadsheetEndpoint(spreadsheetId) + "?fields=sheets.properties.title";
        var response = responseJson(sendAuthorized(tenantId, agentId,
                token -> HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build()));
        var titles = new LinkedHashSet<String>();
        response.path("sheets").forEach(sheet -> {
            var title = sheet.path("properties").path("title").asText("");
            if (!title.isBlank()) titles.add(title);
        });
        return titles;
    }

    private HttpRequest jsonRequest(String endpoint, String token, String method, List<?> values) {
        return jsonRequest(endpoint, token, method, Map.of("values", List.of(values)));
    }

    private HttpRequest jsonRequest(String endpoint, String token, String method, Object bodyValue) {
        try {
            var body = objectMapper.writeValueAsBytes(bodyValue);
            return HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Google Sheets request could not be prepared", exception);
        }
    }

    private HttpResponse<String> sendAuthorized(
            UUID tenantId,
            UUID agentId,
            Function<String, HttpRequest> request
    ) {
        var response = send(request.apply(oauth.accessToken(tenantId, agentId, "google_sheets")));
        if (response.statusCode() == 401) {
            response = send(request.apply(oauth.refreshAccessToken(tenantId, agentId, "google_sheets")));
        }
        if (response.statusCode() / 100 != 2) throw providerFailure(response);
        return response;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Sheets request was interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Google Sheets could not be reached", exception);
        }
    }

    private JsonNode responseJson(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException("Google Sheets returned an invalid response", exception);
        }
    }

    private IllegalStateException providerFailure(HttpResponse<String> response) {
        var status = response.statusCode();
        var reason = googleReason(response.body());
        var message = switch (status) {
            case 401 -> "Google Sheets authorization expired; reconnect Google Sheets";
            case 403 -> "The connected Google account cannot access this spreadsheet. Share it with that account, then try again";
            // Google returns 404 rather than disclosing a spreadsheet that the
            // signed-in account is not allowed to see, so cover both cases.
            case 404 -> "Google Sheets could not find this spreadsheet. Check its ID and make sure it is shared with the connected Google account";
            case 429 -> "Google Sheets is temporarily busy; try again shortly";
            default -> "Google Sheets request failed with HTTP " + status;
        };
        return new IllegalStateException(reason.isBlank() ? message : message + " (" + reason + ")");
    }

    private String googleReason(String body) {
        try {
            var error = objectMapper.readTree(body).path("error");
            var reason = error.path("status").asText("");
            if (reason.isBlank() && error.path("details").isArray() && !error.path("details").isEmpty()) {
                reason = error.path("details").get(0).path("reason").asText("");
            }
            return reason.replaceAll("[^A-Za-z0-9_]", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String valuesEndpoint(String spreadsheetId, String range) {
        return spreadsheetEndpoint(spreadsheetId) + "/values/" + encode(range);
    }

    private String spreadsheetEndpoint(String spreadsheetId) {
        return baseUrl + "/spreadsheets/" + encode(spreadsheetId);
    }

    private static String columnName(int oneBasedColumn) {
        var value = oneBasedColumn;
        var result = new StringBuilder();
        while (value > 0) {
            value--;
            result.insert(0, (char) ('A' + (value % 26)));
            value /= 26;
        }
        return result.toString();
    }

    private static boolean rowIsBlank(JsonNode row) {
        if (!row.isArray() || row.isEmpty()) return true;
        for (var cell : row) {
            if (!cell.asText("").isBlank()) return false;
        }
        return true;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record InitializationResult(
            List<String> createdTabs,
            List<String> initializedHeaders,
            List<String> preservedHeaders
    ) {}

    private record TabTemplate(String name, List<String> headers) {}
}
