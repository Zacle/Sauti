package com.sauti.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.sauti.auth.application.port.AuthMailer;
import com.sauti.auth.application.port.OneTimeCodes;
import com.sauti.auth.domain.OneTimeCodePurpose;
import com.sauti.tenant.application.WorkspaceService;
import com.sauti.testsupport.TestAccounts;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
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
    @Autowired TestAccounts accounts;
    @Autowired WorkspaceService workspaces;
    @Autowired ApplicationEvents events;
    @Autowired PilotProvisioningPolicyRepository provisioningPolicies;
    @MockitoBean OneTimeCodes verificationCodes;
    @MockitoBean AuthMailer authEmailService;
    @MockitoBean AuthRateLimitService authRateLimits;

    @Test
    void adminApprovalProducesInvitationAcceptedByPublicJourneyExactlyOnce() throws Exception {
        org.mockito.Mockito.when(verificationCodes.issue(OneTimeCodePurpose.EMAIL_VERIFICATION, "acceptance-owner@example.com"))
                .thenReturn("123456");
        var admin = accounts.verifiedUser("platform-admin@sauti.test");
        var request = demoRequests.save(new DemoRequest(
                "Acme Health", "Amina", "acceptance-owner@example.com", "KE", "+254700000000",
                "Healthcare", "under-100", "voice", "Answer and book patient calls", null
        ));
        mvc.perform(post("/api/v1/admin/demo-requests/{requestId}/invitation", request.getId())
                        .header("Authorization", "Bearer " + accounts.tokenFor(admin)))
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
        var owner = users.findByEmail("acceptance-owner@example.com").orElseThrow();
        var createdWorkspace = workspaces.workspacesFor(owner.getId()).get(0).workspace();
        assertThat(createdWorkspace.businessName()).isEqualTo("Acme Health");
        var policy = provisioningPolicies.findByTenantId(createdWorkspace.id()).orElseThrow();
        assertThat(policy.getStatus()).isEqualTo("pending");
        assertThat(policy.getMonthlyBudget()).isZero();
        assertThat(policy.isPhoneNumbersApproved()).isFalse();

        org.mockito.Mockito.when(verificationCodes.verify(OneTimeCodePurpose.EMAIL_VERIFICATION, "acceptance-owner@example.com", "123456"))
                .thenReturn(true);
        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("{\"email\":\"acceptance-owner@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("{\"email\":\"acceptance-owner@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());
        verify(authEmailService, times(1)).sendWelcome("acceptance-owner@example.com", "Acme Health");

        mvc.perform(post("/api/v1/public/pilot-invitations/accept")
                        .header("X-Sauti-Pilot-Invitation", token)
                .contentType("application/json")
                .content("{\"password\":\"secure-password-123\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("Invitation is expired or already used"));
    }

}
