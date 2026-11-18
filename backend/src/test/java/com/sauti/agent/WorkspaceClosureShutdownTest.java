package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.tenant.TenantRepository;
import com.sauti.testsupport.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Closing a workspace over HTTP stops its phone service: agents are switched off and their numbers
 * released. Not transactional on purpose, so the after-commit release really runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceClosureShutdownTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired TenantRepository tenants;
    @Autowired AgentRepository agents;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void closingAWorkspaceSwitchesOffAgentsAndReleasesTheirNumbers() throws Exception {
        var owner = accounts.owner("Closing Clinic", "closing-owner-" + System.nanoTime() + "@example.com", "KE");
        var tenant = tenants.findById(owner.workspaceId()).orElseThrow();
        var agent = new Agent(tenant, "Front desk", "Hello", "Help callers");
        agent.attachTwilioNumber("+2547" + String.valueOf(System.nanoTime()).substring(0, 8));
        agent.activate();
        agent = agents.save(agent);

        mvc.perform(delete(owner.path("")).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());

        var after = agents.findById(agent.getId()).orElseThrow();
        assertThat(after.isActive()).isFalse();
        assertThat(after.getTwilioPhoneNumber()).isNull();
        assertThat(after.getPhoneNumberStatus()).isEqualTo("released");
    }
}
