package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.call.ManagedVoiceAgentProvisioningService;
import com.sauti.call.ManagedVoiceAgentReference;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelnyxTelephonyProviderTest {
    @Test
    void quotesTheSelectedNumberUsingCurrentProviderPricing() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/available_phone_numbers", exchange -> {
            var response = """
                    {"data":[{
                      "phone_number":"+447911123456",
                      "phone_number_type":"mobile",
                      "features":[{"name":"voice"}],
                      "cost_information":{"upfront_cost":"1.25","monthly_cost":"2.50","currency":"USD"}
                    }]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provider = new TelnyxTelephonyProvider(
                    new ObjectMapper(), "test-key", "connection-1",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", "",
                    "Telnyx.NaturalHD.astra", mock(ManagedVoiceAgentProvisioningService.class));

            var quote = provider.quotePhoneNumber("GB", "+447911123456");

            assertThat(quote.upfrontCost()).isEqualByComparingTo("1.25");
            assertThat(quote.monthlyCost()).isEqualByComparingTo("2.50");
            assertThat(quote.initialEstimatedCost()).isEqualByComparingTo("3.75");
            assertThat(quote.currency()).isEqualTo("USD");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assignsAnActiveNumberToTheConfiguredMessagingProfile() throws Exception {
        var requests = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath() + " " + body);
            var response = ("GET".equals(exchange.getRequestMethod())
                    ? "{\"data\":{\"id\":\"order-1\",\"status\":\"success\",\"phone_numbers\":[{\"phone_number\":\"+447911123456\",\"requirements_met\":true}]}}"
                    : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provider = new TelnyxTelephonyProvider(
                    new ObjectMapper(), "test-key", "connection-1",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", "profile-1",
                    "Telnyx.NaturalHD.astra", mock(ManagedVoiceAgentProvisioningService.class));

            var result = provider.refreshPhoneNumber("order-1");

            assertThat(result.status()).isEqualTo("active");
            assertThat(requests).containsExactly(
                    "GET /number_orders/order-1 ",
                    "PATCH /messaging_phone_numbers/%2B447911123456 {\"messaging_profile_id\":\"profile-1\"}"
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preparesInboundAssistantBeforeAnsweringTheCaller() throws Exception {
        var order = new ArrayList<String>();
        var requestBodies = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var requestBody = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            synchronized (order) {
                order.add(exchange.getRequestURI().getRawPath());
                requestBodies.add(requestBody);
            }
            var response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
            var call = mock(Call.class);
            var agent = mock(Agent.class);
            when(call.getAgent()).thenReturn(agent);
            when(call.getTwilioCallSid()).thenReturn("v3:test-call");
            when(call.getLanguageDetected()).thenReturn("fr");
            when(agent.isRecordCalls()).thenReturn(false);
            when(agent.getTtsVoiceId()).thenReturn("Telnyx.NaturalHD.astra");
            when(agent.getSupportedLanguages()).thenReturn(List.of("fr"));
            when(agent.getDefaultLanguage()).thenReturn("fr");
            when(provisioning.existing(call)).thenAnswer(ignored -> {
                synchronized (order) {
                    order.add("existing");
                }
                return new ManagedVoiceAgentReference("assistant-1", "main", "{}");
            });
            var provider = new TelnyxTelephonyProvider(
                    new ObjectMapper(),
                    "test-key",
                    "connection-1",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "",
                    "",
                    "Telnyx.NaturalHD.astra",
                    provisioning
            );

            provider.answerInboundCall(call, "v3:test-control", "Bonjour");

            assertThat(order).containsExactly(
                    "existing",
                    "/calls/v3%3Atest-control/actions/answer",
                    "/calls/v3%3Atest-control/actions/ai_assistant_start"
            );
            assertThat(requestBodies.get(1))
                    .contains("\"voice\":\"Telnyx.NaturalHD.amarante\"")
                    .contains("\"transcription\":{")
                    .contains("\"model\":\"deepgram/nova-3\"")
                    .contains("\"language\":\"fr\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tellsTheAssistantAboutBusinessHoursAndSpeaksTheClosedMessageWithoutIt() throws Exception {
        var requests = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (requests) {
                requests.add(exchange.getRequestURI().getRawPath() + " " + body);
            }
            var response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provisioning = mock(ManagedVoiceAgentProvisioningService.class);
            var call = mock(Call.class);
            var agent = mock(Agent.class);
            when(call.getAgent()).thenReturn(agent);
            when(call.getTwilioCallSid()).thenReturn("v3:test-call");
            when(agent.getDefaultLanguage()).thenReturn("en");
            when(agent.getSupportedLanguages()).thenReturn(List.of("en"));
            when(provisioning.existing(call)).thenReturn(new ManagedVoiceAgentReference("assistant-1", "", "{}"));
            var provider = new TelnyxTelephonyProvider(new ObjectMapper(), "test-key", "connection-1",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", "", "Telnyx.NaturalHD.astra",
                    provisioning);

            provider.answerInboundCall(call, "v3:open", "We are closed, but I can take a message.",
                    "after_hours_take_message");
            provider.answerWithClosedMessage(call, "v3:closed", "We are closed. Goodbye.");
            provider.reject("v3:nobody");

            assertThat(requests.get(1)).startsWith("/calls/v3%3Aopen/actions/ai_assistant_start")
                    .contains("\"sauti_business_hours\":\"after_hours_take_message\"");
            assertThat(requests.get(2)).startsWith("/calls/v3%3Aclosed/actions/answer");
            assertThat(requests.get(3)).startsWith("/calls/v3%3Aclosed/actions/speak")
                    .contains("\"payload\":\"We are closed. Goodbye.\"")
                    .contains("\"client_state\":\"c2F1dGktY2xvc2Vk\"");
            assertThat(requests.get(4)).startsWith("/calls/v3%3Anobody/actions/reject")
                    .contains("\"cause\":\"CALL_REJECTED\"");
            assertThat(requests).noneMatch(request -> request.startsWith("/calls/v3%3Aclosed/actions/ai_assistant_start"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listensForTheAgentLanguageUnlessItSpeaksSeveral() {
        var single = mock(Agent.class);
        when(single.getSupportedLanguages()).thenReturn(List.of("sw"));
        var several = mock(Agent.class);
        when(several.getSupportedLanguages()).thenReturn(List.of("en", "sw"));

        assertThat(TelnyxTelephonyProvider.transcriptionLanguage(single, "sw")).isEqualTo("sw");
        assertThat(TelnyxTelephonyProvider.transcriptionLanguage(several, "en")).isEqualTo("auto");
    }

    @Test
    void tagsATransferLegWithTheCallItBelongsTo() throws Exception {
        var requests = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            synchronized (requests) {
                requests.add(exchange.getRequestURI().getRawPath() + " "
                        + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            var response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provider = new TelnyxTelephonyProvider(new ObjectMapper(), "test-key", "connection-1",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", "", "Telnyx.NaturalHD.astra",
                    mock(ManagedVoiceAgentProvisioningService.class));

            provider.transfer("v3:caller", "+254711000000", "+254700000001", "sauti-transfer:abc");

            assertThat(requests.get(0)).startsWith("/calls/v3%3Acaller/actions/transfer")
                    .contains("\"to\":\"+254711000000\"")
                    .contains("\"client_state\":\"" + java.util.Base64.getEncoder().encodeToString(
                            "sauti-transfer:abc".getBytes(StandardCharsets.UTF_8)) + "\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsAPhoneCallsConversationInSpokenOrderWithoutToolTraffic() throws Exception {
        var queries = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getRawPath();
            var query = exchange.getRequestURI().getRawQuery();
            synchronized (queries) {
                queries.add(path + "?" + query);
            }
            String body;
            if (path.equals("/ai/conversations")) {
                body = "{\"data\":[{\"id\":\"conv-1\",\"metadata\":{\"call_control_id\":\"v3:caller\"}}]}";
            } else if (query.contains("page%5Bnumber%5D=1")) {
                // Newest first, as a provider may return them; a tool call carries no spoken text.
                body = """
                        {"data":[
                          {"role":"assistant","text":"Booked for Monday.","sent_at":"2026-11-22T09:00:09Z"},
                          {"role":"assistant","text":null,"tool_calls":[{"id":"t1","type":"function"}],"sent_at":"2026-11-22T09:00:08Z"},
                          {"role":"tool","text":"{\\"ok\\":true}","sent_at":"2026-11-22T09:00:08Z"}
                        ],"meta":{"total_pages":2,"total_results":5,"page_number":1,"page_size":100}}
                        """;
            } else {
                body = """
                        {"data":[
                          {"role":"user","text":"Monday please.","sent_at":"2026-11-22T09:00:05Z"},
                          {"role":"assistant","text":"Hello, which day?","sent_at":"2026-11-22T09:00:01Z"}
                        ],"meta":{"total_pages":2,"total_results":5,"page_number":2,"page_size":100}}
                        """;
            }
            var response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provider = new TelnyxTelephonyProvider(new ObjectMapper(), "test-key", "connection-1",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", "", "Telnyx.NaturalHD.astra",
                    mock(ManagedVoiceAgentProvisioningService.class));

            var lookup = provider.conversation("v3:caller");

            assertThat(lookup.status()).isEqualTo(com.sauti.call.ProviderConversations.Status.FOUND);
            assertThat(lookup.messages()).extracting(com.sauti.call.ProviderConversations.Message::role,
                            com.sauti.call.ProviderConversations.Message::text)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("agent", "Hello, which day?"),
                            org.assertj.core.groups.Tuple.tuple("caller", "Monday please."),
                            org.assertj.core.groups.Tuple.tuple("agent", "Booked for Monday."));
            assertThat(queries.get(0)).isEqualTo(
                    "/ai/conversations?metadata-%3Ecall_control_id=eq.v3%3Acaller&order=created_at.asc&limit=5");
            assertThat(queries).hasSize(3);
            // Not a Telnyx call: nothing to ask.
            assertThat(provider.conversation("web-test-1").status())
                    .isEqualTo(com.sauti.call.ProviderConversations.Status.UNAVAILABLE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aConversationNotCreatedYetIsAskedForAgainLater() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var response = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var provider = new TelnyxTelephonyProvider(new ObjectMapper(), "test-key", "connection-1",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", "", "Telnyx.NaturalHD.astra",
                    mock(ManagedVoiceAgentProvisioningService.class));

            assertThat(provider.conversation("v3:caller").status())
                    .isEqualTo(com.sauti.call.ProviderConversations.Status.NOT_READY);
        } finally {
            server.stop(0);
        }
    }
}
