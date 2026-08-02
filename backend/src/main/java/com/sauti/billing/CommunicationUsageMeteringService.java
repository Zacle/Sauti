package com.sauti.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.call.CallRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CommunicationUsageMeteringService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommunicationUsageMeteringService.class);
    private static final DateTimeFormatter RENTAL_MONTH = DateTimeFormatter.ofPattern("uuuu-MM");

    private final BillingLedgerService ledger;
    private final CallRepository calls;
    private final AgentRepository agents;
    private final ObjectMapper objectMapper;
    private final ProviderCostReconciliationService reconciliation;

    public CommunicationUsageMeteringService(BillingLedgerService ledger, CallRepository calls,
                                              AgentRepository agents, ObjectMapper objectMapper,
                                              ProviderCostReconciliationService reconciliation) {
        this.ledger = ledger;
        this.calls = calls;
        this.agents = agents;
        this.objectMapper = objectMapper;
        this.reconciliation = reconciliation;
    }

    public void meterCompletedCall(UUID tenantId, UUID callId) {
        try {
            var call = calls.findByIdAndTenantId(callId, tenantId).orElse(null);
            if (call == null || call.getEndedAt() == null || "whatsapp".equals(call.getDirection())) return;
            var seconds = call.getDurationSeconds() == null ? 0 : Math.max(0, call.getDurationSeconds());
            if (seconds == 0) return;
            var desiredMinutes = BigDecimal.valueOf(seconds)
                    .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            var reference = callId.toString();
            var currentMinutes = ledger.quantityTotal(tenantId, "voice_call", reference);
            var adjustment = desiredMinutes.subtract(currentMinutes);
            if (adjustment.signum() == 0) return;
            var metadata = Map.<String, Object>of(
                    "callId", reference,
                    "agentId", call.getAgent().getId().toString(),
                    "direction", call.getDirection(),
                    "outcome", call.getOutcome() == null ? "" : call.getOutcome(),
                    "durationSeconds", seconds,
                    "test", "test".equals(call.getDirection())
            );
            var snapshot = call.getUpdatedAt() == null
                    ? "seconds-" + seconds
                    : Long.toString(call.getUpdatedAt().toInstant().toEpochMilli());
            var idempotencyKey = "voice-call:" + reference + ":snapshot:" + snapshot;
            if (adjustment.signum() > 0) {
                ledger.recordDebit(tenantId, "voice_call", adjustment, "minute", null, null,
                        idempotencyKey, reference, "Completed voice call usage adjustment", metadata);
            } else {
                ledger.recordUnpricedCredit(tenantId, "voice_call", adjustment.abs(), "minute",
                        idempotencyKey, reference, "Authoritative voice call usage correction", metadata);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not meter completed call tenantId={} callId={}", tenantId, callId, exception);
        }
    }

    public void meterOutboundMessage(UUID tenantId, UUID agentId, String channel, String providerMessageId,
                                     String fallbackReference, String messageType) {
        try {
            var normalizedChannel = channel == null ? "" : channel.trim().toLowerCase(java.util.Locale.ROOT);
            if (!("sms".equals(normalizedChannel) || "whatsapp".equals(normalizedChannel))) {
                throw new IllegalArgumentException("Unsupported metered message channel");
            }
            var providerReference = providerMessageId == null ? "" : providerMessageId.trim();
            var reference = providerReference.isBlank() ? required(fallbackReference) : providerReference;
            ledger.recordDebit(
                    tenantId,
                    normalizedChannel + "_message",
                    BigDecimal.ONE,
                    "message",
                    null,
                    null,
                    "outbound-message:" + normalizedChannel + ":" + reference,
                    reference,
                    "Provider-accepted outbound " + normalizedChannel + " message",
                    Map.of(
                            "agentId", agentId.toString(),
                            "channel", normalizedChannel,
                            "messageType", messageType == null || messageType.isBlank() ? "text" : messageType,
                            "providerMessageId", providerReference
                    )
            );
            if ("sms".equals(normalizedChannel) && !providerReference.isBlank()) {
                reconciliation.enqueueTelnyxMessage(tenantId, providerReference);
            } else {
                reconciliation.recordMessageRateCardEstimate(tenantId, normalizedChannel, reference);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not meter outbound message tenantId={} channel={} reference={}",
                    tenantId, channel, fallbackReference, exception);
        }
    }

    @Scheduled(cron = "${sauti.billing.number-rental-accrual-cron:0 17 2 * * *}", zone = "UTC")
    public void accrueMonthlyNumberRentals() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var monthStart = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate()
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        for (var agent : agents.findAllByTwilioPhoneNumberIsNotNull()) {
            try {
                var tenantId = agent.getTenant().getId();
                var phoneNumber = agent.getTwilioPhoneNumber();
                var purchase = ledger.latestPhoneNumberPurchase(tenantId, phoneNumber);
                if (purchase == null || purchase.getCreatedAt() == null
                        || !purchase.getCreatedAt().isBefore(monthStart)) continue;
                var metadata = objectMapper.readTree(purchase.getMetadataJson());
                var monthlyCost = decimal(metadata.path("monthlyCost").asText("0"));
                var currency = purchase.getCurrency() == null ? "USD" : purchase.getCurrency();
                var month = RENTAL_MONTH.format(now);
                ledger.recordDebit(
                        tenantId, "phone_number_rental", BigDecimal.ONE, "number_month",
                        monthlyCost, currency,
                        "phone-number-rental:" + phoneNumber + ":" + month,
                        phoneNumber,
                        "Estimated monthly phone number rental for " + month,
                        Map.of("agentId", agent.getId().toString(), "phoneNumber", phoneNumber, "month", month)
                );
            } catch (Exception exception) {
                LOGGER.warn("Could not accrue monthly number rental agentId={}", agent.getId(), exception);
            }
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Metering reference is required");
        return value.trim();
    }
}
