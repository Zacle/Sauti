package com.sauti.session;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Server-owned booking identity. This is persisted with the call session but is
 * never placed in model-authored conversation state.
 */
public record VerifiedBookingIdentity(
        UUID tenantId,
        UUID bookingId,
        String bookingReference,
        String normalizedCallerPhone,
        OffsetDateTime verifiedAt
) {
}
