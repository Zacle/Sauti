package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.auth.InvalidSessionException;
import com.sauti.auth.RegistrationClosedException;
import com.sauti.auth.application.AccountQueries;
import com.sauti.auth.application.AccountRegistration;
import com.sauti.auth.application.AccountSettings;
import com.sauti.auth.application.AdminSignIn;
import com.sauti.auth.application.AuthDtos.AdminChallengeRequest;
import com.sauti.auth.application.AuthDtos.AdminCodeRequest;
import com.sauti.auth.application.AuthDtos.ChangePasswordRequest;
import com.sauti.auth.application.AuthDtos.ConsoleAppearanceRequest;
import com.sauti.auth.application.AuthDtos.ConsoleAppearanceResponse;
import com.sauti.auth.application.AuthDtos.ForgotPasswordRequest;
import com.sauti.auth.application.AuthDtos.GoogleLoginRequest;
import com.sauti.auth.application.AuthDtos.LoginRequest;
import com.sauti.auth.application.AuthDtos.MessageResponse;
import com.sauti.auth.application.AuthDtos.RegisterRequest;
import com.sauti.auth.application.AuthDtos.RegisterResponse;
import com.sauti.auth.application.AuthDtos.ResendVerificationRequest;
import com.sauti.auth.application.AuthDtos.ResetPasswordRequest;
import com.sauti.auth.application.AuthDtos.SessionResponse;
import com.sauti.auth.application.AuthDtos.VerifyEmailRequest;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.auth.application.EmailVerification;
import com.sauti.auth.application.GoogleSignIn;
import com.sauti.auth.application.PasswordRecovery;
import com.sauti.auth.application.PasswordSignIn;
import com.sauti.auth.application.SessionService;
import com.sauti.auth.application.port.GoogleIdentityProvider;
import com.sauti.auth.domain.User;
import com.sauti.shared.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/** Sign-in, sessions and account recovery. Every successful sign-in sets the refresh cookie. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AccountRegistration registration;
    private final PasswordSignIn passwordSignIn;
    private final GoogleSignIn googleSignIn;
    private final GoogleIdentityProvider google;
    private final EmailVerification emailVerification;
    private final PasswordRecovery passwordRecovery;
    private final AccountSettings accountSettings;
    private final SessionService sessions;
    private final AccountQueries accounts;
    private final AuthRateLimitService rateLimits;
    private final ClientAddressResolver clientAddresses;
    private final SessionCookies cookies;
    private final AdminSignIn adminSignIn;
    private final String dashboardBaseUrl;

    public AuthController(AccountRegistration registration, PasswordSignIn passwordSignIn, GoogleSignIn googleSignIn,
                          GoogleIdentityProvider google, EmailVerification emailVerification,
                          PasswordRecovery passwordRecovery, AccountSettings accountSettings, SessionService sessions,
                          AccountQueries accounts, AuthRateLimitService rateLimits,
                          ClientAddressResolver clientAddresses, SessionCookies cookies, AdminSignIn adminSignIn,
                          @Value("${sauti.dashboard.base-url}") String dashboardBaseUrl) {
        this.adminSignIn = adminSignIn;
        this.registration = registration;
        this.passwordSignIn = passwordSignIn;
        this.googleSignIn = googleSignIn;
        this.google = google;
        this.emailVerification = emailVerification;
        this.passwordRecovery = passwordRecovery;
        this.accountSettings = accountSettings;
        this.sessions = sessions;
        this.accounts = accounts;
        this.rateLimits = rateLimits;
        this.clientAddresses = clientAddresses;
        this.cookies = cookies;
        this.dashboardBaseUrl = dashboardBaseUrl.replaceAll("/+$", "");
    }

    @PostMapping("/register")
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registration.register(request);
    }

    @PostMapping("/login")
    SessionResponse login(HttpServletRequest http, HttpServletResponse response, @Valid @RequestBody LoginRequest request) {
        rateLimits.checkLogin(clientAddresses.resolve(http), request.email());
        return startSession(passwordSignIn.signIn(request.email(), request.password()), http, response);
    }

    @PostMapping("/oauth/google")
    SessionResponse googleLogin(HttpServletRequest http, HttpServletResponse response,
                                @Valid @RequestBody GoogleLoginRequest request) {
        rateLimits.checkGoogleLogin(clientAddresses.resolve(http));
        var profile = google.verifyIdToken(request.idToken());
        return startSession(googleSignIn.signIn(profile, request.businessName(), request.countryCode()), http, response);
    }

    @GetMapping("/oauth/google/status")
    Map<String, Boolean> googleStatus() {
        return Map.of("configured", google.isConfigured());
    }

    @GetMapping("/oauth/google/authorize")
    RedirectView googleAuthorize(@RequestParam(defaultValue = "") String businessName,
                                 @RequestParam(defaultValue = "") String countryCode,
                                 @RequestParam(defaultValue = "/dashboard") String returnPath) {
        return new RedirectView(google.authorizationUrl(businessName, countryCode, returnPath));
    }

    /**
     * Google returns here. The session is set as a cookie on this redirect and the console then
     * calls /refresh; no token ever appears in a URL.
     */
    @GetMapping("/oauth/google/callback")
    RedirectView googleCallback(HttpServletRequest http, HttpServletResponse response,
                                @RequestParam(required = false) String code,
                                @RequestParam(required = false) String state,
                                @RequestParam(required = false) String error) {
        if (error != null || code == null || state == null) {
            return new RedirectView(dashboardBaseUrl + "/login?google=cancelled");
        }
        var result = google.exchangeCode(code, state);
        final User user;
        try {
            user = googleSignIn.signIn(result.profile(), result.context().businessName(), result.context().countryCode());
        } catch (RegistrationClosedException exception) {
            return new RedirectView(dashboardBaseUrl + "/request-demo?registration=closed");
        }
        var grant = sessions.start(user, http.getHeader(HttpHeaders.USER_AGENT));
        cookies.issue(response, grant.refreshToken(), grant.refreshExpiresAt());
        return new RedirectView(dashboardBaseUrl + "/oauth/callback?next="
                + URLEncoder.encode(result.context().returnPath(), StandardCharsets.UTF_8));
    }

    /** Rotates the refresh cookie and returns a new access token. A failed refresh clears the cookie. */
    @PostMapping("/refresh")
    SessionResponse refresh(HttpServletRequest http, HttpServletResponse response) {
        try {
            var grant = sessions.refresh(cookies.read(http), http.getHeader(HttpHeaders.USER_AGENT));
            cookies.issue(response, grant.refreshToken(), grant.refreshExpiresAt());
            return accounts.session(grant);
        } catch (InvalidSessionException exception) {
            cookies.clear(response);
            throw exception;
        }
    }

    @PostMapping("/logout")
    MessageResponse logout(HttpServletRequest http, HttpServletResponse response) {
        sessions.end(cookies.read(http));
        cookies.clear(response);
        return new MessageResponse("ok", "Logged out.", null);
    }

    @PostMapping("/logout-all")
    MessageResponse logoutEverywhere(@AuthenticationPrincipal AuthenticatedUser user, HttpServletResponse response) {
        sessions.endAll(user.userId());
        cookies.clear(response);
        return new MessageResponse("ok", "Signed out on every device.", null);
    }

    @PostMapping("/verify-email")
    SessionResponse verifyEmail(HttpServletRequest http, HttpServletResponse response,
                                @Valid @RequestBody VerifyEmailRequest request) {
        rateLimits.checkVerifyEmail(request.email());
        return startSession(emailVerification.verify(request.email(), request.code()), http, response);
    }

    @PostMapping("/resend-verification")
    MessageResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        rateLimits.checkResendVerification(request.email());
        return emailVerification.resend(request.email());
    }

    @PostMapping("/forgot-password")
    MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        rateLimits.checkForgotPassword(request.email());
        return passwordRecovery.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        rateLimits.checkResetPassword(request.email());
        return passwordRecovery.reset(request.email(), request.code(), request.newPassword());
    }

    /** Ends every session, then starts a fresh one for this browser so the person stays signed in here. */
    @PutMapping("/password")
    MessageResponse changePassword(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http,
                                   HttpServletResponse response, @Valid @RequestBody ChangePasswordRequest request) {
        var updated = accountSettings.changePassword(user.userId(), request.currentPassword(), request.newPassword());
        var grant = sessions.start(updated, http.getHeader(HttpHeaders.USER_AGENT));
        cookies.issue(response, grant.refreshToken(), grant.refreshExpiresAt());
        return new MessageResponse("ok", "Password changed. You were signed out on your other devices.", null);
    }

    /** Administration sign-in, step one: the password. Returns a short-lived challenge, never a session. */
    @PostMapping("/admin/login")
    AdminSignIn.Challenge adminLogin(HttpServletRequest http, @Valid @RequestBody LoginRequest request) {
        rateLimits.checkLogin(clientAddresses.resolve(http), request.email());
        return adminSignIn.checkPassword(request.email(), request.password());
    }

    @PostMapping("/admin/authenticator")
    AdminSignIn.AuthenticatorSetup adminAuthenticatorSetup(@Valid @RequestBody AdminChallengeRequest request) {
        return adminSignIn.beginAuthenticatorSetup(request.challenge());
    }

    /** Step two: the authenticator code. Only this starts a session with administration rights. */
    @PostMapping("/admin/verify")
    SessionResponse adminVerify(HttpServletRequest http, HttpServletResponse response,
                                @Valid @RequestBody AdminCodeRequest request) {
        rateLimits.checkAdminCode(adminSignIn.challengeUser(request.challenge()));
        var user = adminSignIn.verifyCode(request.challenge(), request.code());
        var grant = sessions.startElevated(user, http.getHeader(HttpHeaders.USER_AGENT));
        cookies.issue(response, grant.refreshToken(), grant.refreshExpiresAt());
        return accounts.session(grant);
    }

    @GetMapping("/appearance")
    ConsoleAppearanceResponse appearance(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountSettings.appearance(user.userId());
    }

    @PutMapping("/appearance")
    ConsoleAppearanceResponse configureAppearance(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody ConsoleAppearanceRequest request) {
        return accountSettings.configureAppearance(user.userId(), request);
    }

    private SessionResponse startSession(User user, HttpServletRequest http, HttpServletResponse response) {
        var grant = sessions.start(user, http.getHeader(HttpHeaders.USER_AGENT));
        cookies.issue(response, grant.refreshToken(), grant.refreshExpiresAt());
        return accounts.session(grant);
    }
}
