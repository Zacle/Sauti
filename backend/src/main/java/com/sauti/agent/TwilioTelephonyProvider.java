package com.sauti.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "twilio")
public class TwilioTelephonyProvider implements TelephonyProvider {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String accountSid;
    private final String authToken;
    private final String publicBaseUrl;

    public TwilioTelephonyProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.twilio.account-sid}") String accountSid,
            @Value("${sauti.twilio.auth-token}") String authToken,
            @Value("${sauti.twilio.public-base-url}") String publicBaseUrl
    ) {
        this.objectMapper = objectMapper;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public String provisionNumber(String tenantCountryCode) {
        try {
            var phoneNumber = findAvailableNumber(tenantCountryCode);
            purchaseNumber(phoneNumber);
            return phoneNumber;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not provision Twilio number", exception);
        }
    }

    @Override
    public String buildMediaStreamTwiMl(
            String websocketUrl,
            String callSid,
            String tenantId,
            String agentId,
            boolean recordCall,
            java.util.Map<String, String> extraParameters
    ) {
        var recording = recordCall
                ? """
                  <Start>
                    <Recording recordingStatusCallback="%s/webhooks/twilio/status"
                               recordingStatusCallbackEvent="completed absent"
                               recordingTrack="both"/>
                  </Start>
                """.formatted(escapeXml(publicBaseUrl))
                : "";
        var extra = extraParameters.entrySet().stream()
                .map(entry -> "      <Parameter name=\"" + escapeXml(entry.getKey())
                        + "\" value=\"" + escapeXml(entry.getValue()) + "\"/>")
                .collect(java.util.stream.Collectors.joining("\n"));
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
                """.formatted(recording, escapeXml(websocketUrl), escapeXml(callSid), escapeXml(tenantId), escapeXml(agentId), extra);
    }

    private String findAvailableNumber(String countryCode) throws Exception {
        var country = countryCode == null || countryCode.isBlank() ? "US" : countryCode.toUpperCase();
        var request = HttpRequest.newBuilder(URI.create(twilioApi("/AvailablePhoneNumbers/" + country + "/Local.json?VoiceEnabled=true&PageSize=1")))
                .header("Authorization", authorization())
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio available-number lookup failed with HTTP " + response.statusCode());
        }
        var numbers = objectMapper.readTree(response.body()).path("available_phone_numbers");
        if (!numbers.isArray() || numbers.isEmpty()) {
            throw new IllegalStateException("No Twilio voice numbers are available for " + country);
        }
        return numbers.get(0).path("phone_number").asText();
    }

    private void purchaseNumber(String phoneNumber) throws Exception {
        var voiceUrl = publicBaseUrl + "/webhooks/twilio/voice";
        var statusUrl = publicBaseUrl + "/webhooks/twilio/status";
        var body = "PhoneNumber=" + encode(phoneNumber)
                + "&VoiceUrl=" + encode(voiceUrl)
                + "&VoiceMethod=POST"
                + "&StatusCallback=" + encode(statusUrl)
                + "&StatusCallbackMethod=POST";
        var request = HttpRequest.newBuilder(URI.create(twilioApi("/IncomingPhoneNumbers.json")))
                .header("Authorization", authorization())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Twilio number purchase failed with HTTP " + response.statusCode());
        }
    }

    private String twilioApi(String path) {
        return "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + path;
    }

    private String authorization() {
        return "Basic " + Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String escapeXml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
