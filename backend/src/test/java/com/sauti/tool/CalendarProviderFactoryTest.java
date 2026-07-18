package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.calendar.CalendarProvider;
import com.sauti.calendar.GoogleCalendarApiClient;
import com.sauti.calendar.GoogleCalendarProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalendarProviderFactoryTest {
    @Test
    void resolvesRealtimeGoogleProviderWithoutDereferencingDetachedAgent() {
        var fallback = mock(CalendarProvider.class);
        var credentials = mock(CalendarCredentialRepository.class);
        var client = mock(GoogleCalendarApiClient.class);
        var tools = mock(AgentToolRepository.class);
        var tool = mock(AgentTool.class);
        var credential = mock(CalendarCredential.class);
        var credentialId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        when(tool.getCalendarType()).thenReturn("google");
        when(tool.getCalendarCredentialId()).thenReturn(credentialId);
        when(credentials.findByIdAndTenant_Id(credentialId, tenantId)).thenReturn(Optional.of(credential));

        var provider = new CalendarProviderFactory(fallback, credentials, client, tools)
                .forTool(tool, tenantId);

        assertThat(provider).isInstanceOf(GoogleCalendarProvider.class);
        verify(tool, never()).getAgent();
    }
}
