package com.sauti.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * Server-only boundary for the Telnyx native voice catalog and preview API.
 */
@Service
public class TelnyxVoiceCatalogClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiBaseUrl;

    @Autowired
    public TelnyxVoiceCatalogClient(
            ObjectMapper objectMapper,
            @Value("${sauti.telnyx.api-key:}") String apiKey,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String apiBaseUrl
    ) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                objectMapper,
                apiKey,
                apiBaseUrl
        );
    }

    TelnyxVoiceCatalogClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String apiKey,
            String apiBaseUrl
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = normalize(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public JsonNode listNativeVoices() {
        ensureConfigured();
        var endpoint = apiBaseUrl + "/text-to-speech/voices?provider="
                + URLEncoder.encode("telnyx", StandardCharsets.UTF_8);
        var request = request(URI.create(endpoint)).GET().build();
        return sendJson(request, "voice catalog");
    }

    public byte[] synthesize(String voiceId, String language, String text) {
        ensureConfigured();
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "text", text,
                    "voice", voiceId,
                    "language", language,
                    "output_type", "binary_output"
            ));
            var request = request(URI.create(apiBaseUrl + "/text-to-speech"))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Telnyx voice preview failed with status " + response.statusCode()
                );
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telnyx voice preview was interrupted", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate the Telnyx voice preview", exception);
        }
    }

    private JsonNode sendJson(HttpRequest request, String operation) {
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Telnyx " + operation + " failed with status " + response.statusCode()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telnyx " + operation + " was interrupted", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load the Telnyx " + operation, exception);
        }
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Telnyx voices require TELNYX_API_KEY");
        }
    }

    private static String stripTrailingSlash(String value) {
        var normalized = normalize(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
