package com.sauti.api;

import com.sauti.demo.PublicDemoVoiceDtos.PublicDemoVoiceConfiguration;
import com.sauti.demo.PublicDemoVoiceDtos.StartPublicDemoVoiceRequest;
import com.sauti.demo.PublicDemoVoiceDtos.StartPublicDemoVoiceResponse;
import com.sauti.demo.PublicDemoVoiceService;
import com.sauti.call.CallDtos.StartupLatencyRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public/demo-voice")
public class PublicDemoVoiceController {
    private final PublicDemoVoiceService service;

    public PublicDemoVoiceController(PublicDemoVoiceService service) {
        this.service = service;
    }

    @GetMapping("/configuration")
    PublicDemoVoiceConfiguration configuration(@RequestParam String origin) {
        return service.configuration(origin);
    }

    @PostMapping("/sessions")
    StartPublicDemoVoiceResponse start(
            @RequestBody StartPublicDemoVoiceRequest request,
            HttpServletRequest servletRequest
    ) {
        var headerOrigin = servletRequest.getHeader("Origin");
        if (headerOrigin != null && !headerOrigin.isBlank()
                && request.origin() != null
                && !request.origin().isBlank()
                && !headerOrigin.equalsIgnoreCase(request.origin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Public demo origin does not match");
        }
        var verifiedOrigin = headerOrigin == null || headerOrigin.isBlank() ? request.origin() : headerOrigin;
        return service.start(
                verifiedOrigin,
                clientAddress(servletRequest),
                request.deviceId(),
                request.consentAccepted()
        );
    }

    @PostMapping("/sessions/{sessionId}/complete")
    void complete(
            @org.springframework.web.bind.annotation.PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Public demo session token is required");
        }
        try {
            service.complete(sessionId, authorization.substring("Bearer ".length()).trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid public demo session token");
        }
    }

    @PostMapping("/sessions/{sessionId}/startup-latency")
    void recordStartupLatency(
            @org.springframework.web.bind.annotation.PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody StartupLatencyRequest measurement
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Public demo session token is required");
        }
        try {
            service.recordStartupLatency(
                    sessionId,
                    authorization.substring("Bearer ".length()).trim(),
                    measurement.latencyMs()
            );
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid public demo session token");
        }
    }

    private String clientAddress(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }
}
