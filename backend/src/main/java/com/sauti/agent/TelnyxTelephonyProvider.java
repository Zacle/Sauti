package com.sauti.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.call.ManagedVoiceAgentProvisioningService;
import com.sauti.voice.TelnyxVoiceCompatibility;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "telnyx")
public class TelnyxTelephonyProvider implements TelephonyProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelnyxTelephonyProvider.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String connectionId;
    private final String apiBaseUrl;
    private final String requirementGroupId;
    private final ManagedVoiceAgentProvisioningService provisioningService;
    private final String defaultVoiceId;

    public TelnyxTelephonyProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.telnyx.api-key:}") String apiKey,
            @Value("${sauti.telnyx.connection-id:}") String connectionId,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String apiBaseUrl,
            @Value("${sauti.telnyx.requirement-group-id:}") String requirementGroupId,
            @Value("${sauti.telnyx.default-voice-id:Telnyx.NaturalHD.astra}") String defaultVoiceId,
            ManagedVoiceAgentProvisioningService provisioningService
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = blank(apiKey);
        this.connectionId = blank(connectionId);
        this.apiBaseUrl = apiBaseUrl.replaceFirst("/+$", "");
        this.requirementGroupId = blank(requirementGroupId);
        this.provisioningService = provisioningService;
        this.defaultVoiceId = blank(defaultVoiceId);
    }

    @Override
    public String provisionNumber(String tenantCountryCode) {
        return provisionPhoneNumber(tenantCountryCode, null).phoneNumber();
    }

    @Override
    public String provisionNumber(String tenantCountryCode, String requestedPhoneNumber) {
        return provisionPhoneNumber(tenantCountryCode, requestedPhoneNumber).phoneNumber();
    }

    @Override
    public TelephonyProvider.PhoneNumberProvisioning provisionPhoneNumber(
            String tenantCountryCode,
            String requestedPhoneNumber
    ) {
        var phoneNumber = requestedPhoneNumber;
        if (phoneNumber == null || phoneNumber.isBlank()) {
            var numbers = searchAvailableNumbers(tenantCountryCode, 1);
            if (numbers.isEmpty()) {
                throw new IllegalStateException("No Telnyx voice numbers are available for " + tenantCountryCode);
            }
            phoneNumber = numbers.get(0).phoneNumber();
        }
        var order = createNumberOrder(phoneNumber);
        return provisioning(order);
    }

    @Override
    public TelephonyProvider.PhoneNumberProvisioning refreshPhoneNumber(String providerReference) {
        if (providerReference == null || providerReference.isBlank()) return null;
        requireConfigured();
        return provisioning(readNumberOrder(providerReference));
    }

    @Override
    public List<TelephonyProvider.AvailablePhoneNumber> searchAvailableNumbers(String countryCode, int limit) {
        requireConfigured();
        var country = countryCode == null || countryCode.isBlank() ? "US" : countryCode.toUpperCase();
        var uri = apiBaseUrl + "/available_phone_numbers"
                + "?filter%5Bcountry_code%5D=" + encode(country)
                + "&filter%5Bfeatures%5D=voice"
                + "&filter%5Blimit%5D=" + Math.max(1, Math.min(20, limit));
        var response = send("GET", uri, null);
        var result = new ArrayList<TelephonyProvider.AvailablePhoneNumber>();
        for (var number : response.withArray("data")) {
            result.add(new TelephonyProvider.AvailablePhoneNumber(
                    number.path("phone_number").asText(""),
                    number.path("phone_number_type").asText("local"),
                    number.path("locality").asText(""),
                    number.path("region_information").isArray() && !number.path("region_information").isEmpty()
                            ? number.path("region_information").get(0).path("region_name").asText("")
                            : "",
                    number.path("cost_information").path("upfront_cost").asText(""),
                    number.path("cost_information").path("monthly_cost").asText(""),
                    number.path("cost_information").path("currency").asText("")
            ));
        }
        return List.copyOf(result);
    }

    public NumberOrder createNumberOrder(String phoneNumber) {
        requireConfigured();
        if (phoneNumber == null || !phoneNumber.matches("^\\+[1-9]\\d{6,14}$")) {
            throw new IllegalArgumentException("A valid E.164 phone number is required");
        }
        var number = new LinkedHashMap<String, Object>();
        number.put("phone_number", phoneNumber);
        if (!requirementGroupId.isBlank()) {
            number.put("requirement_group_id", requirementGroupId);
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("phone_numbers", List.of(number));
        body.put("connection_id", connectionId);
        body.put("customer_reference", "sauti-" + UUID.randomUUID());
        var response = send("POST", apiBaseUrl + "/number_orders", body).path("data");
        return numberOrder(response, phoneNumber);
    }

    private NumberOrder readNumberOrder(String orderId) {
        var response = send("GET", apiBaseUrl + "/number_orders/" + encodePath(orderId), null).path("data");
        return numberOrder(response, "");
    }

    private NumberOrder numberOrder(JsonNode response, String fallbackPhoneNumber) {
        var orderedNumber = response.path("phone_numbers").isArray() && !response.path("phone_numbers").isEmpty()
                ? response.path("phone_numbers").get(0)
                : objectMapper.createObjectNode();
        var status = response.path("status").asText(orderedNumber.path("status").asText("pending"));
        return new NumberOrder(
                response.path("id").asText(""),
                status,
                orderedNumber.path("phone_number").asText(fallbackPhoneNumber),
                response.path("requirements_met").asBoolean(orderedNumber.path("requirements_met").asBoolean(false))
        );
    }

    private TelephonyProvider.PhoneNumberProvisioning provisioning(NumberOrder order) {
        var status = "success".equalsIgnoreCase(order.status()) ? "active"
                : "failure".equalsIgnoreCase(order.status()) ? "failed"
                : order.status().toLowerCase(java.util.Locale.ROOT);
        return new TelephonyProvider.PhoneNumberProvisioning(
                order.phoneNumber(),
                "telnyx",
                order.id(),
                status,
                order.requirementsMet()
        );
    }

    public void answerInboundCall(Call call, String callControlId, String greeting) {
        requireConfigured();
        // Resolve and, when necessary, synchronize the managed assistant while
        // the inbound leg is still ringing. Doing this after answer creates
        // several seconds of dead air for the caller on a cold configuration.
        var startedAt = System.nanoTime();
        var preparedAssistant = prepareAiAssistant(call, greeting);
        var preparedAt = System.nanoTime();
        var body = new LinkedHashMap<String, Object>();
        body.put("command_id", UUID.randomUUID().toString());
        if (call.getAgent().isRecordCalls()) {
            body.put("record", "record-from-answer");
            body.put("record_channels", "dual");
            body.put("record_format", "mp3");
            body.put("record_track", "both");
        }
        send("POST", apiBaseUrl + "/calls/" + encodePath(callControlId) + "/actions/answer", body);
        var answeredAt = System.nanoTime();
        startAiAssistant(call, callControlId, preparedAssistant);
        var assistantStartedAt = System.nanoTime();
        LOGGER.info(
                "Telnyx inbound startup preparedMs={} answerMs={} assistantStartMs={}",
                elapsedMilliseconds(startedAt, preparedAt),
                elapsedMilliseconds(preparedAt, answeredAt),
                elapsedMilliseconds(answeredAt, assistantStartedAt)
        );
    }

    public void startAiAssistant(Call call, String callControlId, String greeting) {
        startAiAssistant(call, callControlId, prepareAiAssistant(call, greeting));
    }

    private PreparedAiAssistant prepareAiAssistant(Call call, String greeting) {
        var managed = provisioningService.existing(call);
        return new PreparedAiAssistant(
                managed.externalAgentId(),
                greeting == null ? "" : greeting.trim()
        );
    }

    private void startAiAssistant(
            Call call,
            String callControlId,
            PreparedAiAssistant prepared
    ) {
        var assistant = new LinkedHashMap<String, Object>();
        assistant.put("id", prepared.externalAgentId());
        assistant.put("dynamic_variables", Map.of("sauti_call_sid", call.getTwilioCallSid()));
        var body = new LinkedHashMap<String, Object>();
        body.put("assistant", Map.copyOf(assistant));
        body.put("greeting", prepared.greeting());
        var language = call.getLanguageDetected() == null || call.getLanguageDetected().isBlank()
                ? call.getAgent().getDefaultLanguage()
                : call.getLanguageDetected();
        var configuredVoice = blank(call.getAgent().getTtsVoiceId());
        body.put(
                "voice",
                TelnyxVoiceCompatibility.select(
                        configuredVoice,
                        language,
                        defaultVoiceId
                )
        );
        body.put("transcription", Map.of(
                "model", "deepgram/nova-3",
                "language", "auto"
        ));
        body.put("send_message_history_updates", true);
        body.put("client_state", java.util.Base64.getEncoder().encodeToString(
                call.getTwilioCallSid().getBytes(StandardCharsets.UTF_8)
        ));
        body.put("command_id", UUID.randomUUID().toString());
        send(
                "POST",
                apiBaseUrl + "/calls/" + encodePath(callControlId) + "/actions/ai_assistant_start",
                body
        );
    }

    @Override
    public String createOutboundCall(String to, String from, String clientState) {
        requireConfigured();
        if (to == null || !to.matches("^\\+[1-9]\\d{6,14}$")) {
            throw new IllegalArgumentException("A valid outbound E.164 destination is required");
        }
        if (from == null || !from.matches("^\\+[1-9]\\d{6,14}$")) {
            throw new IllegalArgumentException("A valid Telnyx E.164 caller number is required");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("connection_id", connectionId);
        body.put("to", to);
        body.put("from", from);
        body.put("command_id", UUID.randomUUID().toString());
        if (clientState != null && !clientState.isBlank()) {
            body.put("client_state", java.util.Base64.getEncoder().encodeToString(
                    clientState.getBytes(StandardCharsets.UTF_8)
            ));
        }
        var data = send("POST", apiBaseUrl + "/calls", body).path("data");
        var callControlId = data.path("call_control_id").asText("").trim();
        if (callControlId.isBlank()) {
            throw new IllegalStateException("Telnyx did not return a call control id");
        }
        return callControlId;
    }

    @Override
    public void transfer(String callControlId, String to, String from) {
        requireConfigured();
        var body = new LinkedHashMap<String, Object>();
        body.put("to", to);
        if (from != null && !from.isBlank()) body.put("from", from);
        body.put("command_id", UUID.randomUUID().toString());
        send(
                "POST",
                apiBaseUrl + "/calls/" + encodePath(callControlId) + "/actions/transfer",
                body
        );
    }

    public void hangup(String callControlId) {
        requireConfigured();
        send(
                "POST",
                apiBaseUrl + "/calls/" + encodePath(callControlId) + "/actions/hangup",
                Map.of("command_id", UUID.randomUUID().toString())
        );
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
        var parameters = new StringBuilder()
                .append("<Parameter name=\"callSid\" value=\"").append(escapeXml(callSid)).append("\"/>")
                .append("<Parameter name=\"tenantId\" value=\"").append(escapeXml(tenantId)).append("\"/>")
                .append("<Parameter name=\"agentId\" value=\"").append(escapeXml(agentId)).append("\"/>");
        extraParameters.forEach((key, value) -> parameters
                .append("<Parameter name=\"").append(escapeXml(key))
                .append("\" value=\"").append(escapeXml(value)).append("\"/>"));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                  <Connect>
                    <Stream url="%s" track="inbound_track" bidirectionalMode="rtp"
                            bidirectionalCodec="PCMU" bidirectionalSamplingRate="8000">
                      %s
                    </Stream>
                  </Connect>
                </Response>
                """.formatted(escapeXml(websocketUrl), parameters);
    }

    private JsonNode send(String method, String url, Object body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json");
            if ("POST".equals(method)) {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            } else {
                builder.GET();
            }
            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Telnyx API request failed with HTTP " + response.statusCode());
            }
            return response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telnyx API request was interrupted", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to call the Telnyx API", exception);
        }
    }

    private void requireConfigured() {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("TELNYX_API_KEY is required when SAUTI_TELEPHONY_PROVIDER=telnyx");
        }
        if (connectionId.isBlank()) {
            throw new IllegalStateException("TELNYX_CONNECTION_ID is required when SAUTI_TELEPHONY_PROVIDER=telnyx");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private long elapsedMilliseconds(long startedAt, long endedAt) {
        return Math.max(0, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(endedAt - startedAt));
    }

    private String escapeXml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public record NumberOrder(String id, String status, String phoneNumber, boolean requirementsMet) {
    }

    private record PreparedAiAssistant(String externalAgentId, String greeting) {
    }
}
