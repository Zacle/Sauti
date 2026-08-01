package com.sauti.integration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.calendar.Booking;
import com.sauti.calendar.BookingRepository;
import com.sauti.call.Call;
import com.sauti.tenant.Tenant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoogleSheetsCustomerSyncServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final GoogleSheetsApiClient googleSheets = mock(GoogleSheetsApiClient.class);
    private final GoogleSheetsCustomerSyncService service =
            new GoogleSheetsCustomerSyncService(bookings, googleSheets);

    @Test
    void appendsANewConfirmedBookingCustomer() throws Exception {
        var fixture = fixture("confirmed", "+20 11 5752 441", "Zachary", "zachary@example.test");
        when(googleSheets.values(fixture.tenantId, fixture.agentId, "sheet-1", "Customers!A:C"))
                .thenReturn(objectMapper.readTree("{\"values\":[[\"Phone\",\"Name\",\"Email\"]]}"));

        service.syncConfirmedBookingCustomer(fixture.call, configuration());

        verify(googleSheets).appendValues(
                fixture.tenantId,
                fixture.agentId,
                "sheet-1",
                "Customers!A:C",
                List.of("+20 11 5752 441", "Zachary", "zachary@example.test")
        );
        verify(googleSheets, never()).updateValues(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void enrichesMissingFieldsWithoutReplacingExistingCustomerData() throws Exception {
        var fixture = fixture("confirmed", "001155752441", "New name", "new@example.test");
        when(googleSheets.values(fixture.tenantId, fixture.agentId, "sheet-1", "Customers!A:C"))
                .thenReturn(objectMapper.readTree("""
                        {"values":[
                          ["Phone","Name","Email"],
                          ["+11 5575 2441","Existing name",""]
                        ]}
                        """));

        service.syncConfirmedBookingCustomer(fixture.call, configuration());

        verify(googleSheets).updateValues(
                fixture.tenantId,
                fixture.agentId,
                "sheet-1",
                "Customers!C2",
                List.of("new@example.test")
        );
        verify(googleSheets, never()).appendValues(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void ignoresCancelledBookings() {
        var fixture = fixture("cancelled", "0115752441", "Zachary", null);

        service.syncConfirmedBookingCustomer(fixture.call, configuration());

        verify(googleSheets, never()).values(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void makesNoWriteWhenTheCustomerAlreadyHasAllKnownDetails() throws Exception {
        var fixture = fixture("confirmed", "001155752441", "Zachary", "zachary@example.test");
        when(googleSheets.values(fixture.tenantId, fixture.agentId, "sheet-1", "Customers!A:C"))
                .thenReturn(objectMapper.readTree("""
                        {"values":[
                          ["Phone","Name","Email"],
                          ["+11 5575 2441","Zachary","zachary@example.test"]
                        ]}
                        """));

        service.syncConfirmedBookingCustomer(fixture.call, configuration());

        verify(googleSheets, never()).appendValues(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()
        );
        verify(googleSheets, never()).updateValues(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    private Fixture fixture(String status, String phone, String name, String email) {
        var call = mock(Call.class);
        var tenant = mock(Tenant.class);
        var agent = mock(Agent.class);
        var booking = mock(Booking.class);
        var callId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        when(call.getId()).thenReturn(callId);
        when(call.getTenant()).thenReturn(tenant);
        when(call.getAgent()).thenReturn(agent);
        when(tenant.getId()).thenReturn(tenantId);
        when(agent.getId()).thenReturn(agentId);
        when(bookings.findFirstByTenantIdAndCall_IdAndAgent_Id(tenantId, callId, agentId))
                .thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(status);
        when(booking.getCallerPhone()).thenReturn(phone);
        when(booking.getCallerName()).thenReturn(name);
        when(booking.getCallerEmail()).thenReturn(email);
        return new Fixture(call, tenantId, agentId);
    }

    private Map<String, Object> configuration() {
        return Map.of(
                "spreadsheetId", "sheet-1",
                "range", "Customers!A:C",
                "lookupColumn", "0",
                "customerNameColumn", "1",
                "customerEmailColumn", "2"
        );
    }

    private record Fixture(Call call, UUID tenantId, UUID agentId) {}
}
