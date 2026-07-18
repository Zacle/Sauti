package com.sauti.session;

public record BookingDraft(
        String callerName,
        String serviceType,
        String preferredDate,
        String confirmedSlot,
        String callerPhone,
        boolean identityReadbackRequested
) {
    public BookingDraft(String callerName, String serviceType, String preferredDate, String confirmedSlot, String callerPhone) {
        this(callerName, serviceType, preferredDate, confirmedSlot, callerPhone, false);
    }
}
