package com.sauti.tool;

import com.sauti.calendar.Booking;
import com.sauti.call.Call;

/** Produces the exact confirmation spoken only after calendar insertion succeeds. */
final class BookingSpeechRenderer {
    private BookingSpeechRenderer() {
    }

    static String render(Call call, Booking booking, String confirmationCode) {
        var language = SpokenDateTimeFormatter.language(call);
        var when = SpokenDateTimeFormatter.appointment(booking.getAppointmentAt(), language);
        return switch (language) {
            case "fr" -> "Votre rendez-vous pour " + booking.getServiceType() + " est confirmé pour le "
                    + when + ". Votre code de confirmation est " + confirmationCode + ".";
            case "ar" -> "تم تأكيد موعد " + booking.getServiceType() + " يوم " + when
                    + ". رمز التأكيد هو " + confirmationCode + ".";
            case "sw" -> "Miadi yako ya " + booking.getServiceType() + " imethibitishwa " + when
                    + ". Nambari ya uthibitisho ni " + confirmationCode + ".";
            default -> "Your " + booking.getServiceType() + " appointment is booked for " + when
                    + ". Your confirmation code is " + confirmationCode + ".";
        };
    }
}
