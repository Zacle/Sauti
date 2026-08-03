package com.sauti.api;

import com.sauti.admin.AdminDtos.DemoRequestPage;
import com.sauti.admin.AdminDtos.Overview;
import com.sauti.admin.AdminService;
import com.sauti.demo.PilotInvitationDtos.InvitationIssued;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    Overview overview() {
        return service.overview();
    }

    @GetMapping("/demo-requests")
    DemoRequestPage demoRequests(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "25") int pageSize) {
        return service.demoRequests(page, pageSize);
    }

    @PostMapping("/demo-requests/{requestId}/invitation")
    InvitationIssued invite(@PathVariable UUID requestId) {
        return service.invite(requestId);
    }
}
