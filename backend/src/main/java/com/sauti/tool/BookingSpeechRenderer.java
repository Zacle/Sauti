package com.sauti.tool;

import com.sauti.calendar.Booking;
import com.sauti.call.Call;

/** Produces the exact status spoken only after the booking has been persisted. */
final class BookingSpeechRenderer {
    private BookingSpeechRenderer() {
    }

    static String render(Call call, Booking booking, String bookingNumber) {
        var language = SpokenDateTimeFormatter.language(call);
        var timezone = call.getAgent() == null ? "" : call.getAgent().getTimezone();
        var when = SpokenDateTimeFormatter.appointment(booking.getAppointmentAt(), language, timezone);
        var calendarStatus = booking.getCalendarSyncStatus();
        var calendarSynced = "synced".equals(calendarStatus);
        var localOnly = "not_configured".equals(calendarStatus);
        return switch (language) {
            case "fr" -> calendarSynced
                    ? "Votre rendez-vous pour " + booking.getServiceType() + " est confirmé pour le " + when
                        + ". Votre numéro de réservation est " + bookingNumber + "."
                    : localOnly
                        ? "Votre rendez-vous pour " + booking.getServiceType() + " est enregistré pour le " + when
                            + ". Votre numéro de réservation est " + bookingNumber + "."
                        : "Votre rendez-vous pour " + booking.getServiceType() + " est enregistré dans notre système pour le "
                            + when + ", mais la confirmation du calendrier externe est encore en attente. Votre numéro de réservation est "
                            + bookingNumber + ".";
            case "ar" -> calendarSynced
                    ? "تم تأكيد موعد " + booking.getServiceType() + " في " + when
                        + ". رقم الحجز هو " + bookingNumber + "."
                    : localOnly
                        ? "تم حفظ موعد " + booking.getServiceType() + " في " + when
                            + ". رقم الحجز هو " + bookingNumber + "."
                        : "تم حفظ موعد " + booking.getServiceType() + " في نظامنا في " + when
                            + "، لكن تأكيد التقويم الخارجي ما زال قيد الانتظار. رقم الحجز هو " + bookingNumber + ".";
            case "sw" -> calendarSynced
                    ? "Miadi yako ya " + booking.getServiceType() + " imethibitishwa " + when
                        + ". Nambari ya nafasi ni " + bookingNumber + "."
                    : localOnly
                        ? "Miadi yako ya " + booking.getServiceType() + " imehifadhiwa " + when
                            + ". Nambari ya nafasi ni " + bookingNumber + "."
                        : "Miadi yako ya " + booking.getServiceType() + " imehifadhiwa kwenye mfumo wetu " + when
                            + ", lakini uthibitisho wa kalenda ya nje bado unasubiri. Nambari ya nafasi ni " + bookingNumber + ".";
            default -> calendarSynced
                    ? "Your " + booking.getServiceType() + " appointment is booked for " + when
                        + ". Your booking number is " + bookingNumber + "."
                    : localOnly
                        ? "Your " + booking.getServiceType() + " appointment is saved for " + when
                            + ". Your booking number is " + bookingNumber + "."
                        : "Your " + booking.getServiceType() + " appointment is saved in our system for " + when
                            + ", but external calendar confirmation is still pending. Your booking number is "
                            + bookingNumber + ".";
        };
    }
}
