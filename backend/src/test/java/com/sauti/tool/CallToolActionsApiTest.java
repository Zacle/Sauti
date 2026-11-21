package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.llm.LlmToolCall;
import com.sauti.testsupport.TestAccounts;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tools that change something need confirmation and run at most once per provider tool call, and
 * every attempt is recorded for the owner. Not transactional: the router writes its records as it
 * would during a real call.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CallToolActionsApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired ToolFulfillmentRouter router;
    @Autowired AgentRepository agents;
    @Autowired CallRepository calls;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void aToolThatChangesSomethingCannotBeSavedWithoutConfirmation() throws Exception {
        var owner = accounts.owner("Policy Clinic", "policy-" + System.nanoTime() + "@example.com", "KE");
        var agentId = agent(owner);

        createTool(owner, agentId, "record_visit", "noop", "data_write", "none").andExpect(status().isBadRequest());
        createTool(owner, agentId, "record_visit", "noop", "data_write", "verified_review").andExpect(status().isBadRequest());
        createTool(owner, agentId, "text_customer", "sauti_sms", "read_only", "none").andExpect(status().isBadRequest());
        createTool(owner, agentId, "record_visit", "noop", "data_write", "explicit").andExpect(status().isOk());
    }

    @Test
    void aRepeatedProviderDeliveryRunsOnceAndEveryAttemptIsRecorded() throws Exception {
        var owner = accounts.owner("Actions Clinic", "actions-" + System.nanoTime() + "@example.com", "KE");
        var agentId = agent(owner);
        createTool(owner, agentId, "record_visit", "noop", "data_write", "explicit").andExpect(status().isOk());
        var call = call(agentId);

        // Proposed: retained and read back, nothing changes yet.
        var proposal = router.route(call, new LlmToolCall("telnyx:webhook:proposal", "record_visit",
                Map.of("question_handling", "ready_for_action", "confirmation_state", "not_confirmed")));
        assertThat(proposal.result()).containsEntry("actionPerformed", false);

        // Confirmed on a later turn, then delivered twice by the provider.
        var confirmed = ToolActionPolicy.managedConfirmedCall(
                new LlmToolCall("telnyx:webhook:approved", "record_visit", Map.of()), Map.of());
        var first = router.route(call, confirmed);
        var second = router.route(call, confirmed);
        assertThat(first.result()).containsEntry("noop", true);
        assertThat(second.result()).containsEntry("status", "already_completed").doesNotContainKey("noop");

        // Sauti's own model loop may reuse an id across turns; those are separate actions.
        router.route(call, ToolActionPolicy.managedConfirmedCall(new LlmToolCall("call_0", "record_visit", Map.of()), Map.of()));
        router.route(call, ToolActionPolicy.managedConfirmedCall(new LlmToolCall("call_0", "record_visit", Map.of()), Map.of()));

        mvc.perform(get(owner.path("/calls/" + call.getId() + "/actions")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].status").value("awaiting_confirmation"))
                .andExpect(jsonPath("$[1].status").value("performed"))
                .andExpect(jsonPath("$[1].summary").value("Ran record_visit"))
                .andExpect(jsonPath("$[1].effect").value("data_write"))
                .andExpect(jsonPath("$[3].status").value("performed"));
    }

    @Test
    void anotherWorkspaceCannotReadACallsActions() throws Exception {
        var owner = accounts.owner("Private Clinic", "private-" + System.nanoTime() + "@example.com", "KE");
        var other = accounts.owner("Other Clinic", "other-" + System.nanoTime() + "@example.com", "KE");
        var call = call(agent(owner));

        mvc.perform(get(other.path("/calls/" + call.getId() + "/actions")).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    private Call call(UUID agentId) {
        return new TransactionTemplate(transactions).execute(status -> {
            var agent = agents.findById(agentId).orElseThrow();
            return calls.save(new Call(agent.getTenant(), agent, "v3:actions-" + UUID.randomUUID(),
                    "+254700000000", "inbound"));
        });
    }

    private UUID agent(TestAccounts.Member owner) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"agent":{"name":"Front desk","greetingMessage":"Hello.",
                                  "systemPrompt":"You answer the phone.","defaultLanguage":"en","supportedLanguages":["en"]}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions createTool(
            TestAccounts.Member owner, UUID agentId, String name, String fulfillment, String effect, String confirmation
    ) throws Exception {
        var body = json.writeValueAsString(Map.of(
                "toolName", name,
                "toolDescription", "Test tool",
                "parametersSchema", Map.of("type", "object", "properties", Map.of()),
                "fulfillmentType", fulfillment,
                "actionEffect", effect,
                "confirmationPolicy", confirmation,
                "active", true,
                "displayOrder", 90
        ));
        return mvc.perform(post(owner.path("/agents/" + agentId + "/tools")).header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
