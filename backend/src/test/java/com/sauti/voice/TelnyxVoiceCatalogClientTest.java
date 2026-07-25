package com.sauti.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TelnyxVoiceCatalogClientTest {
    @Test
    void synthesizesThroughTheCurrentRestSpeechEndpoint() throws Exception {
        var requestedPath = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/text-to-speech/speech", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            var response = new byte[] {1, 2, 3};
            exchange.getResponseHeaders().add("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new TelnyxVoiceCatalogClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    "test-key",
                    "http://localhost:" + server.getAddress().getPort() + "/v2"
            );

            var audio = client.synthesize("Telnyx.Ultra.test", "en", "Hello");

            assertThat(audio).containsExactly(1, 2, 3);
            assertThat(requestedPath.get()).isEqualTo("/v2/text-to-speech/speech");
        } finally {
            server.stop(0);
        }
    }
}
