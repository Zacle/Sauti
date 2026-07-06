package com.sauti.agent;

import java.util.List;
import java.util.Map;

public interface TelephonyProvider {
    String provisionNumber(String tenantCountryCode);

    default List<AvailablePhoneNumber> searchAvailableNumbers(String tenantCountryCode, int limit) {
        return List.of();
    }

    default String provisionNumber(String tenantCountryCode, String requestedPhoneNumber) {
        if (requestedPhoneNumber != null && !requestedPhoneNumber.isBlank()) {
            throw new UnsupportedOperationException("The configured telephony provider does not support number selection");
        }
        return provisionNumber(tenantCountryCode);
    }

    default PhoneNumberProvisioning provisionPhoneNumber(String tenantCountryCode, String requestedPhoneNumber) {
        return new PhoneNumberProvisioning(
                provisionNumber(tenantCountryCode, requestedPhoneNumber),
                "legacy",
                null,
                "active",
                true
        );
    }

    default PhoneNumberProvisioning refreshPhoneNumber(String providerReference) {
        return null;
    }

    default String buildMediaStreamTwiMl(
            String websocketUrl,
            String callSid,
            String tenantId,
            String agentId,
            boolean recordCall
    ) {
        return buildMediaStreamTwiMl(websocketUrl, callSid, tenantId, agentId, recordCall, Map.of());
    }

    String buildMediaStreamTwiMl(
            String websocketUrl,
            String callSid,
            String tenantId,
            String agentId,
            boolean recordCall,
            Map<String, String> extraParameters
    );

    record AvailablePhoneNumber(
            String phoneNumber,
            String type,
            String locality,
            String region,
            String upfrontCost,
            String monthlyCost,
            String currency
    ) {
    }

    record PhoneNumberProvisioning(
            String phoneNumber,
            String provider,
            String providerReference,
            String status,
            boolean requirementsMet
    ) {
    }
}
