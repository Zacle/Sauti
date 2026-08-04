package com.sauti.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.auth.AuthDtos.RegisterResponse;
import com.sauti.auth.AuthService;
import com.sauti.auth.UserRepository;
import com.sauti.tenant.TenantDtos.TenantResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PilotInvitationServiceTest {
    private final DemoRequestRepository requests = mock(DemoRequestRepository.class);
    private final PilotInvitationRepository invitations = mock(PilotInvitationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PilotInvitationService service = new PilotInvitationService(requests, invitations, users, auth, events);

    @Test
    void issuesHashedExpiringInvitationForApprovedLead() {
        var request = request();
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(invitations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var issued = service.issue(request.getId());

        assertThat(issued.email()).isEqualTo("owner@example.com");
        assertThat(request.getStatus()).isEqualTo("invited");
        var event = ArgumentCaptor.forClass(PilotInvitationIssued.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().rawToken()).hasSizeGreaterThan(40);
        assertThat(event.getValue().invitation().getTokenHash()).hasSize(64).doesNotContain(event.getValue().rawToken());
    }

    @Test
    void acceptsInvitationOnlyOnceAndCreatesAccountThroughTrustedPath() {
        var invitation = new PilotInvitation(request(), "hash", java.time.OffsetDateTime.now().plusHours(1));
        when(invitations.findLockedByTokenHash(any())).thenReturn(Optional.of(invitation));
        var response = new RegisterResponse("verification_required", "Verify", null,
                new TenantResponse(java.util.UUID.randomUUID(), "Acme", "owner@example.com", "KE", "trial", "active", 60, 0));
        when(auth.registerInvited("Acme", "owner@example.com", "KE", "password123")).thenReturn(response);

        assertThat(service.accept("secret-token", "password123")).isSameAs(response);
        assertThat(invitation.getAcceptedAt()).isNotNull();
        assertThatThrownBy(() -> service.accept("secret-token", "password123"))
                .isInstanceOf(PilotInvitationUnavailableException.class)
                .hasMessageContaining("expired or already used");
    }

    private DemoRequest request() {
        return new DemoRequest("Acme", "Amina", "owner@example.com", "KE", "+254700000000",
                "Healthcare", "under-100", "voice", "Answer calls", null);
    }
}
