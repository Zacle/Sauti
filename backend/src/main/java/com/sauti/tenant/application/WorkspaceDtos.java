package com.sauti.tenant.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request bodies for the workspace, member and invitation endpoints. */
public final class WorkspaceDtos {
    private WorkspaceDtos() {
    }

    public record CreateWorkspaceRequest(
            @NotBlank @Size(max = 120) String businessName,
            @NotBlank @Size(min = 2, max = 2) String countryCode
    ) {
    }

    public record ChangeRoleRequest(@NotBlank String role) {
    }

    public record TransferOwnershipRequest(@NotNull UUID userId) {
    }

    public record InviteRequest(@NotBlank @Size(max = 255) String email, @NotBlank String role) {
    }

    public record CreateApiKeyRequest(@NotBlank @Size(max = 80) String name, @NotBlank String access) {
    }
}
