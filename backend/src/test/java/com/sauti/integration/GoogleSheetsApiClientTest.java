package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GoogleSheetsApiClientTest {
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void retriesOnceWithARefreshedTokenAfterAnEarlyUnauthorizedResponse() throws Exception {
        var requests = new AtomicInteger();
        var server = server(exchange -> {
            var attempt = requests.incrementAndGet();
            var authorized = "Bearer fresh-token".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            var status = attempt == 1 ? 401 : authorized ? 200 : 403;
            var body = status == 200 ? "{\"values\":[[\"customer-1\",\"active\"]]}" : "{\"error\":{}}";
            exchange.sendResponseHeaders(status, body.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        var oauth = mock(ProviderOAuthService.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(oauth.accessToken(tenantId, agentId, "google_sheets")).thenReturn("stale-token");
        when(oauth.refreshAccessToken(tenantId, agentId, "google_sheets")).thenReturn("fresh-token");
        var client = client(server, oauth);

        var response = client.values(tenantId, agentId, "sheet-1", "Calls!A:B");

        assertThat(response.path("values").path(0).path(0).asText()).isEqualTo("customer-1");
        assertThat(requests).hasValue(2);
        verify(oauth).refreshAccessToken(tenantId, agentId, "google_sheets");
    }

    @Test
    void appendsRowsWithTheDocumentedPostOperation() throws Exception {
        var methods = new ArrayList<String>();
        var paths = new ArrayList<String>();
        var bodies = new ArrayList<String>();
        var server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var body = "{\"updates\":{\"updatedRows\":1}}";
            exchange.sendResponseHeaders(200, body.length());
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        var oauth = mock(ProviderOAuthService.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(oauth.accessToken(tenantId, agentId, "google_sheets")).thenReturn("token");
        var client = client(server, oauth);

        var status = client.appendValues(
                tenantId, agentId, "sheet-1", "Calls!A:E", List.of("now", "+2011", "booked")
        );

        assertThat(status).isEqualTo(200);
        assertThat(methods).containsExactly("POST");
        assertThat(paths.get(0)).contains("/spreadsheets/sheet-1/values/Calls%21A%3AE:append")
                .contains("valueInputOption=USER_ENTERED", "insertDataOption=INSERT_ROWS");
        assertThat(bodies.get(0)).isEqualTo("{\"values\":[[\"now\",\"+2011\",\"booked\"]]}");
    }

    @Test
    void createsOnlyMissingTabsAndDoesNotReplaceExistingHeaders() throws Exception {
        var methods = new ArrayList<String>();
        var paths = new ArrayList<String>();
        var bodies = new ArrayList<String>();
        var server = server(exchange -> {
            var method = exchange.getRequestMethod();
            var path = exchange.getRequestURI().getRawPath();
            methods.add(method);
            paths.add(path);
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var body = switch (method + " " + path) {
                case "GET /v4/spreadsheets/sheet-1" ->
                        "{\"sheets\":[{\"properties\":{\"title\":\"Customers\"}}]}";
                case "GET /v4/spreadsheets/sheet-1/values/Customers%211%3A1" ->
                        "{\"values\":[[\"Phone\",\"Full name\",\"Email\"]]}";
                case "GET /v4/spreadsheets/sheet-1/values/Calls%211%3A1" -> "{}";
                default -> "{}";
            };
            exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        var oauth = mock(ProviderOAuthService.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(oauth.accessToken(tenantId, agentId, "google_sheets")).thenReturn("token");
        var client = client(server, oauth);

        var result = client.initialize(tenantId, agentId, "sheet-1");

        assertThat(result.createdTabs()).containsExactly("Calls");
        assertThat(result.initializedHeaders()).containsExactly("Calls");
        assertThat(result.preservedHeaders()).containsExactly("Customers");
        assertThat(paths).contains("/v4/spreadsheets/sheet-1:batchUpdate")
                .contains("/v4/spreadsheets/sheet-1/values/Calls%21A1%3AF1");
        assertThat(methods.stream().filter("PUT"::equals).count()).isEqualTo(1);
        assertThat(bodies.get(paths.indexOf("/v4/spreadsheets/sheet-1:batchUpdate")))
                .contains("\"title\":\"Calls\"")
                .doesNotContain("\"title\":\"Customers\"");
        assertThat(bodies.get(paths.indexOf("/v4/spreadsheets/sheet-1/values/Calls%21A1%3AF1")))
                .isEqualTo("{\"values\":[[\"Call ID\",\"Started At\",\"Caller Phone\",\"Outcome\",\"Summary\",\"Sentiment\"]]}");
    }

    @Test
    void explainsThatAnUnavailableSpreadsheetMustBeSharedWithTheConnectedAccount() throws Exception {
        var server = server(exchange -> {
            var body = "{\"error\":{\"status\":\"NOT_FOUND\"}}";
            exchange.sendResponseHeaders(404, body.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        var oauth = mock(ProviderOAuthService.class);
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(oauth.accessToken(tenantId, agentId, "google_sheets")).thenReturn("token");
        var client = client(server, oauth);

        assertThatThrownBy(() -> client.initialize(tenantId, agentId, "another-account-sheet"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Check its ID")
                .hasMessageContaining("shared with the connected Google account");
    }

    private GoogleSheetsApiClient client(HttpServer server, ProviderOAuthService oauth) {
        return new GoogleSheetsApiClient(
                new ObjectMapper(), oauth, HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v4"
        );
    }

    private HttpServer server(Handler handler) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception exception) {
                exchange.close();
            }
        });
        server.start();
        servers.add(server);
        return server;
    }

    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
    }
}
