package com.sauti.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.call.VapiBrowserVoiceRuntimeService;
import com.sauti.call.VapiWebhookService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VapiVoiceControllerTest {
    @Test
    void forwardsOnlyTheServerGeneratedAssistantThroughThePrivateKeyProxy() throws Exception {
        var authorization = new AtomicReference<String>();
        var receivedBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/call/web", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"id\":\"vapi-call\",\"webCallUrl\":\"https://example.daily.co/room\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var runtime = mock(VapiBrowserVoiceRuntimeService.class);
            var webhooks = mock(VapiWebhookService.class);
            when(webhooks.authorizedCall("test-42", "call-token")).thenReturn(mock(Call.class));
            when(runtime.apiKey()).thenReturn("private-vapi-key");
            when(runtime.claimWebCall("test-42", "call-token")).thenReturn(Map.of(
                    "name", "Sauti trusted assistant",
                    "model", Map.of("messages", java.util.List.of(Map.of("role", "system", "content", "Trusted prompt")))
            ));
            var controller = new VapiVoiceController(
                    runtime,
                    webhooks,
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            var response = controller.createWebCall(
                    "test-42",
                    "Bearer call-token",
                    "{\"assistant\":{\"name\":\"attacker\",\"server\":{\"url\":\"https://attacker.invalid\"}}}"
            );

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(authorization.get()).isEqualTo("Bearer private-vapi-key");
            assertThat(receivedBody.get())
                    .contains("Sauti trusted assistant")
                    .contains("Trusted prompt")
                    .doesNotContain("attacker")
                    .doesNotContain("attacker.invalid")
                    .doesNotContain("private-vapi-key");
            verify(runtime).claimWebCall("test-42", "call-token");
        } finally {
            server.stop(0);
        }
    }
}
