package com.sauti.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppMessageSender {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final String accessToken;
    private final String graphApiBaseUrl;

    public WhatsAppMessageSender(
            ObjectMapper objectMapper,
            @Value("${sauti.whatsapp.access-token:}") String accessToken,
            @Value("${sauti.whatsapp.graph-api-base-url:https://graph.facebook.com/v23.0}") String graphApiBaseUrl
    ) {
        this.objectMapper = objectMapper;
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.graphApiBaseUrl = graphApiBaseUrl.replaceFirst("/+$", "");
    }

    public void sendText(String phoneNumberId, String customerNumber, String text) {
        sendText(phoneNumberId, customerNumber, text, null);
    }

    public void sendText(String phoneNumberId, String customerNumber, String text, String workspaceAccessToken) {
        var token = resolveToken(workspaceAccessToken);
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            var payload = objectMapper.writeValueAsBytes(Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", customerNumber,
                    "type", "text",
                    "text", Map.of("preview_url", false, "body", text)
            ));
            var request = HttpRequest.newBuilder(URI.create(graphApiBaseUrl + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("WhatsApp send failed with HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp send was interrupted", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to send WhatsApp message", exception);
        }
    }

    public DownloadedMedia downloadMedia(String mediaId) {
        return downloadMedia(mediaId, null);
    }

    public DownloadedMedia downloadMedia(String mediaId, String workspaceAccessToken) {
        var token = resolveToken(workspaceAccessToken);
        try {
            var metadataRequest = HttpRequest.newBuilder(URI.create(graphApiBaseUrl + "/" + mediaId))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            var metadataResponse = httpClient.send(metadataRequest, HttpResponse.BodyHandlers.ofString());
            if (metadataResponse.statusCode() < 200 || metadataResponse.statusCode() >= 300) {
                throw new IllegalStateException("WhatsApp media lookup failed with HTTP " + metadataResponse.statusCode());
            }
            var metadata = objectMapper.readTree(metadataResponse.body());
            var url = metadata.path("url").asText("");
            if (url.isBlank()) {
                throw new IllegalStateException("WhatsApp media lookup returned no download URL");
            }
            var mediaRequest = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            var mediaResponse = httpClient.send(mediaRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (mediaResponse.statusCode() < 200 || mediaResponse.statusCode() >= 300) {
                throw new IllegalStateException("WhatsApp media download failed with HTTP " + mediaResponse.statusCode());
            }
            if (mediaResponse.body().length > 25 * 1024 * 1024) {
                throw new IllegalArgumentException("WhatsApp voice note exceeds the 25 MB processing limit");
            }
            return new DownloadedMedia(
                    mediaResponse.body(),
                    mediaResponse.headers().firstValue("Content-Type").orElse(
                            metadata.path("mime_type").asText("application/octet-stream")
                    )
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp media download was interrupted", exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to download WhatsApp media", exception);
        }
    }

    public void sendVoiceNote(String phoneNumberId, String customerNumber, byte[] oggOpusAudio) {
        sendVoiceNote(phoneNumberId, customerNumber, oggOpusAudio, null);
    }

    public void sendVoiceNote(
            String phoneNumberId,
            String customerNumber,
            byte[] oggOpusAudio,
            String workspaceAccessToken
    ) {
        var token = resolveToken(workspaceAccessToken);
        var mediaId = uploadAudio(phoneNumberId, oggOpusAudio, token);
        sendMessage(phoneNumberId, Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", customerNumber,
                "type", "audio",
                "audio", Map.of("id", mediaId)
        ), token);
    }

    private String uploadAudio(String phoneNumberId, byte[] audio, String token) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("No WhatsApp reply audio was generated");
        }
        try {
            var boundary = "----Sauti" + UUID.randomUUID().toString().replace("-", "");
            var prefix = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"messaging_product\"\r\n\r\n"
                    + "whatsapp\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"type\"\r\n\r\n"
                    + "audio/ogg\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"reply.ogg\"\r\n"
                    + "Content-Type: audio/ogg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            var suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            var body = new byte[prefix.length + audio.length + suffix.length];
            System.arraycopy(prefix, 0, body, 0, prefix.length);
            System.arraycopy(audio, 0, body, prefix.length, audio.length);
            System.arraycopy(suffix, 0, body, prefix.length + audio.length, suffix.length);
            var request = HttpRequest.newBuilder(URI.create(graphApiBaseUrl + "/" + phoneNumberId + "/media"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("WhatsApp media upload failed with HTTP " + response.statusCode());
            }
            var mediaId = objectMapper.readTree(response.body()).path("id").asText("");
            if (mediaId.isBlank()) {
                throw new IllegalStateException("WhatsApp media upload returned no media ID");
            }
            return mediaId;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp media upload was interrupted", exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to upload WhatsApp media", exception);
        }
    }

    private void sendMessage(String phoneNumberId, Map<String, ?> payload, String token) {
        try {
            var body = objectMapper.writeValueAsBytes(payload);
            var request = HttpRequest.newBuilder(URI.create(graphApiBaseUrl + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("WhatsApp send failed with HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp send was interrupted", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to send WhatsApp message", exception);
        }
    }

    private String resolveToken(String workspaceAccessToken) {
        var token = workspaceAccessToken == null ? "" : workspaceAccessToken.trim();
        if (token.isBlank()) token = accessToken;
        if (token.isBlank()) {
            throw new IllegalStateException("WHATSAPP_ACCESS_TOKEN is required to send WhatsApp replies");
        }
        return token;
    }

    public record DownloadedMedia(byte[] bytes, String contentType) {
    }
}
