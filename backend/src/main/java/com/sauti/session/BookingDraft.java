package com.sauti.session;

public record BookingDraft(
        String callerName,
        String serviceType,
        String preferredDate,
        String confirmedSlot,
        String callerPhone,
        boolean identityReadbackRequested,
        String reviewToken,
        int durationMinutes
) {
    public BookingDraft(String callerName, String serviceType, String preferredDate, String confirmedSlot, String callerPhone) {
        this(callerName, serviceType, preferredDate, confirmedSlot, callerPhone, true, "", 60);
    }

    public BookingDraft(String callerName, String serviceType, String preferredDate, String confirmedSlot, String callerPhone, boolean identityReadbackRequested) {
        this(callerName, serviceType, preferredDate, confirmedSlot, callerPhone, identityReadbackRequested, "", 60);
    }

    public BookingDraft(
            String callerName,
            String serviceType,
            String preferredDate,
            String confirmedSlot,
            String callerPhone,
            boolean identityReadbackRequested,
            String reviewToken
    ) {
        this(callerName, serviceType, preferredDate, confirmedSlot, callerPhone,
                identityReadbackRequested, reviewToken, 60);
    }
}
