package com.sauti.api;

import com.sauti.admin.AdminDtos.DemoRequestPage;
import com.sauti.admin.AdminDtos.Overview;
import com.sauti.admin.AdminDtos.CustomerDetail;
import com.sauti.admin.AdminDtos.CustomerPage;
import com.sauti.admin.AdminDtos.WorkspaceItem;
import com.sauti.admin.AdminDtos.WorkspacePage;
import com.sauti.admin.AdminDtos.PlatformAnalytics;
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

    @GetMapping("/workspaces")
    WorkspacePage workspaces(@RequestParam(defaultValue = "") String query,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "25") int pageSize) {
        return service.workspaces(query, page, pageSize);
    }

    @GetMapping("/workspaces/{tenantId}")
    WorkspaceItem workspace(@PathVariable UUID tenantId) {
        return service.workspace(tenantId);
    }

    @GetMapping("/customers")
    CustomerPage customers(@RequestParam(defaultValue = "") String query,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "25") int pageSize) {
        return service.customers(query, page, pageSize);
    }

    @GetMapping("/customers/{tenantId}")
    CustomerDetail customer(@PathVariable UUID tenantId, @RequestParam String phone) {
        return service.customer(tenantId, phone);
    }

    @GetMapping("/analytics")
    PlatformAnalytics analytics(@RequestParam(defaultValue = "30") int days) {
        return service.analytics(days);
    }
}
