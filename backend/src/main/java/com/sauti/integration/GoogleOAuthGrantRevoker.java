package com.sauti.integration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleOAuthGrantRevoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleOAuthGrantRevoker.class);
    private static final String REVOCATION_URL = "https://oauth2.googleapis.com/revoke";
    private final HttpClient httpClient;
    private final boolean enabled;

    @Autowired
    public GoogleOAuthGrantRevoker(
            @Value("${sauti.integrations.google-revoke-on-disconnect:false}") boolean enabled
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), enabled);
    }

    GoogleOAuthGrantRevoker(HttpClient httpClient, boolean enabled) {
        this.httpClient = httpClient;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean revoke(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            var body = "token=" + URLEncoder.encode(token.trim(), StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(URI.create(REVOCATION_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200
                    || (response.statusCode() == 400 && response.body().contains("invalid_token"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Google OAuth revocation was interrupted");
            return false;
        } catch (Exception exception) {
            LOGGER.warn("Google OAuth revocation could not be confirmed: {}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
