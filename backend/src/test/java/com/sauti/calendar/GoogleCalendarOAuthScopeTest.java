package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GoogleCalendarOAuthScopeTest {
    private static final String EVENTS = "https://www.googleapis.com/auth/calendar.events";
    private static final String FREE_BUSY = "https://www.googleapis.com/auth/calendar.freebusy";

    @Test
    void acceptsBothRequiredCalendarScopesInAnyOrder() {
        assertThatCode(() -> GoogleCalendarIntegrationService.requireGrantedScopes(
                FREE_BUSY + " " + EVENTS
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsAPartialCalendarGrant() {
        assertThatThrownBy(() -> GoogleCalendarIntegrationService.requireGrantedScopes(EVENTS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both Calendar permissions");
    }
}
