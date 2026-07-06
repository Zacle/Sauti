package com.sauti.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.tool.AgentTool;
import com.sauti.tool.ToolFulfillment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DuringCallIntegrationFulfillment implements ToolFulfillment {
    private final IntegrationService integrations;
    private final ProviderOAuthService oauth;
    private final MpesaPaymentRequestRepository payments;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String graphApiBase;
    private final String publicBaseUrl;

    public DuringCallIntegrationFulfillment(IntegrationService integrations,
                                            ProviderOAuthService oauth,
                                            MpesaPaymentRequestRepository payments,
                                            ObjectMapper objectMapper,
                                            @Value("${sauti.whatsapp.graph-api-base-url:https://graph.facebook.com/v23.0}") String graphApiBase,
                                            @Value("${sauti.telephony.public-base-url}") String publicBaseUrl) {
        this.integrations = integrations; this.oauth = oauth; this.payments = payments; this.objectMapper = objectMapper;
        this.graphApiBase = graphApiBase.replaceFirst("/+$", "");
        this.publicBaseUrl = publicBaseUrl.replaceFirst("/+$", "");
    }

    @Override
    @Transactional
    public LlmToolResult execute(Call call, AgentTool tool, LlmToolCall toolCall) {
        try {
            return switch (toolCall.name()) {
                case "send_whatsapp_message" -> whatsapp(call, toolCall);
                case "lookup_google_sheet_row" -> sheets(call, toolCall);
                case "request_mpesa_payment" -> mpesa(call, toolCall);
                case "check_mpesa_payment" -> checkMpesa(call, toolCall);
                case "call_custom_webhook" -> webhook(call, toolCall);
                default -> LlmToolResult.error(toolCall, "Unsupported integration tool");
            };
        } catch (Exception exception) {
            return LlmToolResult.error(toolCall,
                    exception.getMessage() == null ? "Integration request failed" : exception.getMessage());
        }
    }

    private LlmToolResult whatsapp(Call call, LlmToolCall toolCall) throws Exception {
        var runtime = integrations.runtime(call.getTenant().getId(), call.getAgent().getId(), "whatsapp");
        var config = runtime.configuration();
        var to = string(toolCall.arguments().getOrDefault("phone", call.getCallerNumber()));
        var body = Map.of("messaging_product", "whatsapp", "to", to, "type", "template",
                "template", Map.of("name", string(config.get("templateName")),
                        "language", Map.of("code", string(config.get("templateLanguage")))));
        send(HttpRequest.newBuilder(URI.create(graphApiBase + "/" + config.get("phoneNumberId") + "/messages"))
                .header("Authorization", "Bearer " + runtime.credentials().get("accessToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body))).build());
        return LlmToolResult.success(toolCall, Map.of("sent", true, "to", to));
    }

    private LlmToolResult sheets(Call call, LlmToolCall toolCall) throws Exception {
        var runtime = integrations.runtime(call.getTenant().getId(), call.getAgent().getId(), "google_sheets");
        var config = runtime.configuration();
        var spreadsheet = required(config, "spreadsheetId");
        var range = required(config, "range");
        var url = "https://sheets.googleapis.com/v4/spreadsheets/" + encode(spreadsheet)
                + "/values/" + encode(range);
        var response = send(HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + oauth.accessToken(
                        call.getTenant().getId(), call.getAgent().getId(), "google_sheets")).GET().build());
        var rows = objectMapper.readTree(response.body()).path("values");
        var lookup = string(toolCall.arguments().get("lookup_value"));
        int lookupIndex = integer(config.get("lookupColumn"), 0);
        for (JsonNode row : rows) {
            if (row.path(lookupIndex).asText().equalsIgnoreCase(lookup)) {
                return LlmToolResult.success(toolCall, Map.of("found", true,
                        "values", objectMapper.convertValue(row, List.class)));
            }
        }
        return LlmToolResult.success(toolCall, Map.of("found", false));
    }

    private LlmToolResult mpesa(Call call, LlmToolCall toolCall) throws Exception {
        if (!Boolean.TRUE.equals(toolCall.arguments().get("amount_confirmed"))) {
            return LlmToolResult.error(toolCall, "The caller must explicitly confirm the amount");
        }
        var runtime = integrations.runtime(call.getTenant().getId(), call.getAgent().getId(), "mpesa");
        var config = runtime.configuration();
        var credentials = runtime.credentials();
        var amount = new BigDecimal(string(toolCall.arguments().get("amount")));
        var minimum = new BigDecimal(required(config, "minimumAmount"));
        var maximum = new BigDecimal(required(config, "maximumAmount"));
        if (amount.compareTo(minimum) < 0 || amount.compareTo(maximum) > 0) {
            return LlmToolResult.error(toolCall, "Amount is outside the configured payment limits");
        }
        if (amount.scale() > 0 && amount.stripTrailingZeros().scale() > 0) {
            var rounded = amount.setScale(0, RoundingMode.CEILING);
            return LlmToolResult.error(toolCall,
                    "M-Pesa requires a whole-KES amount; ask the caller to confirm " + rounded.toPlainString() + " KES");
        }
        var phone = normalizeKenyanPhone(string(toolCall.arguments().get("phone")));
        var reference = string(toolCall.arguments().get("account_reference"));
        var description = string(toolCall.arguments().get("description"));
        if (reference.isBlank() || description.isBlank()) {
            return LlmToolResult.error(toolCall, "Account reference and description are required");
        }
        var request = payments.save(new MpesaPaymentRequest(call.getTenant().getId(), call.getAgent().getId(),
                call.getId(), runtime.connectionId(), phone, amount, reference, description));
        try {
            var base = "production".equals(config.get("environment"))
                    ? "https://api.safaricom.co.ke" : "https://sandbox.safaricom.co.ke";
            var basic = Base64.getEncoder().encodeToString((credentials.get("consumerKey") + ":"
                    + credentials.get("consumerSecret")).getBytes(StandardCharsets.UTF_8));
            var tokenResponse = send(HttpRequest.newBuilder(URI.create(base + "/oauth/v1/generate?grant_type=client_credentials"))
                    .header("Authorization", "Basic " + basic).GET().build());
            var token = objectMapper.readTree(tokenResponse.body()).path("access_token").asText();
            var timestamp = OffsetDateTime.now(ZoneId.of("Africa/Nairobi"))
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            var shortcode = required(config, "shortcode");
            var password = Base64.getEncoder().encodeToString(
                    (shortcode + credentials.get("passkey") + timestamp).getBytes(StandardCharsets.UTF_8));
            var payload = Map.ofEntries(
                    Map.entry("BusinessShortCode", shortcode), Map.entry("Password", password),
                    Map.entry("Timestamp", timestamp), Map.entry("TransactionType", "CustomerPayBillOnline"),
                    Map.entry("Amount", amount.setScale(0, RoundingMode.UNNECESSARY).intValueExact()), Map.entry("PartyA", phone),
                    Map.entry("PartyB", shortcode), Map.entry("PhoneNumber", phone),
                    Map.entry("CallBackURL", publicBaseUrl + "/webhooks/mpesa/" + runtime.connectionId()),
                    Map.entry("AccountReference", reference.substring(0, Math.min(12, reference.length()))),
                    Map.entry("TransactionDesc", description.substring(0, Math.min(13, description.length()))));
            var stk = send(HttpRequest.newBuilder(URI.create(base + "/mpesa/stkpush/v1/processrequest"))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload))).build());
            var node = objectMapper.readTree(stk.body());
            request.submitted(node.path("MerchantRequestID").asText(), node.path("CheckoutRequestID").asText());
            return LlmToolResult.success(toolCall, Map.of("requested", true, "paymentRequestId", request.getId(),
                    "status", request.getStatus()));
        } catch (Exception exception) {
            request.failed(exception.getMessage());
            throw exception;
        }
    }

    private LlmToolResult webhook(Call call, LlmToolCall toolCall) throws Exception {
        var runtime = integrations.runtime(call.getTenant().getId(), call.getAgent().getId(), "custom_webhook");
        var url = required(runtime.configuration(), "webhookUrl");
        var payload = toolCall.arguments().get("payload");
        var bytes = objectMapper.writeValueAsBytes(payload == null ? Map.of() : payload);
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("X-Sauti-Call-Id", call.getId().toString());
        var authType = string(runtime.configuration().get("authType"));
        if ("bearer".equals(authType)) {
            builder.header("Authorization", "Bearer " + runtime.credentials().get("authToken"));
        } else if ("api_key".equals(authType)) {
            builder.header("X-API-Key", string(runtime.credentials().get("apiKey")));
        } else if ("hmac".equals(authType)) {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(string(runtime.credentials().get("hmacSecret"))
                    .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            builder.header("X-Sauti-Signature", "sha256=" + Base64.getEncoder().encodeToString(mac.doFinal(bytes)));
        }
        var response = send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
        var responseBody = objectMapper.readTree(response.body());
        return LlmToolResult.success(toolCall, Map.of("status", response.statusCode(), "body", responseBody));
    }

    private LlmToolResult checkMpesa(Call call, LlmToolCall toolCall) {
        java.util.UUID requestId;
        try {
            requestId = java.util.UUID.fromString(string(toolCall.arguments().get("payment_request_id")));
        } catch (Exception exception) {
            return LlmToolResult.error(toolCall, "A valid payment request ID is required");
        }
        var request = payments.findById(requestId)
                .filter(item -> call.getId().equals(item.getCallId()))
                .orElse(null);
        if (request == null) return LlmToolResult.error(toolCall, "Payment request was not found for this call");
        return LlmToolResult.success(toolCall, Map.of(
                "paymentRequestId", request.getId(), "status", request.getStatus(),
                "terminal", List.of("completed", "failed", "cancelled").contains(request.getStatus())));
    }

    @Transactional
    public void callback(java.util.UUID connectionId, JsonNode payload) {
        var callback = payload.path("Body").path("stkCallback");
        var checkoutId = callback.path("CheckoutRequestID").asText();
        var request = payments.findByCheckoutRequestIdAndConnectionId(checkoutId, connectionId).orElse(null);
        if (request != null) request.callback(callback.path("ResultCode").asInt(),
                callback.path("ResultDesc").asText());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("Provider returned HTTP " + response.statusCode());
        return response;
    }
    private static String normalizeKenyanPhone(String value) {
        var digits = value.replaceAll("\\D", "");
        if (digits.startsWith("0")) digits = "254" + digits.substring(1);
        if (digits.startsWith("7") || digits.startsWith("1")) digits = "254" + digits;
        if (!digits.matches("254[17]\\d{8}")) throw new IllegalArgumentException("A valid Kenyan recipient number is required");
        return digits;
    }
    private static String required(Map<String, Object> map, String key) {
        var value = string(map.get(key));
        if (value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
    private static int integer(Object value, int fallback) {
        try { return Integer.parseInt(string(value)); } catch (Exception ignored) { return fallback; }
    }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
