package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.llm.ConversationOrchestrator;
import com.sauti.nlp.SimpleLanguageDetector;
import com.sauti.nlp.LanguageDetector;
import com.sauti.tool.AgentToolLoader;
import com.sauti.tool.ToolFulfillmentRouter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiRealtimeServiceTest {
    @Test
    void followsASubstantialEnglishCallerTurnInsteadOfAStaleLanguage() {
        var orchestrator = mock(ConversationOrchestrator.class);
        var detector = mock(LanguageDetector.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getAgent()).thenReturn(agent);
        when(call.getLanguageDetected()).thenReturn("es");
        when(agent.getSupportedLanguages()).thenReturn(List.of("en", "es"));
        when(detector.detect(
                "Hello, I would like to book an appointment.", "es", List.of("en", "es")
        )).thenReturn("en");
        when(orchestrator.realtimeInstructions(
                call, "en", "Hello, I would like to book an appointment."
        )).thenReturn("English instructions");
        var service = new OpenAiRealtimeService(
                new ObjectMapper(), orchestrator, mock(AgentToolLoader.class), mock(ToolFulfillmentRouter.class),
                mock(com.sauti.session.CallSessionStore.class), detector,
                "server-secret", "http://localhost/unused", "gpt-realtime-1.5", "gpt-4o-mini-transcribe"
        );

        assertThat(service.realtimeInstructions(call, "Hello, I would like to book an appointment."))
                .isEqualTo("English instructions");
        verify(orchestrator).realtimeInstructions(
                call, "en", "Hello, I would like to book an appointment."
        );
    }

    @Test
    void configuresTelephonyPcmAndAgentSpecificTurnDetectionForCartesia() throws Exception {
        var orchestrator = mock(ConversationOrchestrator.class);
        var loader = mock(AgentToolLoader.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getAgent()).thenReturn(agent);
        when(call.getLanguageDetected()).thenReturn("fr");
        when(agent.getId()).thenReturn(UUID.randomUUID());
        when(agent.getTtsVoiceId()).thenReturn("cartesia:french-voice");
        when(agent.getBargeInSensitivity()).thenReturn(0.8);
        when(agent.getSttEndpointingMs()).thenReturn(410);
        when(loader.loadForAgent(agent.getId())).thenReturn(List.of());
        when(orchestrator.realtimeInstructions(call, "fr")).thenReturn("Speak French concisely.");
        var mapper = new ObjectMapper();
        var service = new OpenAiRealtimeService(
                mapper, orchestrator, loader, mock(ToolFulfillmentRouter.class),
                mock(com.sauti.session.CallSessionStore.class),
                new SimpleLanguageDetector(),
                "server-secret", "http://localhost/unused", "gpt-realtime-1.5", "gpt-4o-mini-transcribe"
        );

        var configurationNode = mapper.valueToTree(service.telephonySessionConfiguration(call));
        var configuration = mapper.writeValueAsString(configurationNode);

        assertThat(configuration)
                .contains("\"output_modalities\":[\"text\"]")
                .contains("\"silence_duration_ms\":410")
                .contains("\"create_response\":false")
                .contains("\"interrupt_response\":false")
                .contains("\"threshold\":0.6");
        assertThat(configurationNode.at("/audio/input/format/type").asText()).isEqualTo("audio/pcm");
        assertThat(configurationNode.at("/audio/input/format/rate").asInt()).isEqualTo(24000);
    }

    @Test
    void createsAProviderBoundRealtimeSessionWithoutExposingTheApiKey() throws Exception {
        var receivedAuthorization = new AtomicReference<String>();
        var receivedBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/realtime/calls", exchange -> {
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var answer = "v=0\r\ns=sauti-answer\r\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        try {
            var orchestrator = mock(ConversationOrchestrator.class);
            var loader = mock(AgentToolLoader.class);
            var call = mock(Call.class);
            var agent = mock(Agent.class);
            when(call.getAgent()).thenReturn(agent);
            when(call.getLanguageDetected()).thenReturn("fr");
            when(agent.getId()).thenReturn(UUID.randomUUID());
            when(agent.getTtsVoiceId()).thenReturn("openai:marin");
            when(loader.loadForAgent(agent.getId())).thenReturn(List.of());
            when(orchestrator.realtimeInstructions(call, "fr")).thenReturn("Speak French concisely.");
            var service = new OpenAiRealtimeService(
                    new ObjectMapper(), orchestrator, loader, mock(ToolFulfillmentRouter.class),
                    mock(com.sauti.session.CallSessionStore.class),
                    new SimpleLanguageDetector(),
                    "server-secret", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/realtime/calls",
                    "gpt-realtime-1.5", "gpt-4o-mini-transcribe"
            );

            var answer = service.createWebRtcSession(call, "v=0\r\ns=sauti-offer\r\n");

            assertThat(answer).contains("sauti-answer");
            assertThat(receivedAuthorization.get()).isEqualTo("Bearer server-secret");
            assertThat(receivedBody.get())
                    .contains("sauti-offer")
                    .contains("gpt-realtime-1.5")
                    .contains("openai:marin".substring("openai:".length()))
                    .contains("\"max_output_tokens\":\"inf\"")
                    .contains("\"threshold\":0.55")
                    .contains("\"silence_duration_ms\":320")
                    .contains("\"create_response\":false")
                    .contains("\"interrupt_response\":false")
                    .contains("gpt-4o-mini-transcribe");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuresTextOutputWithoutAnOpenAiVoiceForHybridCartesiaCalls() throws Exception {
        var receivedBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/realtime/calls", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var answer = "v=0\r\ns=hybrid-answer\r\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        try {
            var orchestrator = mock(ConversationOrchestrator.class);
            var loader = mock(AgentToolLoader.class);
            var call = mock(Call.class);
            var agent = mock(Agent.class);
            when(call.getAgent()).thenReturn(agent);
            when(call.getLanguageDetected()).thenReturn("fr");
            when(agent.getId()).thenReturn(UUID.randomUUID());
            when(agent.getTtsVoiceId()).thenReturn("cartesia:french-voice");
            when(loader.loadForAgent(agent.getId())).thenReturn(List.of());
            when(orchestrator.realtimeInstructions(call, "fr")).thenReturn("Speak French concisely.");
            var service = new OpenAiRealtimeService(
                    new ObjectMapper(), orchestrator, loader, mock(ToolFulfillmentRouter.class),
                    mock(com.sauti.session.CallSessionStore.class),
                    new SimpleLanguageDetector(),
                    "server-secret", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/realtime/calls",
                    "gpt-realtime-1.5", "gpt-4o-mini-transcribe"
            );

            assertThat(service.createWebRtcSession(call, "v=0\r\ns=hybrid-offer\r\n"))
                    .contains("hybrid-answer");
            assertThat(receivedBody.get())
                    .contains("\"output_modalities\":[\"text\"]")
                    .contains("\"audio\":{\"input\":")
                    .contains("\"threshold\":0.6")
                    .contains("\"silence_duration_ms\":520")
                    .contains("\"create_response\":false")
                    .contains("\"interrupt_response\":false")
                    .doesNotContain("french-voice")
                    .doesNotContain("\"output\":{\"voice\"");
        } finally {
            server.stop(0);
        }
    }
}
