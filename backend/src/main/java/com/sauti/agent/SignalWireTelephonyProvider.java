package com.sauti.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "signalwire")
public class SignalWireTelephonyProvider implements TelephonyProvider {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String projectId;
    private final String authToken;
    private final String spaceUrl;
    private final String publicBaseUrl;

    public SignalWireTelephonyProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.signalwire.project-id}") String projectId,
            @Value("${sauti.signalwire.auth-token}") String authToken,
            @Value("${sauti.signalwire.space-url}") String spaceUrl,
            @Value("${sauti.signalwire.public-base-url}") String publicBaseUrl
    ) {
        this.objectMapper = objectMapper;
        this.projectId = projectId;
        this.authToken = authToken;
        this.spaceUrl = spaceUrl.endsWith("/") ? spaceUrl.substring(0, spaceUrl.length() - 1) : spaceUrl;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public String provisionNumber(String tenantCountryCode) {
        try {
            var phoneNumber = findAvailableNumber(tenantCountryCode);
            purchaseNumber(phoneNumber);
            return phoneNumber;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not provision SignalWire number", exception);
        }
    }

    @Override
    public String buildMediaStreamTwiMl(
            String websocketUrl,
            String callSid,
            String tenantId,
            String agentId,
            boolean recordCall,
            Map<String, String> extraParameters
    ) {
        var recording = recordCall
                ? """
                  <Start>
                    <Recording recordingStatusCallback="%s/webhooks/signalwire/status"
                               recordingStatusCallbackEvent="completed absent"
                               recordingTrack="both"/>
                  </Start>
                """.formatted(escapeXml(publicBaseUrl))
                : "";
        var extra = extraParameters.entrySet().stream()
                .map(entry -> "      <Parameter name=\"" + escapeXml(entry.getKey())
                        + "\" value=\"" + escapeXml(entry.getValue()) + "\"/>")
                .collect(Collectors.joining("\n"));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                %s
                  <Connect>
                    <Stream url="%s">
                      <Parameter name="callSid" value="%s"/>
                      <Parameter name="tenantId" value="%s"/>
                      <Parameter name="agentId" value="%s"/>
                %s
                    </Stream>
                  </Connect>
                </Response>
                """.formatted(recording, escapeXml(websocketUrl), escapeXml(callSid),
                escapeXml(tenantId), escapeXml(agentId), extra);
    }

    private String findAvailableNumber(String countryCode) throws Exception {
        var country = countryCode == null || countryCode.isBlank() ? "US" : countryCode.toUpperCase();
        var request = HttpRequest.newBuilder(URI.create(api("/AvailablePhoneNumbers/" + country + "/Local.json?VoiceEnabled=true&PageSize=1")))
                .header("Authorization", authorization())
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("SignalWire available-number lookup failed with HTTP " + response.statusCode());
        }
        var numbers = objectMapper.readTree(response.body()).path("available_phone_numbers");
        if (!numbers.isArray() || numbers.isEmpty()) {
            throw new IllegalStateException("No SignalWire voice numbers are available for " + country);
        }
        return numbers.get(0).path("phone_number").asText();
    }

    private void purchaseNumber(String phoneNumber) throws Exception {
        var voiceUrl = publicBaseUrl + "/webhooks/signalwire/voice";
        var statusUrl = publicBaseUrl + "/webhooks/signalwire/status";
        var body = "PhoneNumber=" + encode(phoneNumber)
                + "&VoiceUrl=" + encode(voiceUrl)
                + "&VoiceMethod=POST"
                + "&StatusCallback=" + encode(statusUrl)
                + "&StatusCallbackMethod=POST";
        var request = HttpRequest.newBuilder(URI.create(api("/IncomingPhoneNumbers.json")))
                .header("Authorization", authorization())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("SignalWire number purchase failed with HTTP " + response.statusCode());
        }
    }

    private String api(String path) {
        return spaceUrl + "/api/laml/2010-04-01/Accounts/" + projectId + path;
    }

    private String authorization() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (projectId + ":" + authToken).getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String escapeXml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
