package com.sauti.api;

import com.sauti.demo.DemoRequestDtos.CreateDemoRequest;
import com.sauti.demo.DemoRequestDtos.DemoRequestResponse;
import com.sauti.demo.DemoRequestService;
import com.sauti.shared.ClientAddressResolver;
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
    private final ClientAddressResolver clientAddresses;

    public DemoRequestController(DemoRequestService service, ClientAddressResolver clientAddresses) {
        this.service = service;
        this.clientAddresses = clientAddresses;
    }

    @PostMapping
    DemoRequestResponse create(@Valid @RequestBody CreateDemoRequest request, HttpServletRequest servletRequest) {
        return service.create(request, clientAddresses.resolve(servletRequest));
    }
}
