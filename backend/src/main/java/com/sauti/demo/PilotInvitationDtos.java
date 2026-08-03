package com.sauti.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class PilotInvitationDtos {
    private PilotInvitationDtos() { }

    public record InvitationIssued(UUID invitationId, String email, OffsetDateTime expiresAt) { }
    public record InvitationPreview(String businessName, String contactName, String email,
                                    String countryCode, OffsetDateTime expiresAt) { }
    public record AcceptInvitation(@NotBlank @Size(min = 8, max = 200) String password) { }
}
