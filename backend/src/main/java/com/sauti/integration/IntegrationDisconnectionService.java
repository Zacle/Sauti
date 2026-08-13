package com.sauti.integration;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IntegrationDisconnectionService {
    private final IntegrationService integrations;
    private final GoogleOAuthGrantRevoker googleRevoker;

    public IntegrationDisconnectionService(IntegrationService integrations,
                                           GoogleOAuthGrantRevoker googleRevoker) {
        this.integrations = integrations;
        this.googleRevoker = googleRevoker;
    }

    public DisconnectResult disconnect(UUID tenantId, UUID connectionId) {
        var grant = integrations.googleGrantForDisconnect(tenantId, connectionId);
        var revocationAttempted = grant != null && googleRevoker.isEnabled();
        var revocationConfirmed = revocationAttempted && googleRevoker.revoke(grant.token());
        integrations.disconnect(tenantId, connectionId);
        return new DisconnectResult(grant != null, revocationAttempted, revocationConfirmed);
    }

    public record DisconnectResult(boolean googleGrantRemovedLocally,
                                   boolean providerRevocationAttempted,
                                   boolean providerRevocationConfirmed) {
    }
}
