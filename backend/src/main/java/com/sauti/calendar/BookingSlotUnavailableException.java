package com.sauti.calendar;

/** Raised when a previously available slot becomes occupied before commit. */
public final class BookingSlotUnavailableException extends IllegalArgumentException {
    public BookingSlotUnavailableException() {
        super("The requested appointment time is no longer available");
    }
}
