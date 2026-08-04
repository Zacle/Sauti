package com.sauti.integration;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformIntegrationHealthService {
    private final IntegrationConnectionRepository connections;
    private final IntegrationDeliveryRepository deliveries;

    public PlatformIntegrationHealthService(IntegrationConnectionRepository connections,
                                            IntegrationDeliveryRepository deliveries) {
        this.connections = connections;
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public List<ProviderHealth> snapshot(OffsetDateTime from) {
        var allConnections = connections.findAll();
        var recentDeliveries = deliveries.findAllByCreatedAtGreaterThanEqual(from);
        var providers = new HashSet<String>();
        allConnections.forEach(connection -> providers.add(connection.getProvider()));
        recentDeliveries.forEach(delivery -> providers.add(delivery.getProvider()));
        return providers.stream().map(provider -> {
            var providerConnections = allConnections.stream()
                    .filter(connection -> provider.equals(connection.getProvider())).toList();
            var providerDeliveries = recentDeliveries.stream()
                    .filter(delivery -> provider.equals(delivery.getProvider())).toList();
            long connected = providerConnections.stream().filter(connection -> "connected".equals(connection.getStatus())).count();
            long connectionErrors = providerConnections.stream().filter(connection -> "error".equals(connection.getStatus())).count();
            long delivered = providerDeliveries.stream().filter(delivery -> "delivered".equals(delivery.getStatus())).count();
            long retrying = providerDeliveries.stream().filter(delivery -> "retrying".equals(delivery.getStatus()) || "pending".equals(delivery.getStatus())).count();
            long failed = providerDeliveries.stream().filter(delivery -> "failed".equals(delivery.getStatus())).count();
            var lastActivity = providerDeliveries.stream().map(IntegrationDelivery::getCreatedAt)
                    .max(Comparator.naturalOrder()).orElse(null);
            return new ProviderHealth(provider, status(connected, connectionErrors, delivered, retrying, failed),
                    providerConnections.size(), connected, connectionErrors, providerDeliveries.size(),
                    delivered, retrying, failed, lastActivity);
        }).sorted(Comparator.comparing(ProviderHealth::provider)).toList();
    }

    private String status(long connected, long connectionErrors, long delivered, long retrying, long failed) {
        if (connectionErrors > 0 || failed > 0) return "attention";
        if (retrying > 0) return "degraded";
        if (connected > 0 || delivered > 0) return "healthy";
        return "unknown";
    }

    public record ProviderHealth(String provider, String status, long configuredConnections,
                                 long connectedConnections, long connectionErrors, long deliveryAttempts,
                                 long delivered, long retrying, long failed, OffsetDateTime lastActivityAt) { }
}
