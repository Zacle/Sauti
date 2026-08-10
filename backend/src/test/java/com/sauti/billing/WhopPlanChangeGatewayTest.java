package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WhopPlanChangeGatewayTest {
    @Test
    void schedulesTargetAtRenewalAndCancelsOnlyTheCurrentMembership() throws Exception {
        var invoiceBody = new AtomicReference<String>();
        var cancelBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        respond(server, "/api/v1/memberships/mem_scale", """
                {"id":"mem_scale","status":"active","cancel_at_period_end":false,
                 "member":{"id":"mber_1"},"user":{"id":"user_1"},
                 "company":{"id":"biz_sauti"},"product":{"id":"prod_sauti"}}
                """);
        respond(server, "/api/v1/memberships", "{\"data\":[]}");
        respond(server, "/api/v1/payment_methods", "{\"data\":[{\"id\":\"pmt_1\"}]}");
        respond(server, "/api/v1/plans/plan_growth_monthly", """
                {"id":"plan_growth_monthly","renewal_price":149,"billing_period":30,
                 "description":"Growth monthly","product":{"id":"prod_sauti"}}
                """);
        server.createContext("/api/v1/invoices", exchange -> {
            invoiceBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 201, "{\"id\":\"inv_change\",\"current_plan\":{\"id\":\"plan_generated\"}}");
        });
        server.createContext("/api/v1/memberships/mem_scale/cancel", exchange -> {
            cancelBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 200, "{\"id\":\"mem_scale\",\"status\":\"canceling\"}");
        });
        server.start();
        try {
            var mapper = new ObjectMapper();
            var gateway = new WhopPlanChangeGateway(mapper, HttpClient.newHttpClient(), "api-key",
                    "biz_sauti", "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                    "2026-07-20");
            var subscription = subscription();
            var effectiveAt = OffsetDateTime.parse("2027-09-10T10:00:00Z");

            var result = gateway.prepare(subscription,
                    new WhopPlanCatalog.Plan("growth", "monthly", "plan_growth_monthly", 750), effectiveAt);

            assertThat(result.kind()).isEqualTo("scheduled");
            assertThat(result.invoiceId()).isEqualTo("inv_change");
            assertThat(result.generatedPlanId()).isEqualTo("plan_generated");
            var body = mapper.readTree(invoiceBody.get());
            assertThat(body.path("product_id").asText()).isEqualTo("prod_sauti");
            assertThat(body.path("member_id").asText()).isEqualTo("mber_1");
            assertThat(body.path("payment_method_id").asText()).isEqualTo("pmt_1");
            assertThat(body.path("collection_method").asText()).isEqualTo("charge_automatically");
            assertThat(body.path("automatically_finalizes_at").asText()).isEqualTo(effectiveAt.toString());
            assertThat(body.path("plan").path("renewal_price").decimalValue()).isEqualByComparingTo("149");
            assertThat(mapper.readTree(cancelBody.get()).path("cancellation_mode").asText())
                    .isEqualTo("at_period_end");
        } finally {
            server.stop(0);
        }
    }

    private static BillingSubscription subscription() {
        var subscription = new BillingSubscription(java.util.UUID.randomUUID(), "whop", "mem_scale");
        subscription.synchronize("user_1", "mem_scale", "prod_sauti", "plan_scale_monthly",
                "scale", "monthly", "active", true, OffsetDateTime.parse("2027-09-10T10:00:00Z"),
                null, null, OffsetDateTime.parse("2026-08-10T10:00:00Z"), "", "",
                "https://whop.com/billing/manage/mem_scale");
        return subscription;
    }

    private static void respond(HttpServer server, String path, String response) {
        server.createContext(path, exchange -> write(exchange, 200, response));
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, String response)
            throws java.io.IOException {
        var bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
