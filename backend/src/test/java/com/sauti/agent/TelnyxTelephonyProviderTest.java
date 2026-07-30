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
                    .contains("\"language\":\"auto\"");
        } finally {
            server.stop(0);
        }
    }
}
