package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookingConversationPolicyTest {
    @Test
    void recognizesTheReportedBookingWithdrawalWithoutConfusingACorrection() {
        assertThat(BookingConversationPolicy.pausesBooking(
                "Everything is wrong, but don't book yet. I will call you back later."
        )).isTrue();
        assertThat(BookingConversationPolicy.pausesBooking(
                "Don't book Friday; book Saturday instead."
        )).isFalse();
    }

    @Test
    void givesAnExplicitProfessionalAssuranceBeforeClosing() {
        assertThat(BookingConversationPolicy.pausedResponse("en"))
                .contains("I won’t book anything")
                .contains("Thank you for calling")
                .endsWith("Goodbye.");
    }
}
