package com.sauti.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class WhopEvidenceService {
    private static final String PROVIDER = "whop";
    private final BillingProviderEvidenceRepository evidence;
    private final BillingSubscriptionRepository subscriptions;
    private final BillingAddOnSubscriptionRepository addOns;
    private final BillingPaymentNotificationRepository paymentNotifications;
    private final ObjectMapper objectMapper;

    WhopEvidenceService(BillingProviderEvidenceRepository evidence,
                        BillingSubscriptionRepository subscriptions,
                        BillingAddOnSubscriptionRepository addOns,
                        BillingPaymentNotificationRepository paymentNotifications,
                        ObjectMapper objectMapper) {
        this.evidence = evidence;
        this.subscriptions = subscriptions;
        this.addOns = addOns;
        this.paymentNotifications = paymentNotifications;
        this.objectMapper = objectMapper;
    }

    void record(BillingProviderEvent event, boolean sandbox) throws Exception {
        if (evidence.findBySourceEventId(event.getId()).isPresent()) return;
        var root = objectMapper.readTree(event.getPayloadJson());
        var data = root.path("data");
        var type = recordType(event.getEventName());
        var resourceId = required(data.path("id"), type + " id");
        var paymentId = paymentId(type, data, resourceId);
        var membershipId = membershipId(data);
        var tenantId = resolveTenant(type, membershipId, paymentId);
        var planId = text(data.path("plan").path("id"));
        if (planId == null && data.path("payment").isObject()) {
            planId = text(data.path("payment").path("plan").path("id"));
        }
        var amount = financial(type) ? amount(data, type) : null;
        var currency = financial(type) ? required(data.path("currency"), type + " currency") : null;
        var status = normalizedStatus(event.getEventName(), data);
        var occurredAt = timestamp(root.path("timestamp"));
        if (occurredAt == null) occurredAt = firstTimestamp(data.path("updated_at"), data.path("created_at"),
                data.path("paid_at"));
        evidence.save(new BillingProviderEvidence(event.getId(), tenantId, PROVIDER, type,
                event.getEventName(), resourceId, paymentId, membershipId, planId, status,
                amount, currency, sandbox, occurredAt));
    }

    private UUID resolveTenant(String type, String membershipId, String paymentId) {
        if (membershipId != null) {
            var base = subscriptions.findByProviderAndProviderSubscriptionId(PROVIDER, membershipId).orElse(null);
            if (base != null) return base.getTenantId();
            var addOn = addOns.findByProviderAndProviderSubscriptionId(PROVIDER, membershipId).orElse(null);
            if (addOn != null) return addOn.getTenantId();
        }
        if (!"payment".equals(type) && paymentId != null) {
            return evidence.findFirstByProviderAndRecordTypeAndProviderResourceIdOrderByOccurredAtDesc(
                            PROVIDER, "payment", paymentId)
                    .map(BillingProviderEvidence::getTenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Whop financial event payment ownership has not been normalized yet"));
        }
        if ("payment".equals(type) && paymentId != null) {
            return paymentNotifications.findByProviderAndProviderPaymentId(PROVIDER, paymentId)
                    .map(BillingPaymentNotification::getTenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Whop evidence membership has not been synchronized yet"));
        }
        throw new IllegalStateException("Whop evidence membership has not been synchronized yet");
    }

    private static String recordType(String eventName) {
        if (eventName.startsWith("membership.")) return "membership";
        if (eventName.startsWith("payment.")) return "payment";
        if (eventName.startsWith("refund.")) return "refund";
        if (eventName.startsWith("dispute.")) return "dispute";
        throw new IllegalArgumentException("Unsupported Whop evidence event");
    }

    private static String paymentId(String type, JsonNode data, String resourceId) {
        if ("payment".equals(type)) return resourceId;
        var id = text(data.path("payment").path("id"));
        if (id == null) id = text(data.path("payment_id"));
        return id;
    }

    private static String membershipId(JsonNode data) {
        var id = text(data.path("membership").path("id"));
        if (id == null) id = text(data.path("membership_id"));
        if (id == null && data.path("payment").isObject()) {
            id = text(data.path("payment").path("membership").path("id"));
        }
        return id;
    }

    private static String normalizedStatus(String eventName, JsonNode data) {
        var status = text(data.path("substatus"));
        if (status == null) status = text(data.path("status"));
        if (status != null) return status.toLowerCase();
        return eventName.substring(eventName.indexOf('.') + 1).toLowerCase();
    }

    private static BigDecimal amount(JsonNode data, String type) {
        var node = "payment".equals(type) ? data.path("total") : data.path("amount");
        if (!node.isNumber()) throw new IllegalArgumentException("Whop " + type + " amount is required");
        var value = node.decimalValue();
        if (value.signum() < 0) throw new IllegalArgumentException("Whop " + type + " amount is invalid");
        return value;
    }

    private static boolean financial(String type) {
        return !"membership".equals(type);
    }

    private static OffsetDateTime firstTimestamp(JsonNode... nodes) {
        for (var node : nodes) {
            var value = timestamp(node);
            if (value != null) return value;
        }
        return OffsetDateTime.now();
    }

    private static OffsetDateTime timestamp(JsonNode node) {
        var value = text(node);
        return value == null ? null : OffsetDateTime.parse(value);
    }

    private static String required(JsonNode node, String label) {
        var value = text(node);
        if (value == null) throw new IllegalArgumentException("Whop " + label + " is required");
        return value;
    }

    private static String text(JsonNode node) {
        var value = node.asText("").trim();
        return value.isBlank() ? null : value;
    }
}
