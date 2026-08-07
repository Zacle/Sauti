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
import com.sauti.auth.AuthenticatedUser;
import com.sauti.admin.AdminDtos.UpdateDemoRequest;
import com.sauti.admin.AdminDtos.RejectDemoRequest;
import com.sauti.admin.AdminDtos.DemoRequestItem;
import com.sauti.admin.AdminDtos.AuditPage;
import com.sauti.admin.AdminDtos.ConfigurePilotPolicy;
import com.sauti.admin.AdminDtos.PilotReadinessItem;
import com.sauti.admin.AdminDtos.UpdatePilotReadiness;
import com.sauti.admin.PlatformAdminAuditService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService service;
    private final PlatformAdminAuditService audit;

    public AdminController(AdminService service, PlatformAdminAuditService audit) {
        this.service = service;
        this.audit = audit;
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
    InvitationIssued invite(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID requestId) {
        return service.invite(requestId, user.email());
    }

    @PostMapping("/demo-requests/{requestId}/invitation/resend")
    InvitationIssued resend(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID requestId) {
        return service.resendInvitation(requestId, user.email());
    }

    @PostMapping("/demo-requests/{requestId}/invitation/revoke")
    void revoke(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID requestId) {
        service.revokeInvitation(requestId, user.email());
    }

    @PostMapping("/demo-requests/{requestId}/reject")
    DemoRequestItem reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID requestId,
                           @RequestBody RejectDemoRequest request) {
        return service.reject(requestId, request.reason(), user.email());
    }

    @PatchMapping("/demo-requests/{requestId}")
    DemoRequestItem update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID requestId,
                           @RequestBody UpdateDemoRequest request) {
        return service.updateDemoOperations(requestId, request.assignedTo(), request.internalNotes(), user.email());
    }

    @GetMapping("/audit")
    AuditPage audit(@RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "50") int pageSize) {
        return audit.list(page, pageSize);
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

    @PatchMapping("/workspaces/{tenantId}/pilot-policy")
    WorkspaceItem configurePilotPolicy(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID tenantId,
                                       @RequestBody ConfigurePilotPolicy request) {
        return service.configurePilotPolicy(tenantId, request, user.email());
    }

    @GetMapping("/workspaces/{tenantId}/readiness")
    PilotReadinessItem pilotReadiness(@PathVariable UUID tenantId) {
        return service.pilotReadiness(tenantId);
    }

    @PatchMapping("/workspaces/{tenantId}/readiness")
    PilotReadinessItem updatePilotReadiness(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID tenantId,
                                            @RequestBody UpdatePilotReadiness request) {
        return service.updatePilotReadiness(tenantId, request, user.email());
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
