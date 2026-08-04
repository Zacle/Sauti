package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformIntegrationHealthServiceTest {
    @Test
    void reportsRecordedFailuresWithoutMakingProviderRequests() {
        var connections = mock(IntegrationConnectionRepository.class);
        var deliveries = mock(IntegrationDeliveryRepository.class);
        var from = OffsetDateTime.now().minusDays(7);
        var connection = mock(IntegrationConnection.class);
        when(connection.getProvider()).thenReturn("google_calendar");
        when(connection.getStatus()).thenReturn("connected");
        var delivered = delivery("google_calendar", "delivered");
        var failed = delivery("google_calendar", "failed");
        when(connections.findAll()).thenReturn(List.of(connection));
        when(deliveries.findAllByCreatedAtGreaterThanEqual(from)).thenReturn(List.of(delivered, failed));

        var result = new PlatformIntegrationHealthService(connections, deliveries).snapshot(from);

        assertThat(result).singleElement().satisfies(health -> {
            assertThat(health.status()).isEqualTo("attention");
            assertThat(health.configuredConnections()).isEqualTo(1);
            assertThat(health.delivered()).isEqualTo(1);
            assertThat(health.failed()).isEqualTo(1);
        });
    }

    private IntegrationDelivery delivery(String provider, String status) {
        var delivery = mock(IntegrationDelivery.class);
        when(delivery.getProvider()).thenReturn(provider);
        when(delivery.getStatus()).thenReturn(status);
        when(delivery.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-08-03T12:00:00Z"));
        return delivery;
    }
}
