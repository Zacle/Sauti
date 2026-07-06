package com.sauti.api;

import com.sauti.auth.AuthDtos.AuthResponse;
import com.sauti.auth.AuthDtos.ForgotPasswordRequest;
import com.sauti.auth.AuthDtos.GoogleLoginRequest;
import com.sauti.auth.AuthDtos.LoginRequest;
import com.sauti.auth.AuthDtos.MessageResponse;
import com.sauti.auth.AuthDtos.RefreshRequest;
import com.sauti.auth.AuthDtos.RegisterRequest;
import com.sauti.auth.AuthDtos.RegisterResponse;
import com.sauti.auth.AuthDtos.ResendVerificationRequest;
import com.sauti.auth.AuthDtos.ResetPasswordRequest;
import com.sauti.auth.AuthDtos.VerifyEmailRequest;
import com.sauti.auth.AuthRateLimitService;
import com.sauti.auth.AuthService;
import com.sauti.auth.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthRateLimitService rateLimitService;
    private final GoogleOAuthService googleOAuthService;
    private final String dashboardBaseUrl;

    public AuthController(
            AuthService authService,
            AuthRateLimitService rateLimitService,
            GoogleOAuthService googleOAuthService,
            @Value("${sauti.dashboard.base-url}") String dashboardBaseUrl
    ) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.googleOAuthService = googleOAuthService;
        this.dashboardBaseUrl = dashboardBaseUrl;
    }

    @PostMapping("/register")
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    AuthResponse login(HttpServletRequest servletRequest, @Valid @RequestBody LoginRequest request) {
        rateLimitService.checkLogin(servletRequest);
        return authService.login(request);
    }

    @PostMapping("/oauth/google")
    AuthResponse googleLogin(HttpServletRequest servletRequest, @Valid @RequestBody GoogleLoginRequest request) {
        rateLimitService.checkLogin(servletRequest);
        return authService.loginWithGoogle(request);
    }

    @GetMapping("/oauth/google/status")
    java.util.Map<String, Boolean> googleStatusEndpoint() {
        return java.util.Map.of("configured", googleOAuthService.isConfigured());
    }

    @GetMapping("/oauth/google/authorize")
    RedirectView googleAuthorize(
            @RequestParam(defaultValue = "") String businessName,
            @RequestParam(defaultValue = "") String countryCode,
            @RequestParam(defaultValue = "/dashboard") String returnPath
    ) {
        return new RedirectView(googleOAuthService.authorizationUrl(businessName, countryCode, returnPath));
    }

    @GetMapping("/oauth/google/callback")
    RedirectView googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        if (error != null || code == null || state == null) {
            return new RedirectView(dashboardBaseUrl + "/login?google=cancelled");
        }
        var result = googleOAuthService.exchangeCodeWithContext(code, state);
        var response = authService.loginWithGoogleProfile(
                result.profile(),
                result.context().businessName(),
                result.context().countryCode()
        );
        var target = dashboardBaseUrl + "/oauth/callback?next=" + encode(result.context().returnPath())
                + "#accessToken=" + encode(response.accessToken())
                + "&refreshToken=" + encode(response.refreshToken())
                + "&tenantId=" + encode(response.tenant().id().toString())
                + "&businessName=" + encode(response.tenant().businessName())
                + "&email=" + encode(response.tenant().email())
                + "&countryCode=" + encode(response.tenant().countryCode())
                + "&plan=" + encode(response.tenant().plan())
                + "&status=" + encode(response.tenant().status())
                + "&monthlyMinutesLimit=" + response.tenant().monthlyMinutesLimit()
                + "&minutesUsedThisCycle=" + response.tenant().minutesUsedThisCycle();
        return new RedirectView(target);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    MessageResponse logout(@Valid @RequestBody RefreshRequest request) {
        return authService.logout(request);
    }

    @PostMapping("/verify-email")
    AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        rateLimitService.checkVerifyEmail(request.email());
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-verification")
    MessageResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        rateLimitService.checkResendVerification(request.email());
        return authService.resendVerification(request);
    }

    @PostMapping("/forgot-password")
    MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        rateLimitService.checkForgotPassword(request.email());
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
