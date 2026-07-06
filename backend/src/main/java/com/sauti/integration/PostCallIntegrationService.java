package com.sauti.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.calendar.BookingRepository;
import com.sauti.tool.WebhookDestinationValidator;
import com.sauti.webhook.WebhookDeliveryService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostCallIntegrationService {
    private static final List<String> POST_CALL_PROVIDERS = List.of(
            "custom_webhook", "whatsapp", "email", "slack", "google_sheets", "hubspot", "salesforce");
    private final PostCallJobRepository jobs;
    private final IntegrationDeliveryRepository deliveries;
    private final AgentIntegrationRepository bindings;
    private final IntegrationConnectionRepository connections;
    private final CallRepository calls;
    private final BookingRepository bookings;
    private final IntegrationService integrationService;
    private final ProviderOAuthService oauth;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final WebhookDestinationValidator destinationValidator;
    private final WebhookDeliveryService tenantWebhooks;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String emailFrom;
    private final String graphApiBaseUrl;
    private final String dashboardBaseUrl;

    public PostCallIntegrationService(PostCallJobRepository jobs,
                                      IntegrationDeliveryRepository deliveries,
                                      AgentIntegrationRepository bindings,
                                      IntegrationConnectionRepository connections,
                                      CallRepository calls,
                                      BookingRepository bookings,
                                      IntegrationService integrationService,
                                      ProviderOAuthService oauth,
                                      ObjectMapper objectMapper,
                                      JavaMailSender mailSender,
                                      WebhookDestinationValidator destinationValidator,
                                      WebhookDeliveryService tenantWebhooks,
                                      @Value("${sauti.email.from}") String emailFrom,
                                      @Value("${sauti.whatsapp.graph-api-base-url:https://graph.facebook.com/v23.0}") String graphApiBaseUrl,
                                      @Value("${sauti.dashboard.base-url}") String dashboardBaseUrl) {
        this.jobs = jobs;
        this.deliveries = deliveries;
        this.bindings = bindings;
        this.connections = connections;
        this.calls = calls;
        this.bookings = bookings;
        this.integrationService = integrationService;
        this.oauth = oauth;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.destinationValidator = destinationValidator;
        this.tenantWebhooks = tenantWebhooks;
        this.emailFrom = emailFrom;
        this.graphApiBaseUrl = graphApiBaseUrl.replaceFirst("/+$", "");
        this.dashboardBaseUrl = dashboardBaseUrl.replaceFirst("/+$", "");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(UUID tenantId, UUID callId, boolean test) {
        var existing = jobs.findByCallId(callId);
        if (existing.isEmpty()) {
            jobs.save(new PostCallJob(tenantId, callId, test));
            calls.findById(callId).ifPresent(tenantWebhooks::callCompleted);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void analysisCompleted(UUID callId) {
        jobs.findByCallId(callId).ifPresent(job -> {
            job.ready();
            jobs.save(job);
        });
        calls.findById(callId).ifPresent(tenantWebhooks::callAnalysed);
    }

    @Scheduled(fixedDelayString = "${sauti.integrations.worker-delay-ms:5000}")
    @Transactional
    public void prepareDeliveries() {
        jobs.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                List.of("pending_analysis", "ready"), OffsetDateTime.now()).forEach(job -> {
            try {
                var call = calls.findById(job.getCallId()).orElseThrow();
                // A pending job is released after analysis or after a short grace period when analysis is disabled.
                if ("pending_analysis".equals(job.getStatus())
                        && job.getNextAttemptAt().plusSeconds(30).isAfter(OffsetDateTime.now())) return;
                bindings.findAllByTenantIdAndAgentIdAndEnabledTrue(job.getTenantId(), call.getAgent().getId())
                        .stream().filter(binding -> POST_CALL_PROVIDERS.contains(binding.getProvider()))
                        .filter(binding -> binding.getConnectionId() != null || "email".equals(binding.getProvider()))
                        .forEach(binding -> {
                            if (!deliveries.existsByAgentIntegrationIdAndCallId(binding.getId(), call.getId())) {
                                deliveries.save(new IntegrationDelivery(job, binding));
                            }
                        });
                job.completed();
            } catch (Exception exception) {
                job.failed(exception.getMessage());
            }
        });
    }

    @Scheduled(fixedDelayString = "${sauti.integrations.delivery-delay-ms:5000}")
    @Transactional
    public void deliverDue() {
        deliveries.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                List.of("pending", "retrying"), OffsetDateTime.now()).forEach(this::deliver);
    }

    private void deliver(IntegrationDelivery delivery) {
        try {
            var call = calls.findById(delivery.getCallId()).orElseThrow();
            var binding = bindings.findById(delivery.getAgentIntegrationId()).orElseThrow();
            var connection = binding.getConnectionId() == null ? null
                    : connections.findById(binding.getConnectionId()).orElseThrow();
            var payload = payload(call, "test".equals(call.getDirection()));
            int responseCode = switch (delivery.getProvider()) {
                case "email" -> sendEmail(call, map(binding.getConfiguration()));
                case "slack" -> post(String.valueOf(integrationService.credentials(connection).get("webhookUrl")),
                        slackPayload(call), Map.of());
                case "custom_webhook" -> post(String.valueOf(map(connection.getConfiguration()).get("webhookUrl")),
                        payload, Map.of("X-Sauti-Event", "call.analysed",
                                "X-Sauti-Delivery", delivery.getId().toString()));
                case "whatsapp" -> sendWhatsApp(call, connection);
                case "google_sheets" -> appendSheet(call, binding, connection);
                case "hubspot" -> syncHubSpot(call, connection);
                case "salesforce" -> syncSalesforce(call, connection);
                default -> throw new IllegalStateException(
                        delivery.getProvider() + " delivery requires its OAuth provider adapter");
            };
            delivery.delivered(responseCode);
        } catch (ProviderException exception) {
            delivery.retry(exception.statusCode, exception.getMessage());
        } catch (Exception exception) {
            delivery.retry(null, safeMessage(exception));
        }
    }

    private int sendEmail(Call call, Map<String, Object> configuration) {
        var recipients = stringList(configuration.get("recipients"));
        if (recipients.isEmpty()) recipients = List.of(call.getTenant().getEmail());
        var message = new SimpleMailMessage();
        message.setFrom(emailFrom);
        message.setTo(recipients.toArray(String[]::new));
        message.setSubject((isTest(call) ? "[TEST] " : "") + "Call completed: " + call.getAgent().getName());
        message.setText(slackText(call));
        mailSender.send(message);
        return 202;
    }

    private int sendWhatsApp(Call call, IntegrationConnection connection) {
        if (call.getCallerNumber() == null || call.getCallerNumber().isBlank()) {
            throw new IllegalArgumentException("Call has no recipient phone number");
        }
        var config = map(connection.getConfiguration());
        var credentials = integrationService.credentials(connection);
        Map<String, Object> message;
        if ("whatsapp".equals(call.getDirection())) {
            var text = call.getCallSummary() == null || call.getCallSummary().isBlank()
                    ? "Thank you for contacting " + call.getTenant().getBusinessName() + "."
                    : call.getCallSummary();
            message = Map.of(
                    "messaging_product", "whatsapp", "to", call.getCallerNumber(),
                    "type", "text", "text", Map.of("preview_url", false, "body", text));
        } else {
            var template = Map.of(
                    "name", required(config, "templateName"),
                    "language", Map.of("code", required(config, "templateLanguage")));
            message = Map.of(
                    "messaging_product", "whatsapp", "to", call.getCallerNumber(),
                    "type", "template", "template", template);
        }
        return post(graphApiBaseUrl + "/" + required(config, "phoneNumberId") + "/messages", message,
                Map.of("Authorization", "Bearer " + credentials.get("accessToken")));
    }

    private int appendSheet(Call call, AgentIntegration binding, IntegrationConnection connection) {
        var config = new LinkedHashMap<>(map(connection.getConfiguration()));
        config.putAll(map(binding.getConfiguration()));
        var spreadsheet = required(config, "spreadsheetId");
        var range = required(config, "range");
        var columns = stringList(config.get("appendColumns"));
        if (columns.isEmpty()) columns = List.of("startedAt", "callerPhone", "outcome", "summary", "sentiment");
        var values = columns.stream().map(column -> callValue(call, column)).toList();
        var url = "https://sheets.googleapis.com/v4/spreadsheets/"
                + urlPart(spreadsheet) + "/values/" + urlPart(range)
                + ":append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS";
        return post(url, Map.of("values", List.of(values)), Map.of(
                "Authorization", "Bearer " + oauth.accessToken(
                        call.getTenant().getId(), call.getAgent().getId(), "google_sheets")));
    }

    private int syncHubSpot(Call call, IntegrationConnection connection) {
        var token = oauth.accessToken(call.getTenant().getId(), call.getAgent().getId(), "hubspot");
        var headers = Map.of("Authorization", "Bearer " + token);
        var name = callerName(call);
        var nameParts = splitName(name);
        var search = requestJson("POST", "https://api.hubapi.com/crm/v3/objects/contacts/search", Map.of(
                "filterGroups", List.of(Map.of("filters", List.of(Map.of(
                        "propertyName", "phone", "operator", "EQ", "value", value(call.getCallerNumber()))))),
                "properties", List.of("phone", "firstname", "lastname"), "limit", 1), headers);
        var contactId = search.body().path("results").path(0).path("id").asText("");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("phone", value(call.getCallerNumber()));
        properties.put("firstname", nameParts[0]);
        properties.put("lastname", nameParts[1]);
        if (contactId.isBlank()) {
            contactId = requestJson("POST", "https://api.hubapi.com/crm/v3/objects/contacts",
                    Map.of("properties", properties), headers).body().path("id").asText("");
        } else {
            requestJson("PATCH", "https://api.hubapi.com/crm/v3/objects/contacts/" + urlPart(contactId),
                    Map.of("properties", properties), headers);
        }
        if (contactId.isBlank()) throw new IllegalStateException("HubSpot returned no contact ID");
        var noteBody = crmNote(call);
        var note = Map.of(
                "properties", Map.of(
                        "hs_timestamp", OffsetDateTime.now().toInstant().toString(),
                        "hs_note_body", noteBody),
                "associations", List.of(Map.of(
                        "to", Map.of("id", contactId),
                        "types", List.of(Map.of(
                                "associationCategory", "HUBSPOT_DEFINED",
                                "associationTypeId", 202)))));
        return requestJson("POST", "https://api.hubapi.com/crm/v3/objects/notes", note, headers).statusCode();
    }

    private int syncSalesforce(Call call, IntegrationConnection connection) {
        var credentials = integrationService.credentials(connection);
        var token = oauth.accessToken(call.getTenant().getId(), call.getAgent().getId(), "salesforce");
        var instanceUrl = required(credentials, "instanceUrl").replaceFirst("/+$", "");
        var headers = Map.of("Authorization", "Bearer " + token);
        var phone = value(call.getCallerNumber()).replace("'", "\\'");
        var queryUrl = instanceUrl + "/services/data/v61.0/query?q=" + urlPart(
                "SELECT Id FROM Contact WHERE Phone = '" + phone + "' LIMIT 1");
        var contactId = requestJson("GET", queryUrl, null, headers)
                .body().path("records").path(0).path("Id").asText("");
        var nameParts = splitName(callerName(call));
        var contactBody = Map.of(
                "FirstName", nameParts[0], "LastName", nameParts[1],
                "Phone", value(call.getCallerNumber()), "Description", value(call.getCallSummary()));
        var contactRequest = contactId.isBlank()
                ? Map.of("method", "POST", "url", "/services/data/v61.0/sobjects/Contact",
                        "referenceId", "contact", "body", contactBody)
                : Map.of("method", "PATCH", "url", "/services/data/v61.0/sobjects/Contact/" + contactId,
                        "referenceId", "contact", "body", contactBody);
        var whoId = contactId.isBlank() ? "@{contact.id}" : contactId;
        var taskRequest = Map.of(
                "method", "POST", "url", "/services/data/v61.0/sobjects/Task",
                "referenceId", "callNote", "body", Map.of(
                        "WhoId", whoId, "Subject", "Sauti call — " + value(call.getOutcome()),
                        "Description", crmNote(call), "Status", "Completed", "Priority", "Normal",
                        "ActivityDate", LocalDate.now().toString()));
        var composite = List.of(contactRequest, taskRequest);
        return post(instanceUrl + "/services/data/v61.0/composite", Map.of(
                "allOrNone", false, "compositeRequest", composite),
                Map.of("Authorization", "Bearer " + token));
    }

    private JsonResponse requestJson(String method, String url, Map<String, ?> payload, Map<String, String> headers) {
        try {
            destinationValidator.validateHttpsPublicUrl(url);
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            headers.forEach(builder::header);
            if (payload != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ProviderException(response.statusCode(),
                        "Provider returned HTTP " + response.statusCode() + ": " + trimProviderError(response.body()));
            }
            return new JsonResponse(response.statusCode(),
                    response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body()));
        } catch (ProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Provider request failed", exception);
        }
    }

    private int post(String url, Map<String, ?> payload, Map<String, String> headers) {
        try {
            destinationValidator.validateHttpsPublicUrl(url);
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)));
            headers.forEach(builder::header);
            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new ProviderException(response.statusCode(), "Provider returned HTTP " + response.statusCode());
            }
            return response.statusCode();
        } catch (ProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Provider request failed", exception);
        }
    }

    private Map<String, Object> payload(Call call, boolean test) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("event", "call.analysed");
        payload.put("test", test);
        payload.put("callId", call.getId());
        payload.put("agentId", call.getAgent().getId());
        payload.put("callerPhone", call.getCallerNumber());
        payload.put("direction", call.getDirection());
        payload.put("outcome", call.getOutcome());
        payload.put("summary", call.getCallSummary());
        payload.put("sentiment", call.getSentiment());
        payload.put("intent", call.getIntent());
        payload.put("transcript", call.getTranscript());
        payload.put("recordingUrl", call.getRecordingUrl());
        payload.put("startedAt", call.getStartedAt());
        payload.put("endedAt", call.getEndedAt());
        return payload;
    }

    private String slackText(Call call) {
        return "%sCall %s\nAgent: %s\nCaller: %s\nOutcome: %s\nSentiment: %s\nSummary: %s".formatted(
                isTest(call) ? "[TEST] " : "", call.getId(), call.getAgent().getName(),
                value(call.getCallerNumber()), value(call.getOutcome()), value(call.getSentiment()),
                value(call.getCallSummary()));
    }

    private Map<String, Object> slackPayload(Call call) {
        var callUrl = dashboardBaseUrl + "/calls?callId=" + call.getId();
        var actions = new java.util.ArrayList<Map<String, Object>>();
        actions.add(Map.of("type", "button", "text", Map.of("type", "plain_text", "text", "View transcript"),
                "url", callUrl, "action_id", "view_call"));
        if (call.getRecordingUrl() != null && !call.getRecordingUrl().isBlank()) {
            actions.add(Map.of("type", "button", "text", Map.of("type", "plain_text", "text", "Listen to recording"),
                    "url", call.getRecordingUrl(), "action_id", "listen_recording"));
        }
        var blocks = new java.util.ArrayList<Map<String, Object>>();
        blocks.add(Map.of("type", "header", "text", Map.of("type", "plain_text",
                "text", (isTest(call) ? "[TEST] " : "") + call.getAgent().getName() + " — " + value(call.getOutcome()))));
        blocks.add(Map.of("type", "section", "fields", List.of(
                Map.of("type", "mrkdwn", "text", "*Caller*\n" + value(call.getCallerNumber())),
                Map.of("type", "mrkdwn", "text", "*Sentiment*\n" + value(call.getSentiment())))));
        blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn",
                "text", "*Summary*\n" + value(call.getCallSummary()))));
        blocks.add(Map.of("type", "actions", "elements", actions));
        return Map.of("text", slackText(call), "blocks", blocks);
    }

    private String callerName(Call call) {
        return bookings.findFirstByCall_Id(call.getId()).map(booking -> booking.getCallerName())
                .filter(name -> name != null && !name.isBlank()).orElse("Sauti Caller");
    }

    private String[] splitName(String name) {
        var normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) return new String[]{"Sauti", "Caller"};
        var separator = normalized.lastIndexOf(' ');
        return separator < 0
                ? new String[]{"", normalized}
                : new String[]{normalized.substring(0, separator), normalized.substring(separator + 1)};
    }

    private String crmNote(Call call) {
        return """
                Summary: %s
                Outcome: %s
                Sentiment: %s
                Transcript: %s/calls?callId=%s
                Recording: %s
                """.formatted(value(call.getCallSummary()), value(call.getOutcome()), value(call.getSentiment()),
                dashboardBaseUrl, call.getId(), value(call.getRecordingUrl()));
    }

    private String trimProviderError(String body) {
        if (body == null) return "";
        return body.length() <= 500 ? body : body.substring(0, 500);
    }

    private Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Stored integration configuration is invalid"); }
    }
    private List<String> stringList(Object value) {
        if (value instanceof String text) {
            return java.util.Arrays.stream(text.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
        }
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }
    private Object callValue(Call call, String column) {
        return switch (column) {
            case "callId" -> call.getId();
            case "agentId" -> call.getAgent().getId();
            case "callerPhone" -> value(call.getCallerNumber());
            case "direction" -> call.getDirection();
            case "outcome" -> value(call.getOutcome());
            case "summary" -> value(call.getCallSummary());
            case "sentiment" -> value(call.getSentiment());
            case "intent" -> value(call.getIntent());
            case "transcript" -> value(call.getTranscript());
            case "recordingUrl" -> value(call.getRecordingUrl());
            case "endedAt" -> value(call.getEndedAt());
            default -> value(call.getStartedAt());
        };
    }
    private static String required(Map<String, Object> values, String key) {
        var value = values.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalStateException(key + " is required");
        return String.valueOf(value);
    }
    private static String urlPart(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static boolean isTest(Call call) { return "test".equals(call.getDirection()); }
    private static String value(Object value) { return value == null ? "—" : String.valueOf(value); }
    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static final class ProviderException extends RuntimeException {
        private final int statusCode;
        private ProviderException(int statusCode, String message) { super(message); this.statusCode = statusCode; }
    }
    private record JsonResponse(int statusCode, com.fasterxml.jackson.databind.JsonNode body) {}
}
