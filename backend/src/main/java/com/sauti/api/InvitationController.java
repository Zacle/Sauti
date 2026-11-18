package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.auth.application.AccountQueries;
import com.sauti.auth.application.AccountRegistration;
import com.sauti.auth.application.AccountSettings;
import com.sauti.auth.application.AuthDtos.AcceptInvitationAsNewAccountRequest;
import com.sauti.auth.application.AuthDtos.AcceptInvitationRequest;
import com.sauti.auth.application.AuthDtos.SessionResponse;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.auth.application.SessionService;
import com.sauti.shared.ClientAddressResolver;
import com.sauti.tenant.application.InvitationService;
import com.sauti.tenant.application.WorkspaceViews.InvitationPreview;
import com.sauti.tenant.application.WorkspaceViews.WorkspaceMembershipView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opening a workspace invitation link. The token travels in a header or body, never in a URL,
 * so it does not end up in access logs.
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {
    static final String TOKEN_HEADER = "X-Sauti-Invitation";

    private final InvitationService invitations;
    private final AccountRegistration registration;
    private final AccountSettings settings;
    private final SessionService sessions;
    private final AccountQueries accounts;
    private final AuthRateLimitService rateLimits;
    private final ClientAddressResolver clientAddresses;
    private final SessionCookies cookies;

    public InvitationController(InvitationService invitations, AccountRegistration registration,
                                AccountSettings settings, SessionService sessions, AccountQueries accounts,
                                AuthRateLimitService rateLimits, ClientAddressResolver clientAddresses,
                                SessionCookies cookies) {
        this.invitations = invitations;
        this.registration = registration;
        this.settings = settings;
        this.sessions = sessions;
        this.accounts = accounts;
        this.rateLimits = rateLimits;
        this.clientAddresses = clientAddresses;
        this.cookies = cookies;
    }

    @GetMapping("/preview")
    InvitationPreview preview(HttpServletRequest http, @RequestHeader(TOKEN_HEADER) String token) {
        rateLimits.checkInvitationLink(clientAddresses.resolve(http));
        return invitations.preview(token);
    }

    /** For someone who already has an account and is signed in with the invited email. */
    @PostMapping("/accept")
    WorkspaceMembershipView accept(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody AcceptInvitationRequest request) {
        var joined = invitations.accept(request.token(), user.userId(), user.email());
        settings.rememberWorkspace(user.userId(), joined.workspace().id());
        return joined;
    }

    /** For someone new: creates the account, joins the workspace and signs them in. */
    @PostMapping("/accept-new")
    SessionResponse acceptAsNewAccount(HttpServletRequest http, HttpServletResponse response,
                                       @Valid @RequestBody AcceptInvitationAsNewAccountRequest request) {
        rateLimits.checkInvitationLink(clientAddresses.resolve(http));
        var user = registration.acceptInvitationAsNewAccount(request.token(), request.password());
        var grant = sessions.start(user, http.getHeader(HttpHeaders.USER_AGENT));
        cookies.issue(response, grant.refreshToken(), grant.refreshExpiresAt());
        return accounts.session(grant);
    }
}
