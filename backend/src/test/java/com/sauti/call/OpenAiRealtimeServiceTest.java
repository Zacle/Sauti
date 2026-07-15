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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiRealtimeServiceTest {
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
                    .contains("interrupt_response")
                    .contains("gpt-4o-mini-transcribe");
        } finally {
            server.stop(0);
        }
    }
}
