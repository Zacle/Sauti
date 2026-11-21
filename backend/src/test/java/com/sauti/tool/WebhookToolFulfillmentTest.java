package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.tenant.Tenant;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** An owner's webhook can recognise a repeated delivery, and is never waited on without limit. */
class WebhookToolFulfillmentTest {
    @Test
    void aRepeatedProviderDeliveryCarriesTheSameKey() throws Exception {
        var keys = new ArrayList<String>();
        var bodies = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders", exchange -> {
            synchronized (keys) {
                keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            var response = "{\"success\":true,\"data\":{\"orderNumber\":\"A-17\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var fulfillment = new WebhookToolFulfillment(new ObjectMapper(), mock(CredentialEncryption.class),
                    mock(WebhookDestinationValidator.class));
            var tool = mock(AgentTool.class);
            when(tool.getWebhookUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + "/orders");
            when(tool.getWebhookMethod()).thenReturn("POST");
            when(tool.getAuthType()).thenReturn("none");
            var call = call();
            var delivery = new LlmToolCall("telnyx:webhook:tool-9", "place_order", Map.of("item", "cake"));

            var first = fulfillment.execute(call, tool, delivery);
            fulfillment.execute(call, tool, delivery);
            fulfillment.execute(call, tool, new LlmToolCall("call_0", "place_order", Map.of("item", "cake")));

            assertThat(first.success()).isTrue();
            assertThat(first.result()).containsEntry("orderNumber", "A-17");
            assertThat(keys).hasSize(3);
            assertThat(keys.get(0)).startsWith("sauti-").isEqualTo(keys.get(1)).isNotEqualTo(keys.get(2));
            assertThat(bodies.get(0)).contains("\"idempotencyKey\":\"" + keys.get(0) + "\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theKeyDependsOnTheCallAndTheProviderDelivery() {
        var delivery = new LlmToolCall("telnyx:webhook:tool-9", "place_order", Map.of());

        assertThat(WebhookToolFulfillment.idempotencyKey(call(), delivery))
                .isNotEqualTo(WebhookToolFulfillment.idempotencyKey(call(), delivery));
        assertThat(WebhookToolFulfillment.REQUEST_TIMEOUT).isLessThan(java.time.Duration.ofSeconds(30));
        assertThat(List.of(WebhookToolFulfillment.CONNECT_TIMEOUT)).isNotEmpty();
    }

    private static Call call() {
        var agent = mock(Agent.class);
        when(agent.getId()).thenReturn(UUID.randomUUID());
        return new Call(new Tenant("Bakery", "owner@example.com", "KE"), agent, "v3:" + UUID.randomUUID(),
                "+254700000000", "inbound");
    }
}
