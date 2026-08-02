package com.sauti.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.CallRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderCostReconciliationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderCostReconciliationService.class);
    private final ProviderCostReconciliationRepository jobs;
    private final BillingLedgerService ledger;
    private final ProviderCostRateCard rateCard;
    private final CallRepository calls;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String telnyxApiKey;
    private final String telnyxApiBase;
    private final int maxAttempts;

    public ProviderCostReconciliationService(
            ProviderCostReconciliationRepository jobs,
            BillingLedgerService ledger,
            ProviderCostRateCard rateCard,
            CallRepository calls,
            ObjectMapper objectMapper,
            @Value("${sauti.telnyx.api-key:}") String telnyxApiKey,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String telnyxApiBase,
            @Value("${sauti.billing.reconciliation.max-attempts:8}") int maxAttempts) {
        this.jobs = jobs;
        this.ledger = ledger;
        this.rateCard = rateCard;
        this.calls = calls;
        this.objectMapper = objectMapper;
        this.telnyxApiKey = telnyxApiKey == null ? "" : telnyxApiKey.trim();
        this.telnyxApiBase = telnyxApiBase.replaceFirst("/+$", "");
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueTelnyxVoice(UUID tenantId, UUID callId, String callSessionId, OffsetDateTime occurredAt) {
        enqueue(tenantId, "telnyx", "voice_session", callSessionId, callId.toString(), occurredAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueTelnyxVoiceByCallControlId(String callControlId, String callSessionId,
                                                  OffsetDateTime occurredAt) {
        calls.findByTwilioCallSid(callControlId).ifPresent(call -> enqueue(
                call.getTenant().getId(), "telnyx", "voice_session", callSessionId,
                call.getId().toString(), occurredAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueTelnyxMessage(UUID tenantId, String providerMessageId) {
        enqueue(tenantId, "telnyx", "sms_message", providerMessageId, providerMessageId, OffsetDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileTelnyxFinalizedMessage(String providerMessageId, JsonNode payload) {
        if (providerMessageId == null || providerMessageId.isBlank()) return;
        var job = jobs.findFirstByProviderAndResourceTypeAndProviderResourceId(
                "telnyx", "sms_message", providerMessageId).orElse(null);
        if (job == null) {
            LOGGER.warn("No tenant-scoped reconciliation job for finalized Telnyx messageId={}", providerMessageId);
            return;
        }
        var cost = payload.path("cost");
        var amount = decimal(cost.path("amount").asText(""));
        var currency = cost.path("currency").asText("");
        if (amount == null || currency.isBlank()) return;
        reconcile(job, amount, currency, Map.of(
                "source", "message.finalized",
                "parts", payload.path("parts").asInt(1),
                "carrierFee", payload.path("cost_breakdown").path("carrier_fee").path("amount").asText(""),
                "rate", payload.path("cost_breakdown").path("rate").path("amount").asText("")
        ));
    }

    public void recordMessageRateCardEstimate(UUID tenantId, String channel, String reference) {
        var resourceType = "sms".equals(channel) ? "sms_message" : "whatsapp_message";
        rateCard.estimate(resourceType, BigDecimal.ONE).ifPresent(estimate ->
                ledger.recordRateCardCost(
                        tenantId, channel + "_provider_cost", BigDecimal.ONE, "message",
                        estimate.amount(), estimate.currency(),
                        "rate-card:" + channel + ":" + reference, reference,
                        "Configured " + channel + " cost estimate pending provider reconciliation",
                        Map.of("unitRate", estimate.unitRate(), "source", "workspace_rate_card")
                ));
    }

    @Scheduled(fixedDelayString = "${sauti.billing.reconciliation.poll-delay-ms:30000}")
    public void reconcileDue() {
        jobs.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                List.of("pending", "retrying"), OffsetDateTime.now()).forEach(this::poll);
    }

    private void poll(ProviderCostReconciliationJob job) {
        try {
            if (telnyxApiKey.isBlank()) throw new IllegalStateException("Telnyx API key is unavailable");
            var response = request(job);
            if (response.statusCode() == 404 || response.statusCode() == 409) {
                retryOrEstimate(job, "Provider cost is not finalized yet");
                return;
            }
            if (response.statusCode() / 100 != 2) {
                retryOrEstimate(job, "Telnyx cost lookup returned HTTP " + response.statusCode());
                return;
            }
            var root = objectMapper.readTree(response.body());
            var cost = "voice_session".equals(job.getResourceType())
                    ? root.path("cost") : root.path("data").path("cost");
            var amount = decimal("voice_session".equals(job.getResourceType())
                    ? cost.path("total").asText("") : cost.path("amount").asText(""));
            var currency = cost.path("currency").asText("");
            if (amount == null || currency.isBlank()) {
                retryOrEstimate(job, "Provider cost is not finalized yet");
                return;
            }
            reconcile(job, amount, currency, Map.of(
                    "source", "telnyx_api",
                    "sessionId", root.path("session_id").asText(""),
                    "products", root.path("meta").path("products")
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            retryOrEstimate(job, "Provider cost lookup was interrupted");
        } catch (Exception exception) {
            retryOrEstimate(job, exception.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void reconcile(ProviderCostReconciliationJob job, BigDecimal confirmedAmount, String currency,
                             Map<String, Object> metadata) {
        if (confirmedAmount.signum() < 0) throw new IllegalArgumentException("Provider cost cannot be negative");
        var category = category(job.getResourceType());
        var current = ledger.amountTotal(job.getTenantId(), category, job.getLocalReference(), currency);
        var adjustment = confirmedAmount.subtract(current);
        var key = "provider-cost:telnyx:" + job.getResourceType() + ":" + job.getProviderResourceId()
                + ":" + confirmedAmount.stripTrailingZeros().toPlainString() + ":" + currency;
        if (adjustment.signum() >= 0) {
            ledger.recordProviderCost(job.getTenantId(), category, adjustment, currency, key,
                    job.getLocalReference(), "Telnyx-confirmed provider cost", metadata);
        } else {
            ledger.recordProviderCostCredit(job.getTenantId(), category, adjustment.abs(), currency,
                    key + ":credit", job.getLocalReference(),
                    "Telnyx provider cost correction", metadata);
        }
        job.reconciled();
        jobs.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void retryOrEstimate(ProviderCostReconciliationJob job, String error) {
        if (job.getAttempts() + 1 < maxAttempts) {
            var delaySeconds = Math.min(21600, 30L << Math.min(job.getAttempts(), 9));
            job.retry(error, OffsetDateTime.now().plusSeconds(delaySeconds));
            jobs.save(job);
            return;
        }
        var quantityCategory = "voice_session".equals(job.getResourceType()) ? "voice_call" : "sms_message";
        var quantity = ledger.quantityTotal(job.getTenantId(), quantityCategory, job.getLocalReference());
        var estimate = rateCard.estimate(job.getResourceType(), quantity);
        if (estimate.isPresent()) {
            var value = estimate.get();
            ledger.recordRateCardCost(
                    job.getTenantId(), category(job.getResourceType()), quantity,
                    "voice_session".equals(job.getResourceType()) ? "minute" : "message",
                    value.amount(), value.currency(),
                    "rate-card:" + job.getProvider() + ":" + job.getResourceType() + ":" + job.getProviderResourceId(),
                    job.getLocalReference(), "Configured provider cost estimate after reconciliation timeout",
                    Map.of("unitRate", value.unitRate(), "source", "workspace_rate_card")
            );
            job.estimated(error);
        } else {
            job.unavailable(error);
        }
        jobs.save(job);
    }

    private void enqueue(UUID tenantId, String provider, String type, String providerId,
                         String localReference, OffsetDateTime occurredAt) {
        if (providerId == null || providerId.isBlank()) return;
        if (jobs.findByTenantIdAndProviderAndResourceTypeAndProviderResourceId(
                tenantId, provider, type, providerId).isPresent()) return;
        try {
            jobs.saveAndFlush(new ProviderCostReconciliationJob(
                    tenantId, provider, type, providerId, localReference, occurredAt));
        } catch (DataIntegrityViolationException ignored) {
            // Provider resource IDs are idempotent across webhook retries and send acknowledgements.
        }
    }

    private HttpResponse<String> request(ProviderCostReconciliationJob job) throws Exception {
        var path = "voice_session".equals(job.getResourceType())
                ? "/session_analysis/call-session/" + encode(job.getProviderResourceId())
                    + "?include_children=true&max_depth=5&expand=none"
                    + (job.getResourceOccurredAt() == null ? "" : "&date_time=" + encode(job.getResourceOccurredAt().toString()))
                : "/messages/" + encode(job.getProviderResourceId());
        var request = HttpRequest.newBuilder(URI.create(telnyxApiBase + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + telnyxApiKey)
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String category(String resourceType) {
        return "voice_session".equals(resourceType) ? "voice_provider_cost" : "sms_provider_cost";
    }
    private static BigDecimal decimal(String value) {
        try { return value == null || value.isBlank() ? null : new BigDecimal(value); }
        catch (NumberFormatException ignored) { return null; }
    }
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
