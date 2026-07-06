package com.sauti.session;

public record BookingDraft(
        String callerName,
        String serviceType,
        String preferredDate,
        String confirmedSlot,
        String callerPhone
) {
}
