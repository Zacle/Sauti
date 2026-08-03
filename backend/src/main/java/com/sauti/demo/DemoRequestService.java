package com.sauti.demo;

import com.sauti.demo.DemoRequestDtos.CreateDemoRequest;
import com.sauti.demo.DemoRequestDtos.DemoRequestResponse;
import com.sauti.shared.RedisRateLimiter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoRequestService {
    private static final Set<String> CHANNELS = Set.of("voice", "browser", "sms", "whatsapp");
    private static final Set<String> VOLUMES = Set.of("under-100", "100-500", "500-2000", "2000-plus", "not-sure");
    private static final DemoRequestResponse RECEIVED = new DemoRequestResponse(
            "received", "Thanks. We will review your needs and contact you about a tailored Sauti demo."
    );
    private final DemoRequestRepository requests;
    private final RedisRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;

    public DemoRequestService(DemoRequestRepository requests, RedisRateLimiter rateLimiter,
                              ApplicationEventPublisher events) {
        this.requests = requests;
        this.rateLimiter = rateLimiter;
        this.events = events;
    }

    @Transactional
    public DemoRequestResponse create(CreateDemoRequest request, String clientAddress) {
        if (request.website() != null && !request.website().isBlank()) return RECEIVED;
        var email = request.email().trim().toLowerCase(Locale.ROOT);
        checkLimit("demo-request:ip", clientAddress, 5, Duration.ofHours(1));
        checkLimit("demo-request:email", email, 3, Duration.ofDays(1));
        if (requests.findFirstByEmailIgnoreCaseAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                email, OffsetDateTime.now().minusHours(24)).isPresent()) return RECEIVED;

        var channels = normalizedChannels(request);
        var volume = request.monthlyCallVolume().trim().toLowerCase(Locale.ROOT);
        if (!VOLUMES.contains(volume)) throw new IllegalArgumentException("Unsupported monthly call volume");
        var saved = requests.save(new DemoRequest(
                request.businessName(), request.contactName(), email, request.countryCode(), request.phone(),
                request.industry(), volume, String.join(",", channels), request.primaryUseCase(), request.notes()
        ));
        events.publishEvent(new DemoRequestReceived(saved));
        return RECEIVED;
    }

    private LinkedHashSet<String> normalizedChannels(CreateDemoRequest request) {
        var normalized = new LinkedHashSet<String>();
        request.channels().forEach(channel -> {
            var value = channel.trim().toLowerCase(Locale.ROOT);
            if (!CHANNELS.contains(value)) throw new IllegalArgumentException("Unsupported demo channel");
            normalized.add(value);
        });
        if (normalized.isEmpty()) throw new IllegalArgumentException("Select at least one demo channel");
        return normalized;
    }

    private void checkLimit(String namespace, String identity, int limit, Duration window) {
        if (!rateLimiter.tryAcquire(namespace, identity == null ? "unknown" : identity, limit, window)) {
            throw new DemoRequestRateLimitExceededException();
        }
    }
}
