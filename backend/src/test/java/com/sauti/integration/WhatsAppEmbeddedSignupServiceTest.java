package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.integration.WhatsAppEmbeddedSignupService.CompleteRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WhatsAppEmbeddedSignupServiceTest {
    @Test
    void exchangesCodeDiscoversAssetsSubscribesAndStoresEncryptedConnectionInput() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var subscribed = new java.util.concurrent.atomic.AtomicBoolean();
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            var body = switch (path) {
                case "/v23.0/oauth/access_token" -> "{\"access_token\":\"workspace-token\"}";
                case "/v23.0/waba-1/phone_numbers" -> """
                        {"data":[{"id":"phone-1","display_phone_number":"+27 11 555 0100",
                        "verified_name":"Demo Clinic","quality_rating":"GREEN","status":"CONNECTED"}]}
                        """;
                case "/v23.0/waba-1/message_templates" -> """
                        {"data":[
                          {"id":"tpl-1","name":"appointment_reminder","language":"en_US","status":"APPROVED","category":"UTILITY"},
                          {"id":"tpl-2","name":"draft_template","language":"en_US","status":"PENDING","category":"UTILITY"}
                        ]}
                        """;
                case "/v23.0/waba-1/subscribed_apps" -> {
                    subscribed.set("POST".equals(exchange.getRequestMethod()));
                    yield "{}";
                }
                default -> "{\"id\":\"waba-1\",\"name\":\"Demo Clinic WABA\"}";
            };
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var integrations = mock(IntegrationService.class);
            var connections = mock(IntegrationConnectionRepository.class);
            var agents = mock(AgentRepository.class);
            var agent = mock(Agent.class);
            var tenantId = UUID.randomUUID();
            var agentId = UUID.randomUUID();
            var connection = new IntegrationConnection(
                    tenantId, "whatsapp", "WhatsApp", null, "{}");
            when(agents.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(agent));
            when(connections.findFirstByTenantIdAndProviderOrderByCreatedAtDesc(tenantId, "whatsapp"))
                    .thenReturn(Optional.of(connection));

            var service = new WhatsAppEmbeddedSignupService(
                    new ObjectMapper(), integrations, connections, agents,
                    "app-1", "app-secret", "config-1",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v23.0");
            var result = service.complete(tenantId,
                    new CompleteRequest(agentId, "temporary-code", "waba-1", "phone-1"));

            assertThat(result.phoneNumberId()).isEqualTo("phone-1");
            assertThat(result.verifiedName()).isEqualTo("Demo Clinic");
            assertThat(result.templates()).singleElement().satisfies(template -> {
                assertThat(template.name()).isEqualTo("appointment_reminder");
                assertThat(template.language()).isEqualTo("en_US");
            });
            assertThat(subscribed).isTrue();
            @SuppressWarnings("unchecked")
            var credentials = ArgumentCaptor.forClass(Map.class);
            @SuppressWarnings("unchecked")
            var configuration = ArgumentCaptor.forClass(Map.class);
            verify(integrations).connectOAuth(eq(tenantId), eq(agentId), eq("whatsapp"),
                    credentials.capture(), configuration.capture());
            assertThat(credentials.getValue()).containsEntry("accessToken", "workspace-token");
            assertThat(configuration.getValue())
                    .containsEntry("wabaId", "waba-1")
                    .containsEntry("phoneNumberId", "phone-1")
                    .containsEntry("templateName", "appointment_reminder")
                    .containsEntry("templateLanguage", "en_US");
            verify(agent).configureWhatsApp(true, "phone-1");
            verify(agents).save(agent);
        } finally {
            server.stop(0);
        }
    }
}
