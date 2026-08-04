package com.sauti.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.sauti.auth.JwtService;
import com.sauti.auth.AuthEmailService;
import com.sauti.auth.AuthRateLimitService;
import com.sauti.auth.User;
import com.sauti.auth.UserRepository;
import com.sauti.auth.VerificationCodeService;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.sauti.provisioning.PilotProvisioningPolicyRepository;

@SpringBootTest(properties = {
        "sauti.admin.emails=platform-admin@sauti.test",
        "sauti.auth.expose-dev-tokens=true",
        "spring.datasource.url=jdbc:h2:mem:pilot-invite-acceptance;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@RecordApplicationEvents
class PilotInvitationAdminAcceptanceTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired DemoRequestRepository demoRequests;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;
    @Autowired PilotProvisioningPolicyRepository provisioningPolicies;
    @MockitoBean VerificationCodeService verificationCodes;
    @MockitoBean AuthEmailService authEmailService;
    @MockitoBean AuthRateLimitService authRateLimits;

    @Test
    void adminApprovalProducesInvitationAcceptedByPublicJourneyExactlyOnce() throws Exception {
        org.mockito.Mockito.when(verificationCodes.generateAndStoreEmailVerificationCode(
                org.mockito.ArgumentMatchers.any(User.class))).thenReturn("123456");
        var adminTenant = tenants.save(new Tenant("Sauti operations", "platform-admin@sauti.test", "GB"));
        var admin = new User(adminTenant, "platform-admin@sauti.test", passwords.encode("password123"));
        admin.verifyEmail();
        users.save(admin);
        var request = demoRequests.save(new DemoRequest(
                "Acme Health", "Amina", "acceptance-owner@example.com", "KE", "+254700000000",
                "Healthcare", "under-100", "voice", "Answer and book patient calls", null
        ));
        mvc.perform(post("/api/v1/admin/demo-requests/{requestId}/invitation", request.getId())
                        .header("Authorization", "Bearer " + jwt.issueAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("acceptance-owner@example.com"));

        var token = events.stream(PilotInvitationIssued.class)
                .map(PilotInvitationIssued::rawToken)
                .findFirst()
                .orElseThrow();

        mvc.perform(get("/api/v1/public/pilot-invitations/preview")
                        .header("X-Sauti-Pilot-Invitation", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Acme Health"))
                .andExpect(jsonPath("$.email").value("acceptance-owner@example.com"));

        mvc.perform(post("/api/v1/public/pilot-invitations/accept")
                        .header("X-Sauti-Pilot-Invitation", token)
                        .contentType("application/json")
                        .content("{\"password\":\"secure-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verification_required"));

        assertThat(users.existsByEmail("acceptance-owner@example.com")).isTrue();
        assertThat(tenants.findByEmail("acceptance-owner@example.com")).isPresent();
        var createdTenant = tenants.findByEmail("acceptance-owner@example.com").orElseThrow();
        var policy = provisioningPolicies.findByTenantId(createdTenant.getId()).orElseThrow();
        assertThat(policy.getStatus()).isEqualTo("pending");
        assertThat(policy.getMonthlyBudget()).isZero();
        assertThat(policy.isPhoneNumbersApproved()).isFalse();

        org.mockito.Mockito.when(verificationCodes.verifyEmailCode(
                org.mockito.ArgumentMatchers.any(User.class), org.mockito.ArgumentMatchers.eq("123456")))
                .thenReturn(true);
        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("{\"email\":\"acceptance-owner@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("{\"email\":\"acceptance-owner@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());
        verify(authEmailService, times(1)).sendWelcomeEmail("acceptance-owner@example.com", "Acme Health");

        mvc.perform(post("/api/v1/public/pilot-invitations/accept")
                        .header("X-Sauti-Pilot-Invitation", token)
                .contentType("application/json")
                .content("{\"password\":\"secure-password-123\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("Invitation is expired or already used"));
    }

}
