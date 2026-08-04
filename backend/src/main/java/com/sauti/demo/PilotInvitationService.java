package com.sauti.demo;

import com.sauti.auth.AuthDtos.RegisterResponse;
import com.sauti.auth.AuthService;
import com.sauti.auth.UserRepository;
import com.sauti.demo.PilotInvitationDtos.InvitationIssued;
import com.sauti.demo.PilotInvitationDtos.InvitationPreview;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PilotInvitationService {
    private static final Duration VALIDITY = Duration.ofHours(72);
    private final DemoRequestRepository requests;
    private final PilotInvitationRepository invitations;
    private final UserRepository users;
    private final AuthService authService;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public PilotInvitationService(DemoRequestRepository requests, PilotInvitationRepository invitations,
                                  UserRepository users, AuthService authService, ApplicationEventPublisher events) {
        this.requests = requests;
        this.invitations = invitations;
        this.users = users;
        this.authService = authService;
        this.events = events;
    }

    @Transactional
    public InvitationIssued issue(UUID demoRequestId) {
        var request = requests.findById(demoRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Demo request not found"));
        if (invitations.existsByDemoRequestId(demoRequestId)) {
            throw new IllegalStateException("An invitation has already been issued for this demo request");
        }
        if (users.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("This email already belongs to a Sauti workspace");
        }
        var token = newToken();
        var invitation = invitations.save(new PilotInvitation(request, hash(token), OffsetDateTime.now().plus(VALIDITY)));
        request.markInvited();
        events.publishEvent(new PilotInvitationIssued(invitation, token));
        return new InvitationIssued(invitation.getId(), invitation.getEmail(), invitation.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        var invitation = invitations.findByTokenHash(hash(requiredToken(token)))
                .orElseThrow(() -> new IllegalArgumentException("Invitation is invalid"));
        requireAvailable(invitation);
        return new InvitationPreview(invitation.getBusinessName(), invitation.getContactName(),
                invitation.getEmail(), invitation.getCountryCode(), invitation.getExpiresAt());
    }

    @Transactional
    public RegisterResponse accept(String token, String password) {
        var now = OffsetDateTime.now();
        var invitation = invitations.findLockedByTokenHash(hash(requiredToken(token)))
                .orElseThrow(() -> new IllegalArgumentException("Invitation is invalid"));
        requireAvailable(invitation);
        var response = authService.registerInvited(invitation.getBusinessName(), invitation.getEmail(),
                invitation.getCountryCode(), password);
        invitation.accept(now);
        requests.findById(invitation.getDemoRequestId()).ifPresent(DemoRequest::markActivated);
        return response;
    }

    private void requireAvailable(PilotInvitation invitation) {
        if (!invitation.isAvailableAt(OffsetDateTime.now())) {
            throw new PilotInvitationUnavailableException();
        }
    }

    private String newToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String requiredToken(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Invitation token is required");
        return token.trim();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
