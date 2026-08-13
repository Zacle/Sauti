package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationDisconnectionServiceTest {
    @Test
    void revokesGoogleGrantBeforeDeletingTheLocalConnection() {
        var integrations = mock(IntegrationService.class);
        var revoker = mock(GoogleOAuthGrantRevoker.class);
        var service = new IntegrationDisconnectionService(integrations, revoker);
        var tenantId = UUID.randomUUID();
        var connectionId = UUID.randomUUID();
        when(integrations.googleGrantForDisconnect(tenantId, connectionId))
                .thenReturn(new IntegrationService.GoogleGrant("google_sheets", "refresh-token"));
        when(revoker.isEnabled()).thenReturn(true);
        when(revoker.revoke("refresh-token")).thenReturn(true);

        var result = service.disconnect(tenantId, connectionId);

        assertThat(result.providerRevocationAttempted()).isTrue();
        assertThat(result.providerRevocationConfirmed()).isTrue();
        verify(revoker).revoke("refresh-token");
        verify(integrations).disconnect(tenantId, connectionId);
    }

    @Test
    void deletesLocalConnectionEvenWhenGoogleCannotConfirmRevocation() {
        var integrations = mock(IntegrationService.class);
        var revoker = mock(GoogleOAuthGrantRevoker.class);
        var service = new IntegrationDisconnectionService(integrations, revoker);
        var tenantId = UUID.randomUUID();
        var connectionId = UUID.randomUUID();
        when(integrations.googleGrantForDisconnect(tenantId, connectionId))
                .thenReturn(new IntegrationService.GoogleGrant("google_calendar", "refresh-token"));
        when(revoker.isEnabled()).thenReturn(true);
        when(revoker.revoke("refresh-token")).thenReturn(false);

        var result = service.disconnect(tenantId, connectionId);

        assertThat(result.providerRevocationAttempted()).isTrue();
        assertThat(result.providerRevocationConfirmed()).isFalse();
        verify(integrations).disconnect(tenantId, connectionId);
    }

    @Test
    void disconnectsNonGoogleProviderWithoutARevocationAttempt() {
        var integrations = mock(IntegrationService.class);
        var revoker = mock(GoogleOAuthGrantRevoker.class);
        var service = new IntegrationDisconnectionService(integrations, revoker);
        var tenantId = UUID.randomUUID();
        var connectionId = UUID.randomUUID();

        var result = service.disconnect(tenantId, connectionId);

        assertThat(result.providerRevocationAttempted()).isFalse();
        assertThat(result.providerRevocationConfirmed()).isFalse();
        verify(integrations).disconnect(tenantId, connectionId);
    }

    @Test
    void doesNotRevokeAcrossASharedGoogleCloudProject() {
        var integrations = mock(IntegrationService.class);
        var revoker = mock(GoogleOAuthGrantRevoker.class);
        var service = new IntegrationDisconnectionService(integrations, revoker);
        var tenantId = UUID.randomUUID();
        var connectionId = UUID.randomUUID();
        when(integrations.googleGrantForDisconnect(tenantId, connectionId))
                .thenReturn(new IntegrationService.GoogleGrant("google_calendar", "refresh-token"));
        when(revoker.isEnabled()).thenReturn(false);

        var result = service.disconnect(tenantId, connectionId);

        assertThat(result.googleGrantRemovedLocally()).isTrue();
        assertThat(result.providerRevocationAttempted()).isFalse();
        verify(integrations).disconnect(tenantId, connectionId);
    }
}
