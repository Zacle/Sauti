package com.sauti.session;

public record BookingDraft(
        String callerName,
        String serviceType,
        String preferredDate,
        String confirmedSlot,
        String callerPhone,
        boolean identityReadbackRequested,
        String reviewToken
) {
    public BookingDraft(String callerName, String serviceType, String preferredDate, String confirmedSlot, String callerPhone) {
        this(callerName, serviceType, preferredDate, confirmedSlot, callerPhone, true, "");
    }

    public BookingDraft(String callerName, String serviceType, String preferredDate, String confirmedSlot, String callerPhone, boolean identityReadbackRequested) {
        this(callerName, serviceType, preferredDate, confirmedSlot, callerPhone, identityReadbackRequested, "");
    }
}
