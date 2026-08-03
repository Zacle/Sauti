package com.sauti.api;

import com.sauti.demo.DemoRequestDtos.CreateDemoRequest;
import com.sauti.demo.DemoRequestDtos.DemoRequestResponse;
import com.sauti.demo.DemoRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/demo-requests")
public class DemoRequestController {
    private final DemoRequestService service;

    public DemoRequestController(DemoRequestService service) {
        this.service = service;
    }

    @PostMapping
    DemoRequestResponse create(@Valid @RequestBody CreateDemoRequest request, HttpServletRequest servletRequest) {
        return service.create(request, clientAddress(servletRequest));
    }

    private String clientAddress(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }
}
