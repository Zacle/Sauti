package com.sauti.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AvailabilityIntentDetectorTest {
    @Test
    void detectsEnglishFrenchAndArabicDatesAndTimes() {
        assertThat(AvailabilityIntentDetector.requiresAvailabilityCheck("Wednesday at 3 P.M.")).isTrue();
        assertThat(AvailabilityIntentDetector.requiresAvailabilityCheck("mercredi à 15:00")).isTrue();
        assertThat(AvailabilityIntentDetector.requiresAvailabilityCheck("موعد الخميس")).isTrue();
    }

    @Test
    void doesNotTreatAnUnscheduledBookingRequestAsAnAvailabilityLookup() {
        assertThat(AvailabilityIntentDetector.requiresAvailabilityCheck("I want to book a general fitness class")).isFalse();
    }
}
