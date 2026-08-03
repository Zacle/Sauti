package com.sauti.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.demo.DemoRequestDtos.CreateDemoRequest;
import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class DemoRequestServiceTest {
    private final DemoRequestRepository requests = mock(DemoRequestRepository.class);
    private final RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DemoRequestService service = new DemoRequestService(requests, rateLimiter, events);

    @Test
    void storesAQualifiedRequestAndPublishesTheNotificationEvent() {
        when(rateLimiter.tryAcquire(anyString(), anyString(), any(Integer.class), any(Duration.class)))
                .thenReturn(true);
        when(requests.findFirstByEmailIgnoreCaseAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                eq("owner@example.com"), any())).thenReturn(Optional.empty());
        when(requests.save(any(DemoRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(validRequest(null), "203.0.113.10");

        assertThat(response.status()).isEqualTo("received");
        var saved = ArgumentCaptor.forClass(DemoRequest.class);
        verify(requests).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("owner@example.com");
        assertThat(saved.getValue().getChannels()).isEqualTo("voice,whatsapp");
        assertThat(saved.getValue().getStatus()).isEqualTo("new");
        verify(events).publishEvent(any(DemoRequestReceived.class));
    }

    @Test
    void quietlyAcceptsTheHoneypotWithoutUsingResources() {
        var response = service.create(validRequest("https://spam.example"), "203.0.113.10");

        assertThat(response.status()).isEqualTo("received");
        verify(rateLimiter, never()).tryAcquire(anyString(), anyString(), any(Integer.class), any(Duration.class));
        verify(requests, never()).save(any());
    }

    @Test
    void doesNotCreateRepeatedLeadsWithinTwentyFourHours() {
        when(rateLimiter.tryAcquire(anyString(), anyString(), any(Integer.class), any(Duration.class)))
                .thenReturn(true);
        when(requests.findFirstByEmailIgnoreCaseAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                eq("owner@example.com"), any())).thenReturn(Optional.of(mock(DemoRequest.class)));

        var response = service.create(validRequest(null), "203.0.113.10");

        assertThat(response.status()).isEqualTo("received");
        verify(requests, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    private CreateDemoRequest validRequest(String website) {
        return new CreateDemoRequest(
                "Acme Clinic", "Amina", " Owner@Example.com ", "KE", "+254700000000",
                "Healthcare", "100-500", List.of("Voice", "WhatsApp"),
                "Answer calls and book appointments", "We use Google Calendar", website
        );
    }
}
