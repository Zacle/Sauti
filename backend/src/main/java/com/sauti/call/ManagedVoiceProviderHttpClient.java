package com.sauti.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Small server-only HTTP boundary for managed voice providers.
 *
 * Provider API keys are accepted only as request headers and neither request
 * nor response bodies are logged. Browser adapters receive only the ephemeral
 * credential extracted by the matching runtime provider.
 */
@Service
public class ManagedVoiceProviderHttpClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ManagedVoiceProviderHttpClient(ObjectMapper objectMapper) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                objectMapper
        );
    }

    ManagedVoiceProviderHttpClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode post(String provider, URI uri, Map<String, String> headers, Map<String, Object> body) {
        return write(provider, "POST", uri, headers, body);
    }

    public JsonNode patch(String provider, URI uri, Map<String, String> headers, Map<String, Object> body) {
        return write(provider, "PATCH", uri, headers, body);
    }

    private JsonNode write(
            String provider,
            String method,
            URI uri,
            Map<String, String> headers,
            Map<String, Object> body
    ) {
        try {
            var builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.forEach(builder::header);
            var request = builder.method(
                    method,
                    HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))
            ).build();
            return send(provider, request);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(provider + " browser session could not be created", exception);
        }
    }

    public JsonNode get(String provider, URI uri, Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET();
        headers.forEach(builder::header);
        return send(provider, builder.build());
    }

    private JsonNode send(String provider, HttpRequest request) {
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ManagedVoiceProviderException(
                        provider,
                        response.statusCode(),
                        validationSummary(response.statusCode(), response.body())
                );
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(provider + " browser session request was interrupted", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(provider + " browser session could not be created", exception);
        }
    }

    private String validationSummary(int statusCode, String responseBody) {
        if ((statusCode != 400 && statusCode != 422)
                || responseBody == null
                || responseBody.isBlank()) return "";
        try {
            var root = objectMapper.readTree(responseBody);
            var messages = new java.util.ArrayList<String>();
            var detail = root.path("detail");
            if (detail.isArray()) {
                for (var item : detail) {
                    if (messages.size() == 3) break;
                    var location = new java.util.ArrayList<String>();
                    var locationNode = item.path("loc");
                    if (locationNode.isArray()) {
                        locationNode.forEach(part -> {
                            if (part.isTextual() || part.isIntegralNumber()) location.add(part.asText());
                        });
                    }
                    var message = item.path("msg").isTextual() ? item.path("msg").asText().trim() : "";
                    if (message.isBlank()) continue;
                    var summary = (location.isEmpty() ? "" : String.join(".", location) + ": ") + message;
                    messages.add(safeValidationText(summary));
                }
            }
            var errors = root.path("errors");
            if (errors.isArray()) {
                for (var item : errors) {
                    if (messages.size() == 3) break;
                    var source = item.path("source");
                    var location = source.path("pointer").asText("").trim();
                    if (location.isBlank()) location = source.path("parameter").asText("").trim();
                    var title = item.path("title").asText("").trim();
                    var detailMessage = item.path("detail").asText("").trim();
                    var code = item.path("code").asText("").trim();
                    var message = detailMessage.isBlank() ? title : detailMessage;
                    if (message.isBlank()) continue;
                    var prefix = location.isBlank() ? "" : location + ": ";
                    var suffix = code.isBlank() ? "" : " (" + code + ")";
                    messages.add(safeValidationText(prefix + message + suffix));
                }
            }
            return messages.isEmpty() ? "" : "Validation: " + String.join("; ", messages);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeValidationText(String value) {
        var normalized = value.replaceAll("[\\p{Cntrl}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }
}
