package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.tenant.application.ApiKeyService;
import com.sauti.tenant.application.ApiKeyService.ApiKeyView;
import com.sauti.tenant.application.ApiKeyService.CreatedApiKey;
import com.sauti.tenant.application.InvitationService;
import com.sauti.tenant.application.WorkspaceDtos.CreateApiKeyRequest;
import com.sauti.tenant.domain.ApiKeyAccess;
import com.sauti.tenant.application.MembershipService;
import com.sauti.tenant.application.WorkspaceDtos.ChangeRoleRequest;
import com.sauti.tenant.application.WorkspaceDtos.CreateWorkspaceRequest;
import com.sauti.tenant.application.WorkspaceDtos.InviteRequest;
import com.sauti.tenant.application.WorkspaceDtos.TransferOwnershipRequest;
import com.sauti.tenant.application.WorkspaceExportService;
import com.sauti.tenant.application.WorkspaceService;
import com.sauti.tenant.application.WorkspaceViews.InvitationView;
import com.sauti.tenant.application.WorkspaceViews.MemberView;
import com.sauti.tenant.application.WorkspaceViews.WorkspaceMembershipView;
import com.sauti.tenant.domain.WorkspaceRole;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workspaces and who is in them. Routes under /{workspaceId} have already passed the membership,
 * status and route-permission checks; the services enforce the rules that depend on the target
 * member, such as Owner-only changes.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaces;
    private final MembershipService members;
    private final InvitationService invitations;
    private final ApiKeyService apiKeys;
    private final WorkspaceExportService exports;

    public WorkspaceController(WorkspaceService workspaces, MembershipService members, InvitationService invitations,
                               ApiKeyService apiKeys, WorkspaceExportService exports) {
        this.exports = exports;
        this.workspaces = workspaces;
        this.members = members;
        this.invitations = invitations;
        this.apiKeys = apiKeys;
    }

    @GetMapping
    List<WorkspaceMembershipView> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return workspaces.workspacesFor(user.userId());
    }

    /** Any signed-in person may create a workspace. Paid provider use stays blocked until Sauti approves it. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceMembershipView create(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaces.create(user.userId(), user.email(), request.businessName(), request.countryCode());
    }

    @GetMapping("/{workspaceId}")
    WorkspaceMembershipView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId) {
        return workspaces.workspaceFor(user.userId(), user.tenantId());
    }

    @DeleteMapping("/{workspaceId}")
    ResponseEntity<Void> close(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId) {
        workspaces.close(user.tenantId(), user.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceId}/members")
    List<MemberView> members(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId) {
        return members.members(user.tenantId());
    }

    @PatchMapping("/{workspaceId}/members/{userId}")
    MemberView changeRole(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId,
                          @PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request) {
        return members.changeRole(user.tenantId(), user.userId(), userId, WorkspaceRole.parse(request.role()));
    }

    /** Removing yourself is leaving the workspace. */
    @DeleteMapping("/{workspaceId}/members/{userId}")
    ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId,
                                      @PathVariable UUID userId) {
        members.remove(user.tenantId(), user.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{workspaceId}/transfer-ownership")
    ResponseEntity<Void> transferOwnership(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID workspaceId,
                                           @Valid @RequestBody TransferOwnershipRequest request) {
        members.transferOwnership(user.tenantId(), user.userId(), request.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceId}/invitations")
    List<InvitationView> invitations(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId) {
        return invitations.openInvitations(user.tenantId());
    }

    @PostMapping("/{workspaceId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationView invite(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId,
                          @Valid @RequestBody InviteRequest request) {
        return invitations.invite(user.tenantId(), user.userId(), request.email(), WorkspaceRole.parse(request.role()));
    }

    @PostMapping("/{workspaceId}/invitations/{invitationId}/resend")
    InvitationView resend(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId,
                          @PathVariable UUID invitationId) {
        return invitations.resend(user.tenantId(), user.userId(), invitationId);
    }

    @DeleteMapping("/{workspaceId}/invitations/{invitationId}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId,
                                @PathVariable UUID invitationId) {
        invitations.revoke(user.tenantId(), user.userId(), invitationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceId}/api-keys")
    List<ApiKeyView> apiKeys(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId) {
        return apiKeys.keys(user.tenantId());
    }

    /** The response holds the full key. It is shown once and cannot be retrieved again. */
    @PostMapping("/{workspaceId}/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedApiKey createApiKey(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId,
                              @Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeys.create(user.tenantId(), user.userId(), request.name(), ApiKeyAccess.parse(request.access()));
    }

    @DeleteMapping("/{workspaceId}/api-keys/{keyId}")
    ResponseEntity<Void> revokeApiKey(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID workspaceId,
                                      @PathVariable UUID keyId) {
        apiKeys.revoke(user.tenantId(), user.userId(), keyId);
        return ResponseEntity.noContent().build();
    }

    /** Everything the workspace holds, as one JSON file. Owner only. */
    @GetMapping("/{workspaceId}/export")
    ResponseEntity<java.util.Map<String, Object>> export(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @PathVariable UUID workspaceId) {
        var body = exports.export(user.tenantId(), user.userId());
        var filename = "sauti-workspace-" + java.time.LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
