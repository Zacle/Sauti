package com.sauti.auth;

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
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantDtos.TenantResponse;
import com.sauti.tenant.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthEmailService authEmailService;
    private final GoogleOAuthService googleOAuthService;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenDays;
    private final boolean exposeDevTokens;

    public AuthService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuthEmailService authEmailService,
            GoogleOAuthService googleOAuthService,
            VerificationCodeService verificationCodeService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${sauti.jwt.refresh-token-days}") long refreshTokenDays,
            @Value("${sauti.auth.expose-dev-tokens:true}") boolean exposeDevTokens
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authEmailService = authEmailService;
        this.googleOAuthService = googleOAuthService;
        this.verificationCodeService = verificationCodeService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;
        this.exposeDevTokens = exposeDevTokens;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        var email = request.email().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }
        Tenant tenant = tenantRepository.save(new Tenant(request.businessName(), email, request.countryCode()));
        User user = userRepository.save(new User(tenant, email, passwordEncoder.encode(request.password())));
        String verificationCode = verificationCodeService.generateAndStoreEmailVerificationCode(user);
        authEmailService.sendVerificationEmail(user.getEmail(), tenant.getBusinessName(), verificationCode);
        return new RegisterResponse(
                "verification_required",
                "Registration successful. Verify your email before logging in.",
                exposeDevTokens ? verificationCode : null,
                TenantResponse.from(tenant)
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        if (!user.isEmailVerified()) {
            throw new UnverifiedEmailException("Verify your email before logging in");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        var profile = googleOAuthService.verifyIdToken(request.idToken());
        return issueTokens(findOrCreateGoogleUser(profile, request.businessName(), request.countryCode()));
    }

    @Transactional
    public AuthResponse loginWithGoogleProfile(
            GoogleOAuthService.GoogleProfile profile,
            String businessName,
            String countryCode
    ) {
        return issueTokens(findOrCreateGoogleUser(profile, businessName, countryCode));
    }

    @Transactional
    public AuthResponse loginWithGoogleAuthorizationCode(String code, String state) {
        var result = googleOAuthService.exchangeCodeWithContext(code, state);
        return issueTokens(findOrCreateGoogleUser(
                result.profile(),
                result.context().businessName(),
                result.context().countryCode()
        ));
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.isEmailVerified()) {
            return issueTokens(user);
        }
        if (!verificationCodeService.verifyEmailCode(user, request.code())) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        user.verifyEmail();
        verificationCodeService.deleteEmailVerificationCode(user);
        return issueTokens(user);
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        var user = userRepository.findByEmail(request.email().toLowerCase()).orElse(null);
        if (user == null || user.isEmailVerified()) {
            return new MessageResponse("ok", "If verification is needed, a new email has been sent.", null);
        }
        String code = verificationCodeService.generateAndStoreEmailVerificationCode(user);
        authEmailService.sendVerificationEmail(user.getEmail(), user.getTenant().getBusinessName(), code);
        return new MessageResponse(
                "ok",
                "If verification is needed, a new email has been sent.",
                exposeDevTokens ? code : null
        );
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        var user = userRepository.findByEmail(request.email().toLowerCase()).orElse(null);
        if (user == null) {
            return new MessageResponse("ok", "If the account exists, password reset instructions have been sent.", null);
        }
        String code = verificationCodeService.generateAndStorePasswordResetCode(user);
        authEmailService.sendPasswordResetEmail(user.getEmail(), user.getTenant().getBusinessName(), code);
        return new MessageResponse(
                "ok",
                "If the account exists, password reset instructions have been sent.",
                exposeDevTokens ? code : null
        );
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!verificationCodeService.verifyPasswordResetCode(user, request.code())) {
            throw new IllegalArgumentException("Invalid or expired reset code");
        }
        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        verificationCodeService.deletePasswordResetCode(user);
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId()).forEach(RefreshToken::revoke);
        return new MessageResponse("ok", "Password has been reset.", null);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        var existing = refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (!existing.isActive()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        existing.revoke();
        return issueTokens(existing.getUser());
    }

    @Transactional
    public MessageResponse logout(RefreshRequest request) {
        refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .ifPresent(RefreshToken::revoke);
        return new MessageResponse("ok", "Logged out.", null);
    }

    private AuthResponse issueTokens(User user) {
        var refreshToken = UUID.randomUUID() + "." + UUID.randomUUID();
        refreshTokenRepository.save(new RefreshToken(
                user,
                hash(refreshToken),
                OffsetDateTime.now().plusDays(refreshTokenDays)
        ));
        return new AuthResponse(jwtService.issueAccessToken(user), refreshToken, TenantResponse.from(user.getTenant()));
    }

    private User findOrCreateGoogleUser(GoogleOAuthService.GoogleProfile profile, String requestedBusinessName, String requestedCountryCode) {
        var email = profile.email().toLowerCase();
        return userRepository.findByEmail(email)
                .map(user -> {
                    if (!user.isEmailVerified()) {
                        user.verifyEmail();
                        verificationCodeService.deleteEmailVerificationCode(user);
                    }
                    return user;
                })
                .orElseGet(() -> {
                    Tenant tenant = tenantRepository.save(new Tenant(
                            businessName(requestedBusinessName, profile),
                            email,
                            countryCode(requestedCountryCode)
                    ));
                    User user = new User(tenant, email, passwordEncoder.encode(UUID.randomUUID().toString()));
                    user.verifyEmail();
                    return userRepository.save(user);
                });
    }

    private String businessName(String requestedBusinessName, GoogleOAuthService.GoogleProfile profile) {
        if (requestedBusinessName != null && !requestedBusinessName.isBlank()) {
            return requestedBusinessName.trim();
        }
        if (profile.name() != null && !profile.name().isBlank()) {
            return profile.name().trim();
        }
        var localPart = profile.email().split("@", 2)[0].replace('.', ' ').replace('_', ' ').trim();
        return localPart.isBlank() ? "Sauti Workspace" : localPart;
    }

    private String countryCode(String requestedCountryCode) {
        if (requestedCountryCode != null && requestedCountryCode.trim().length() == 2) {
            return requestedCountryCode.trim().toUpperCase();
        }
        return "SN";
    }

    private String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
