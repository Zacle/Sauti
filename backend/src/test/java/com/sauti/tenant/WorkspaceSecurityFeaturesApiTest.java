package com.sauti.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.auth.domain.Totp;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.domain.WorkspaceRole;
import com.sauti.testsupport.TestAccounts;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** API keys, administration two-factor sign-in, export, account deletion and the activity log, over HTTP. */
@SpringBootTest(properties = "sauti.admin.emails=ops-admin@sauti.test")
@AutoConfigureMockMvc
@Transactional
class WorkspaceSecurityFeaturesApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired UserRepository users;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void apiKeysReachOnlyTheirWorkspaceFeaturesWithinTheirAccess() throws Exception {
        var owner = accounts.owner("Key Clinic", "key-owner@example.com", "KE");
        var other = accounts.owner("Other Clinic", "key-other@example.com", "KE");

        var created = mvc.perform(post(owner.path("/api-keys")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"CRM sync\",\"access\":\"read_only\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key.hint").value(org.hamcrest.Matchers.startsWith("sauti_sk_")))
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(created, "$.secret");
        String keyId = JsonPath.read(created, "$.key.id");
        var bearer = "Bearer " + secret;

        mvc.perform(get(owner.path("/agents")).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(post(owner.path("/agents")).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(owner.path("/members")).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get(owner.path("/api-keys")).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get(other.path("/agents")).header("Authorization", bearer)).andExpect(status().isNotFound());

        mvc.perform(get(owner.path("/api-keys")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("CRM sync"))
                .andExpect(jsonPath("$[0].secret").doesNotExist());
        mvc.perform(delete(owner.path("/api-keys/" + keyId)).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get(owner.path("/agents")).header("Authorization", bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_api_key"));
    }

    @Test
    void administrationNeedsAnAuthenticatorCodeAndOnlyThatSessionIsAdmin() throws Exception {
        accounts.verifiedUser("ops-admin@sauti.test");
        var login = "{\"email\":\"ops-admin@sauti.test\",\"password\":\"" + TestAccounts.PASSWORD + "\"}";

        var normal = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformAdminSession").value(false))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + JsonPath.read(normal, "$.accessToken")))
                .andExpect(status().isForbidden());

        var challenge = mvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticatorReady").value(false))
                .andReturn().getResponse().getContentAsString();
        String challengeToken = JsonPath.read(challenge, "$.challenge");
        var setup = mvc.perform(post("/api/v1/auth/admin/authenticator").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\":\"" + challengeToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(setup, "$.secret");

        mvc.perform(post("/api/v1/auth/admin/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\":\"" + challengeToken + "\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());
        var elevated = mvc.perform(post("/api/v1/auth/admin/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challenge\":\"" + challengeToken + "\",\"code\":\"" + Totp.codeAt(secret, Instant.now()) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformAdminSession").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + JsonPath.read(elevated, "$.accessToken")))
                .andExpect(status().isOk());

        accounts.verifiedUser("not-an-admin@example.com");
        mvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-admin@example.com\",\"password\":\"" + TestAccounts.PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownersCanExportTheWorkspaceAndAdminsCannot() throws Exception {
        var owner = accounts.owner("Export Clinic", "export-owner@example.com", "KE");
        var admin = accounts.member(owner.workspaceId(), "export-admin@example.com", WorkspaceRole.ADMIN);

        mvc.perform(get(owner.path("/export")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(jsonPath("$.format").value("sauti-workspace-export"))
                .andExpect(jsonPath("$.workspace.businessName").value("Export Clinic"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.agents").isArray())
                .andExpect(jsonPath("$.calls").isArray())
                .andExpect(jsonPath("$.bookings").isArray());
        mvc.perform(get(owner.path("/export")).header("Authorization", admin.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyoneCanDeleteTheirAccountAndASoleOwnerClosesTheirWorkspacesWithIt() throws Exception {
        var owner = accounts.owner("Delete Clinic", "delete-owner@example.com", "KE");
        var member = accounts.member(owner.workspaceId(), "delete-member@example.com", WorkspaceRole.MEMBER);
        var password = "{\"password\":\"" + TestAccounts.PASSWORD + "\"}";

        mvc.perform(delete("/api/v1/me").header("Authorization", member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/me").header("Authorization", member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(password))
                .andExpect(status().isNoContent());
        assertThat(users.findByEmail("delete-member@example.com")).isEmpty();
        assertThat(users.findById(member.userId()).orElseThrow().isDeleted()).isTrue();
        mvc.perform(get(owner.path("/members")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"delete-member@example.com\",\"password\":\"" + TestAccounts.PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        var colleague = accounts.member(owner.workspaceId(), "delete-colleague@example.com", WorkspaceRole.ADMIN);
        mvc.perform(get("/api/v1/me/deletion-impact").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Delete Clinic"))
                .andExpect(jsonPath("$[0].otherMembers").value(1));
        mvc.perform(delete("/api/v1/me").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(password))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/me").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + TestAccounts.PASSWORD + "\",\"closeOwnedWorkspaces\":true}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(owner.path("/agents")).header("Authorization", colleague.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theActivityLogRecordsMemberChangesForAdminsOnly() throws Exception {
        var owner = accounts.owner("Log Clinic", "log-owner@example.com", "KE");
        var member = accounts.member(owner.workspaceId(), "log-member@example.com", WorkspaceRole.MEMBER);

        mvc.perform(patch(owner.path("/members/" + member.userId())).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"admin\"}"))
                .andExpect(status().isOk());

        mvc.perform(get(owner.path("/activity")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("member.role_changed"))
                .andExpect(jsonPath("$[0].actor").value("log-owner@example.com"))
                .andExpect(jsonPath("$[0].summary").value("Changed log-member@example.com from Member to Admin"));

        // Once people choose a name, the members list and the log show it; the summary keeps what was written.
        mvc.perform(put("/api/v1/me/display-name").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Amina Log\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(owner.path("/activity")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[0].actor").value("Amina Log"))
                .andExpect(jsonPath("$[0].summary").value("Changed log-member@example.com from Member to Admin"));
        mvc.perform(get(owner.path("/members")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[?(@.email == 'log-owner@example.com')].displayName").value("Amina Log"))
                .andExpect(jsonPath("$[?(@.email == 'log-member@example.com')].displayName").value(org.hamcrest.Matchers.contains((Object) null)));

        var reader = accounts.member(owner.workspaceId(), "log-reader@example.com", WorkspaceRole.MEMBER);
        mvc.perform(get(owner.path("/activity")).header("Authorization", reader.bearer()))
                .andExpect(status().isForbidden());
    }
}
