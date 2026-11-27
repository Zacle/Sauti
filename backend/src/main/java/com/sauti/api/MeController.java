package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.auth.application.AccountDeletion;
import com.sauti.auth.application.AccountQueries;
import com.sauti.auth.application.AccountSettings;
import com.sauti.auth.application.AuthDtos.AccountResponse;
import com.sauti.auth.application.AuthDtos.DeleteAccountRequest;
import com.sauti.auth.application.AuthDtos.DisplayNameRequest;
import com.sauti.auth.application.AuthDtos.RememberWorkspaceRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person and the workspaces they can open. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final AccountQueries accounts;
    private final AccountSettings settings;
    private final AccountDeletion deletion;
    private final SessionCookies cookies;

    public MeController(AccountQueries accounts, AccountSettings settings, AccountDeletion deletion, SessionCookies cookies) {
        this.accounts = accounts;
        this.settings = settings;
        this.deletion = deletion;
        this.cookies = cookies;
    }

    @GetMapping
    AccountResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.account(user.userId());
    }

    /** The name colleagues see on this person's work, such as WhatsApp replies. */
    @PutMapping("/display-name")
    AccountResponse rename(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody DisplayNameRequest request) {
        settings.rename(user.userId(), request.displayName());
        return accounts.account(user.userId());
    }

    @PutMapping("/last-workspace")
    ResponseEntity<Void> rememberWorkspace(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody RememberWorkspaceRequest request) {
        settings.rememberWorkspace(user.userId(), request.workspaceId());
        return ResponseEntity.noContent().build();
    }

    /** What deleting the account would close: workspaces where this person is the only Owner. */
    @GetMapping("/deletion-impact")
    java.util.List<com.sauti.tenant.application.MembershipService.SoleOwnedWorkspace> deletionImpact(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return deletion.impact(user.userId());
    }

    /** Deletes the signed-in account, closing workspaces it alone owns once the caller has confirmed that. */
    @DeleteMapping
    ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AuthenticatedUser user, HttpServletResponse response,
                                       @Valid @RequestBody DeleteAccountRequest request) {
        deletion.delete(user.userId(), request.password(), request.confirmation(), request.closeOwnedWorkspaces());
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
