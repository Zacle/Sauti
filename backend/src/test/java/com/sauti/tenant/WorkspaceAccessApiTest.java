package com.sauti.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sauti.api.SessionCookies;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.provisioning.PilotProvisioningPolicyRepository;
import com.sauti.tenant.application.InvitationService.InvitationIssued;
import com.sauti.tenant.application.WorkspaceAccessService;
import com.sauti.tenant.domain.WorkspaceRole;
import com.sauti.testsupport.TestAccounts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end checks of workspace routing, roles, membership rules and invitations over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class WorkspaceAccessApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired TenantRepository tenants;
    @Autowired WorkspaceAccessService workspaceAccess;
    @Autowired PilotProvisioningPolicyRepository provisioningPolicies;
    @Autowired ApplicationEvents events;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void workspaceFeaturesNeedAWorkspaceInTheUrlAndMembershipInIt() throws Exception {
        var owner = accounts.owner("North Clinic", "north-owner@example.com", "KE");
        var outsider = accounts.owner("South Clinic", "south-owner@example.com", "KE");

        mvc.perform(get(owner.path("/agents")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/agents").header("Authorization", owner.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("workspace_required"));
        mvc.perform(get(owner.path("/agents")).header("Authorization", outsider.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/workspaces/" + UUID.randomUUID() + "/agents").header("Authorization", owner.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get(owner.path("/agents")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void membersReadAdminsConfigureAndOnlyOwnersHandleBilling() throws Exception {
        var owner = accounts.owner("Role Clinic", "role-owner@example.com", "KE");
        var member = accounts.member(owner.workspaceId(), "role-member@example.com", WorkspaceRole.MEMBER);
        var admin = accounts.member(owner.workspaceId(), "role-admin@example.com", WorkspaceRole.ADMIN);
        var agent = """
                {"name":"Amina","greetingMessage":"Hello","systemPrompt":"Help callers","defaultLanguage":"en",
                 "supportedLanguages":["en"],"ttsVoiceId":"Telnyx.NaturalHD.astra"}
                """;

        mvc.perform(get(owner.path("/agents")).header("Authorization", member.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post(owner.path("/agents")).header("Authorization", member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(agent))
                .andExpect(status().isForbidden());
        mvc.perform(post(owner.path("/agents")).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(agent))
                .andExpect(status().isOk());
        mvc.perform(post(owner.path("/billing/checkout")).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(owner.path("")).header("Authorization", admin.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aWorkspaceAlwaysKeepsAnOwner() throws Exception {
        var owner = accounts.owner("Owner Clinic", "keep-owner@example.com", "KE");
        var admin = accounts.member(owner.workspaceId(), "keep-admin@example.com", WorkspaceRole.ADMIN);

        mvc.perform(patch(owner.path("/members/" + owner.userId())).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"admin\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(owner.path("/members/" + owner.userId())).header("Authorization", admin.bearer()))
                .andExpect(status().isForbidden());

        mvc.perform(post(owner.path("/transfer-ownership")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + admin.userId() + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get(owner.path("/members")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'keep-admin@example.com')].role").value("OWNER"))
                .andExpect(jsonPath("$[?(@.email == 'keep-owner@example.com')].role").value("ADMIN"));
    }

    @Test
    void anInvitedPersonCanCreateAnAccountAndJoinOnce() throws Exception {
        var owner = accounts.owner("Invite Clinic", "invite-owner@example.com", "KE");

        mvc.perform(post(owner.path("/invitations")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"New.Person@Example.com\",\"role\":\"member\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.person@example.com"));
        var acceptUrl = events.stream(InvitationIssued.class).findFirst().orElseThrow().email().acceptUrl();
        assertThat(acceptUrl).contains("#token=");
        var token = acceptUrl.substring(acceptUrl.indexOf("#token=") + 7);

        mvc.perform(get("/api/v1/invitations/preview").header("X-Sauti-Invitation", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Invite Clinic"))
                .andExpect(jsonPath("$.accountExists").value(false));

        var accepted = mvc.perform(post("/api/v1/invitations/accept-new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"river-mango-42\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.workspaces[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.account.lastWorkspaceId").value(owner.workspaceId().toString()))
                .andReturn();
        assertThat(accepted.getResponse().getCookie(SessionCookies.NAME)).isNotNull();

        mvc.perform(post("/api/v1/invitations/accept-new")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"river-mango-42\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void anInvitationOnlyWorksForTheInvitedEmail() throws Exception {
        var owner = accounts.owner("Match Clinic", "match-owner@example.com", "KE");
        var someoneElse = accounts.owner("Elsewhere", "someone-else@example.com", "KE");

        mvc.perform(post(owner.path("/invitations")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"intended@example.com\",\"role\":\"admin\"}"))
                .andExpect(status().isCreated());
        var acceptUrl = events.stream(InvitationIssued.class).findFirst().orElseThrow().email().acceptUrl();
        var token = acceptUrl.substring(acceptUrl.indexOf("#token=") + 7);

        mvc.perform(post("/api/v1/invitations/accept").header("Authorization", someoneElse.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void suspendedAndClosedWorkspacesAreBlockedOnTheNextRequest() throws Exception {
        var owner = accounts.owner("Status Clinic", "status-owner@example.com", "KE");
        var tenant = tenants.findById(owner.workspaceId()).orElseThrow();

        tenant.suspend();
        tenants.saveAndFlush(tenant);
        workspaceAccess.evictWorkspace(owner.workspaceId());
        mvc.perform(get(owner.path("/agents")).header("Authorization", owner.bearer()))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("workspace_suspended"));

        tenant.reactivate();
        tenants.saveAndFlush(tenant);
        workspaceAccess.evictWorkspace(owner.workspaceId());
        mvc.perform(delete(owner.path("")).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get(owner.path("/agents")).header("Authorization", owner.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anyoneSignedInCanCreateAWorkspaceButItStartsWithoutSpendingApproval() throws Exception {
        var owner = accounts.owner("First Clinic", "multi-owner@example.com", "KE");

        var created = mvc.perform(post("/api/v1/workspaces").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\":\"Second Clinic\",\"countryCode\":\"tz\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.workspace.countryCode").value("TZ"))
                .andReturn();
        var secondId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.workspace.id"));

        var policy = provisioningPolicies.findByTenantId(secondId).orElseThrow();
        assertThat(policy.getStatus()).isEqualTo("pending");
        assertThat(policy.getMonthlyBudget()).isZero();

        mvc.perform(get("/api/v1/me").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces.length()").value(2))
                .andExpect(jsonPath("$.lastWorkspaceId").value(owner.workspaceId().toString()));
    }
}
