package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.tenant.Tenant;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BrowserSpeechToTextServiceTest {
    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
        servers.clear();
    }

    @Test
    void usesOpenAiFirstForFrenchAgents() throws Exception {
        var openAiRequests = new AtomicInteger();
        var deepgramRequests = new AtomicInteger();
        var openAi = server((body, query) -> {
            openAiRequests.incrementAndGet();
            assertThat(body).contains("name=\"model\"");
            assertThat(body).contains("gpt-4o-transcribe");
            return """
                    {"text":"Bonjour, je voudrais prendre rendez-vous."}
                    """;
        });
        var deepgram = server((body, query) -> {
            deepgramRequests.incrementAndGet();
            return deepgramResponse("");
        });

        var service = service("deepgram-key", deepgram.url(), "openai-key", openAi.url());

        assertThat(service.transcribe(agent("fr", List.of("fr")), new byte[] {1, 2, 3}, "audio/webm"))
                .isEqualTo("Bonjour, je voudrais prendre rendez-vous.");
        assertThat(openAiRequests).hasValue(1);
        assertThat(deepgramRequests).hasValue(0);
    }

    @Test
    void fallsBackToOpenAiWhenDeepgramReturnsNoEnglishTranscript() throws Exception {
        var openAiRequests = new AtomicInteger();
        var deepgramRequests = new AtomicInteger();
        var deepgram = server((body, query) -> {
            deepgramRequests.incrementAndGet();
            assertThat(query).contains("language=en");
            return deepgramResponse("");
        });
        var openAi = server((body, query) -> {
            openAiRequests.incrementAndGet();
            return """
                    {"text":"I need an appointment tomorrow."}
                    """;
        });

        var service = service("deepgram-key", deepgram.url(), "openai-key", openAi.url());

        assertThat(service.transcribe(agent("en", List.of("en")), new byte[] {4, 5, 6}, "audio/webm"))
                .isEqualTo("I need an appointment tomorrow.");
        assertThat(deepgramRequests).hasValue(1);
        assertThat(openAiRequests).hasValue(1);
    }

    private BrowserSpeechToTextService service(String deepgramKey, String deepgramUrl, String openAiKey, String openAiUrl) {
        return new BrowserSpeechToTextService(
                new ObjectMapper(),
                deepgramKey,
                deepgramUrl,
                "nova-3",
                openAiKey,
                openAiUrl,
                "gpt-4o-transcribe"
        );
    }

    private Agent agent(String defaultLanguage, List<String> supportedLanguages) {
        var agent = new Agent(new Tenant("Demo Clinic", "owner@example.com", "KE"), "Amina", "Bonjour", "Prompt");
        agent.update(
                "Amina",
                "",
                "Bonjour",
                "Prompt",
                defaultLanguage,
                supportedLanguages,
                null,
                null,
                List.of(),
                false,
                "UTC",
                "",
                "",
                null,
                true,
                false
        );
        return agent;
    }

    private TestServer server(Handler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var response = handler.handle(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1),
                    exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery()
            );
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return new TestServer("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private String deepgramResponse(String transcript) {
        return """
                {"results":{"channels":[{"alternatives":[{"transcript":"%s"}]}]}}
                """.formatted(transcript);
    }

    private interface Handler {
        String handle(String body, String query);
    }

    private record TestServer(String url) {
    }
}
