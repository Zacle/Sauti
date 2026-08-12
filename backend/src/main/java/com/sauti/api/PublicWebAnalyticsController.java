package com.sauti.api;

import com.sauti.webanalytics.PublicWebAnalyticsDtos.TrackEvent;
import com.sauti.webanalytics.PublicWebAnalyticsService;
import com.sauti.shared.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/analytics/events")
public class PublicWebAnalyticsController {
    private final PublicWebAnalyticsService analytics;
    private final ClientAddressResolver clientAddresses;
    public PublicWebAnalyticsController(PublicWebAnalyticsService analytics, ClientAddressResolver clientAddresses) {
        this.analytics = analytics;
        this.clientAddresses = clientAddresses;
    }

    @PostMapping
    ResponseEntity<Void> track(@Valid @RequestBody TrackEvent event, HttpServletRequest request) {
        if ("1".equals(request.getHeader("DNT"))) return ResponseEntity.noContent().build();
        analytics.track(event, clientAddresses.resolve(request), request.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }
}
