package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.testsupport.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Not transactional on purpose: the request's own transaction must roll back for real. */
@SpringBootTest
@AutoConfigureMockMvc
class AgentSetupAtomicityTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired AgentRepository agents;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void aFailingVariableLeavesNoHalfCreatedAgent() throws Exception {
        var owner = accounts.owner("Atomic Clinic", "atomic-" + System.nanoTime() + "@example.com", "KE");

        mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AgentSetupApiTest.body("""
                                [{"key":"Not A Key","label":"Bad","value":"x","required":true}]
                                """)))
                .andExpect(status().isBadRequest());

        assertThat(agents.findAllByTenantIdOrderByCreatedAtDesc(owner.workspaceId())).isEmpty();
    }
}
