package com.sauti.tenant.application;

import com.sauti.tenant.TenantRepository;
import com.sauti.tenant.application.WorkspaceViews.InvitationPreview;
import com.sauti.tenant.application.WorkspaceViews.InvitationView;
import com.sauti.tenant.application.WorkspaceViews.WorkspaceMembershipView;
import com.sauti.tenant.application.port.InvitationMailer.InvitationEmail;
import com.sauti.tenant.application.port.MemberDirectory;
import com.sauti.tenant.domain.InvitationLimitExceededException;
import com.sauti.tenant.domain.InvitationUnavailableException;
import com.sauti.tenant.domain.MembershipPolicy;
import com.sauti.tenant.domain.MembershipRepository;
import com.sauti.tenant.domain.MembershipRuleException;
import com.sauti.tenant.domain.WorkspaceInvitation;
import com.sauti.tenant.domain.WorkspaceInvitationRepository;
import com.sauti.tenant.domain.WorkspaceNotFoundException;
import com.sauti.tenant.domain.WorkspaceRole;
import com.sauti.tenant.domain.WorkspaceStatus;
import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inviting people into a workspace by email. The raw token exists only in the email link; the
 * database keeps its SHA-256 hash. The email is sent after the transaction commits.
 */
@Service
public class InvitationService {
    static final int MAX_PENDING = 50;
    static final int MAX_PER_DAY = 30;

    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final WorkspaceInvitationRepository invitations;
    private final MembershipService membershipService;
    private final MemberDirectory directory;
    private final ApplicationEventPublisher events;
    private final String dashboardBaseUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public InvitationService(TenantRepository tenants, MembershipRepository memberships,
                             WorkspaceInvitationRepository invitations, MembershipService membershipService,
                             MemberDirectory directory, ApplicationEventPublisher events,
                             @Value("${sauti.dashboard.base-url:https://sauti.uk}") String dashboardBaseUrl) {
        this(tenants, memberships, invitations, membershipService, directory, events, dashboardBaseUrl, Clock.systemUTC());
    }

    InvitationService(TenantRepository tenants, MembershipRepository memberships,
                      WorkspaceInvitationRepository invitations, MembershipService membershipService,
                      MemberDirectory directory, ApplicationEventPublisher events, String dashboardBaseUrl, Clock clock) {
        this.tenants = tenants;
        this.memberships = memberships;
        this.invitations = invitations;
        this.membershipService = membershipService;
        this.directory = directory;
        this.events = events;
        this.dashboardBaseUrl = dashboardBaseUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    /** Invites someone, or re-sends the open invitation if that email already has one. */
    @Transactional
    public InvitationView invite(UUID workspaceId, UUID actorUserId, String email, WorkspaceRole role) {
        var tenant = tenants.findLockedById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
        var actor = memberships.find(workspaceId, actorUserId).orElseThrow(WorkspaceNotFoundException::new);
        MembershipPolicy.requireCanInvite(actor, role);
        var normalized = WorkspaceInvitation.normalizeEmail(email);
        var existingUser = directory.findUserIdByEmail(normalized);
        if (existingUser.isPresent() && memberships.find(workspaceId, existingUser.get()).isPresent()) {
            throw new MembershipRuleException("This person is already a member of the workspace");
        }
        var now = now();
        var open = invitations.openInvitations(workspaceId).stream().filter(i -> i.isFor(normalized)).findFirst();
        var token = newToken();
        WorkspaceInvitation invitation;
        if (open.isPresent()) {
            invitation = open.get();
            invitation.reissue(hash(token), now);
        } else {
            requireWithinLimits(workspaceId, now);
            invitation = new WorkspaceInvitation(workspaceId, normalized, role, hash(token), actorUserId, now);
        }
        invitations.save(invitation);
        publishEmail(invitation, tenant.getBusinessName(), actorUserId, token);
        recordInvitation(workspaceId, actorUserId, open.isPresent() ? "invitation.resent" : "invitation.sent", invitation,
                (open.isPresent() ? "Sent a new invitation link to " : "Invited ") + invitation.email() + " as " + MembershipService.label(invitation.role()));
        return InvitationView.of(invitation, now);
    }

    @Transactional(readOnly = true)
    public List<InvitationView> openInvitations(UUID workspaceId) {
        var now = now();
        return invitations.openInvitations(workspaceId).stream().map(i -> InvitationView.of(i, now)).toList();
    }

    @Transactional
    public InvitationView resend(UUID workspaceId, UUID actorUserId, UUID invitationId) {
        var tenant = tenants.findById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
        var invitation = invitationIn(workspaceId, invitationId);
        MembershipPolicy.requireCanInvite(actor(workspaceId, actorUserId), invitation.role());
        var token = newToken();
        invitation.reissue(hash(token), now());
        invitations.save(invitation);
        publishEmail(invitation, tenant.getBusinessName(), actorUserId, token);
        recordInvitation(workspaceId, actorUserId, "invitation.resent", invitation, "Sent a new invitation link to " + invitation.email());
        return InvitationView.of(invitation, now());
    }

    @Transactional
    public void revoke(UUID workspaceId, UUID actorUserId, UUID invitationId) {
        var invitation = invitationIn(workspaceId, invitationId);
        MembershipPolicy.requireCanInvite(actor(workspaceId, actorUserId), invitation.role());
        invitation.revoke(now());
        invitations.save(invitation);
        recordInvitation(workspaceId, actorUserId, "invitation.cancelled", invitation, "Cancelled the invitation for " + invitation.email());
    }

    /** What the invitation link shows before anyone signs in. Unknown and used tokens look the same. */
    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        var invitation = pendingByToken(token);
        var tenant = openWorkspace(invitation.workspaceId());
        return new InvitationPreview(tenant.getBusinessName(), invitation.email(), invitation.role(),
                invitation.expiresAt(), directory.findUserIdByEmail(invitation.email()).isPresent());
    }

    /**
     * Accepts an invitation for a signed-in person. The account email must match the invited email,
     * so a forwarded link cannot be used by someone else.
     */
    @Transactional
    public WorkspaceMembershipView accept(String token, UUID userId, String userEmail) {
        var invitation = pendingByToken(token);
        if (!invitation.isFor(userEmail)) {
            throw new MembershipRuleException("This invitation was sent to " + invitation.email()
                    + ". Sign in with that email to accept it.");
        }
        var tenant = openWorkspace(invitation.workspaceId());
        invitation.accept(userId, now());
        invitations.save(invitation);
        var membership = membershipService.join(invitation.workspaceId(), userId, invitation.role());
        return WorkspaceMembershipView.of(tenant, membership.role());
    }

    private void requireWithinLimits(UUID workspaceId, OffsetDateTime now) {
        if (invitations.pendingCount(workspaceId, now) >= MAX_PENDING) {
            throw new InvitationLimitExceededException("This workspace has too many open invitations. Revoke some first.");
        }
        if (invitations.countByWorkspaceIdAndCreatedAtAfter(workspaceId, now.minus(Duration.ofDays(1))) >= MAX_PER_DAY) {
            throw new InvitationLimitExceededException("Daily invitation limit reached. Try again tomorrow.");
        }
    }

    private void publishEmail(WorkspaceInvitation invitation, String workspaceName, UUID inviterId, String token) {
        var inviter = directory.profiles(List.of(inviterId)).get(inviterId);
        events.publishEvent(new InvitationIssued(new InvitationEmail(
                invitation.email(),
                workspaceName,
                inviter == null ? "" : inviter.email(),
                invitation.role().name(),
                dashboardBaseUrl + "/invitations/accept#token=" + token,
                invitation.expiresAt())));
    }

    private void recordInvitation(UUID workspaceId, UUID actorUserId, String action, WorkspaceInvitation invitation, String summary) {
        events.publishEvent(WorkspaceActivity.byPerson(workspaceId, actorUserId, action, "invitation",
                invitation.id().toString(), summary));
    }

    private WorkspaceInvitation pendingByToken(String token) {
        if (token == null || token.isBlank()) throw new InvitationUnavailableException();
        var invitation = invitations.findByTokenHash(hash(token.trim())).orElseThrow(InvitationUnavailableException::new);
        if (!invitation.isPendingAt(now())) throw new InvitationUnavailableException();
        return invitation;
    }

    private com.sauti.tenant.Tenant openWorkspace(UUID workspaceId) {
        return tenants.findById(workspaceId)
                .filter(tenant -> tenant.workspaceStatus() == WorkspaceStatus.ACTIVE)
                .orElseThrow(InvitationUnavailableException::new);
    }

    private WorkspaceInvitation invitationIn(UUID workspaceId, UUID invitationId) {
        return invitations.findByIdAndWorkspaceId(invitationId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));
    }

    private com.sauti.tenant.domain.Membership actor(UUID workspaceId, UUID userId) {
        return memberships.find(workspaceId, userId).orElseThrow(WorkspaceNotFoundException::new);
    }

    private String newToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /** Published inside the transaction; delivered by the mail adapter after commit. */
    public record InvitationIssued(InvitationEmail email) {
    }
}
