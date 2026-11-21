package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sauti.agent.Agent;
import com.sauti.calendar.BookingIdentityService;
import com.sauti.calendar.BookingService;
import com.sauti.call.Call;
import com.sauti.call.CallIntakeNoteService;
import com.sauti.llm.LlmToolCall;
import com.sauti.session.CallSessionStore;
import com.sauti.tenant.Tenant;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Bookings take the length of the service being booked, unless the caller asked for another length. */
class ServiceDurationBookingTest {
    private final Tenant tenant = tenant();
    private final Agent agent = new Agent(tenant, "Amina", "Hello", "Prompt");
    private final Call call = new Call(tenant, agent, "sid-duration", "+254700000001", "inbound");
    private final SautiCalendarFulfillment calendar = new SautiCalendarFulfillment(
            mock(CalendarProviderFactory.class), mock(BookingService.class), mock(CallSessionStore.class),
            mock(CallIntakeNoteService.class), mock(BookingIdentityService.class),
            (workspaceId, service) -> service.equalsIgnoreCase("whitening") ? OptionalInt.of(90) : OptionalInt.empty());

    @Test
    void aKnownServiceUsesItsOwnLength() {
        var adjusted = calendar.withServiceDuration(call, booking(Map.of("service_type", "Whitening")));
        assertThat(adjusted.arguments()).containsEntry("duration_minutes", 90);

        var withDefault = calendar.withServiceDuration(call, booking(Map.of(
                "service_type", "whitening", "duration_minutes", agent.getDefaultBookingDurationMinutes())));
        assertThat(withDefault.arguments()).containsEntry("duration_minutes", 90);
    }

    @Test
    void aLengthTheCallerAskedForAndUnknownServicesAreLeftAlone() {
        var requested = booking(Map.of("service_type", "Whitening", "duration_minutes", 30));
        assertThat(calendar.withServiceDuration(call, requested)).isSameAs(requested);

        var unknown = booking(Map.of("service_type", "Root canal"));
        assertThat(calendar.withServiceDuration(call, unknown)).isSameAs(unknown);

        var lookup = new LlmToolCall("2", "lookup_booking", Map.of("service_type", "Whitening"));
        assertThat(calendar.withServiceDuration(call, lookup)).isSameAs(lookup);
    }

    private static LlmToolCall booking(Map<String, Object> arguments) {
        return new LlmToolCall("1", "book_slot", arguments);
    }

    private static Tenant tenant() {
        var tenant = new Tenant("Clinic", "owner@example.com", "KE");
        try {
            var id = Tenant.class.getDeclaredField("id");
            id.setAccessible(true);
            if (id.get(tenant) == null) id.set(tenant, java.util.UUID.randomUUID());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return tenant;
    }
}
