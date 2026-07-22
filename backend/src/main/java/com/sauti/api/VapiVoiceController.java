package com.sauti.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.VapiBrowserVoiceRuntimeService;
import com.sauti.call.VapiWebhookService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/vapi/{callSid}")
public class VapiVoiceController {
    private final VapiBrowserVoiceRuntimeService runtimeService;
    private final VapiWebhookService webhookService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;

    public VapiVoiceController(
            VapiBrowserVoiceRuntimeService runtimeService,
            VapiWebhookService webhookService,
            ObjectMapper objectMapper,
            @Value("${sauti.vapi.api-base-url:https://api.vapi.ai}") String apiBaseUrl
    ) {
        this.runtimeService = runtimeService;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    @PostMapping(value = "/call/web", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> createWebCall(
            @PathVariable String callSid,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody String body
    ) {
        var token = bearer(authorization);
        webhookService.authorizedCall(callSid, token);
        var configuration = runtimeService.claimWebCall(callSid, token);
        try {
            var requested = objectMapper.readTree(body);
            var authoritative = objectMapper.createObjectNode();
            authoritative.set("assistant", objectMapper.valueToTree(configuration));
            if (requested.path("roomDeleteOnUserLeaveEnabled").isBoolean()) {
                authoritative.put(
                        "roomDeleteOnUserLeaveEnabled",
                        requested.path("roomDeleteOnUserLeaveEnabled").asBoolean()
                );
            }
            var request = HttpRequest.newBuilder(URI.create(apiBaseUrl + "/call/web"))
                    .timeout(Duration.ofSeconds(25))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + runtimeService.apiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(authoritative)))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                runtimeService.restoreWebCall(callSid, token, configuration);
            }
            return ResponseEntity.status(response.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.body());
        } catch (InterruptedException exception) {
            runtimeService.restoreWebCall(callSid, token, configuration);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Vapi web call creation was interrupted", exception);
        } catch (Exception exception) {
            runtimeService.restoreWebCall(callSid, token, configuration);
            if (exception instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to create the Vapi web call", exception);
        }
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> webhook(
            @PathVariable String callSid,
            @RequestParam String token,
            @RequestBody JsonNode payload
    ) {
        return webhookService.handle(callSid, token, payload);
    }

    private String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new IllegalArgumentException("Vapi proxy token is required");
        }
        return authorization.substring(7).trim();
    }

    private String stripTrailingSlash(String value) {
        var normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
