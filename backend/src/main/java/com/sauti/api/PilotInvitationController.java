package com.sauti.api;

import com.sauti.auth.AuthDtos.RegisterResponse;
import com.sauti.demo.PilotInvitationDtos.AcceptInvitation;
import com.sauti.demo.PilotInvitationDtos.InvitationIssued;
import com.sauti.demo.PilotInvitationDtos.InvitationPreview;
import com.sauti.demo.PilotInvitationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PilotInvitationController {
    private static final String INVITATION_HEADER = "X-Sauti-Pilot-Invitation";
    private final PilotInvitationService service;

    public PilotInvitationController(PilotInvitationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/operator/demo-requests/{requestId}/invitation")
    InvitationIssued issue(@PathVariable UUID requestId) {
        return service.issue(requestId);
    }

    @GetMapping("/api/v1/public/pilot-invitations/preview")
    InvitationPreview preview(@RequestHeader(INVITATION_HEADER) String token) {
        return service.preview(token);
    }

    @PostMapping("/api/v1/public/pilot-invitations/accept")
    RegisterResponse accept(@RequestHeader(INVITATION_HEADER) String token,
                            @Valid @RequestBody AcceptInvitation request) {
        return service.accept(token, request.password());
    }
}
