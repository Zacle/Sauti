package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.llm.ConversationOrchestrator;
import com.sauti.tool.AgentToolLoader;
import com.sauti.tool.ToolFulfillmentRouter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiRealtimeServiceTest {
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
        when(loader.loadForAgent(agent.getId())).thenReturn(List.of(
                new com.sauti.llm.LlmToolDefinition(
                        "check_availability", "Check one time", Map.of("type", "object")
                ),
                com.sauti.tool.ConversationStateTool.definition()
        ));
        when(orchestrator.realtimeInstructions(call, "fr")).thenReturn("Speak French concisely.");
        var mapper = new ObjectMapper();
        var service = new OpenAiRealtimeService(
                mapper, orchestrator, loader, mock(ToolFulfillmentRouter.class),
                "server-secret", "http://localhost/unused", "gpt-realtime-1.5",
                "gpt-4o-mini-transcribe", 512, 1_000, 0.8
        );

        var configurationNode = mapper.valueToTree(service.telephonySessionConfiguration(call));
        var configuration = mapper.writeValueAsString(configurationNode);

        assertThat(configuration)
                .contains("\"output_modalities\":[\"text\"]")
                .contains("\"silence_duration_ms\":410")
                .contains("\"create_response\":false")
                .contains("\"interrupt_response\":false")
                .contains("\"parallel_tool_calls\":false")
                .contains("\"name\":\"update_conversation_state\"")
                .contains("\"threshold\":0.6");
        assertThat(configurationNode.at("/audio/input/format/type").asText()).isEqualTo("audio/pcm");
        assertThat(configurationNode.at("/audio/input/format/rate").asInt()).isEqualTo(24000);
        assertThat(configurationNode.at("/truncation/type").asText()).isEqualTo("retention_ratio");
        assertThat(configurationNode.at("/truncation/retention_ratio").asDouble()).isEqualTo(0.8);
        assertThat(configurationNode.at("/truncation/token_limits/post_instructions").asInt()).isEqualTo(1_000);
    }

    @Test
    void createsATextFirstProviderSessionForLegacyOpenAiVoicesWithoutExposingTheApiKey() throws Exception {
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
                    "server-secret", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/realtime/calls",
                    "gpt-realtime-1.5", "gpt-4o-mini-transcribe", 512, 1_000, 0.8
            );

            var answer = service.createWebRtcSession(call, "v=0\r\ns=sauti-offer\r\n");

            assertThat(answer).contains("sauti-answer");
            assertThat(receivedAuthorization.get()).isEqualTo("Bearer server-secret");
            assertThat(receivedBody.get())
                    .contains("sauti-offer")
                    .contains("gpt-realtime-1.5")
                    .contains("\"output_modalities\":[\"text\"]")
                    .contains("\"audio\":{\"input\":")
                    .contains("\"max_output_tokens\":512")
                    .contains("\"truncation\":{\"")
                    .contains("\"post_instructions\":1000")
                    .contains("\"threshold\":0.6")
                    .contains("\"silence_duration_ms\":520")
                    .contains("\"create_response\":false")
                    .contains("\"interrupt_response\":false")
                    .contains("gpt-4o-mini-transcribe")
                    .doesNotContain("marin")
                    .doesNotContain("\"output\":{\"voice\"");
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
                    "server-secret", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/realtime/calls",
                    "gpt-realtime-1.5", "gpt-4o-mini-transcribe", 512, 1_000, 0.8
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
