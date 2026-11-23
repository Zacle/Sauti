package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.tenant.TenantRepository;
import com.sauti.testsupport.TestAccounts;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minutes used are counted from the calls of the current cycle; a trial stops at its one-off
 * allowance, a paid plan at its monthly one, and a platform admin can add minutes to either.
 */
@SpringBootTest(properties = "sauti.admin.emails=usage-admin@sauti.test")
@AutoConfigureMockMvc
@Transactional
class WorkspaceUsageTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired AgentRepository agents;
    @Autowired CallRepository calls;
    @Autowired TenantRepository tenants;
    @Autowired BillingSubscriptionRepository subscriptions;
    @Autowired WorkspaceUsageService usage;
    @Autowired BillingAccessPolicy access;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void aTrialCountsConnectedCallsAndStopsAtItsAllowance() throws Exception {
        var owner = accounts.owner("Trial Clinic", "trial-" + System.nanoTime() + "@example.com", "KE");
        var tenantId = owner.workspaceId();
        jdbc.update("update tenants set created_at = ? where id = ?", OffsetDateTime.now().minusDays(40), tenantId);
        entityManager.clear();
        var agentId = agent(owner);
        call(agentId, "inbound", "completed", 30, OffsetDateTime.now().minusDays(35));
        call(agentId, "test", "completed", 61, OffsetDateTime.now().minusHours(2));
        // Never connected, turned away, or a WhatsApp thread: free.
        call(agentId, "inbound", "no_answer", 40, OffsetDateTime.now().minusHours(1));
        call(agentId, "inbound", "unanswered", 0, OffsetDateTime.now().minusHours(1));
        call(agentId, "whatsapp", "completed", 900, OffsetDateTime.now().minusHours(1));

        mvc.perform(get(owner.path("/billing/usage")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trial").value(true))
                .andExpect(jsonPath("$.monthlyMinutesLimit").value(60))
                // Trial minutes never renew: the call from 35 days ago still counts.
                .andExpect(jsonPath("$.minutesUsedThisCycle").value(3))
                .andExpect(jsonPath("$.cycleEndsAt").doesNotExist())
                .andExpect(jsonPath("$.accessBlocked").value(false));

        call(agentId, "inbound", "completed", 57 * 60, OffsetDateTime.now().minusMinutes(5));

        mvc.perform(get(owner.path("/billing/usage")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.minutesUsedThisCycle").value(60))
                .andExpect(jsonPath("$.limitReached").value(true))
                .andExpect(jsonPath("$.accessBlocked").value(true))
                .andExpect(jsonPath("$.blockedReason").value("over_limit"));
        assertThatThrownBy(() -> access.requirePaidCommunication(tenantId))
                .hasMessageContaining("used its 60 trial minutes");

        // A platform admin grants a pilot more minutes, with a reason for the audit log.
        var admin = accounts.owner("Platform", "usage-admin@sauti.test", "KE");
        mvc.perform(patch("/api/v1/admin/workspaces/" + tenantId + "/extra-allowance")
                        .header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":120,\"reason\":\"Pilot extension\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesUsed").value(60))
                .andExpect(jsonPath("$.minutesLimit").value(180))
                .andExpect(jsonPath("$.extraMinutes").value(120));
        mvc.perform(patch("/api/v1/admin/workspaces/" + tenantId + "/extra-allowance")
                        .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":5000,\"reason\":\"Me\"}"))
                .andExpect(status().isForbidden());
        assertThat(access.decision(tenantId).allowed()).isTrue();
    }

    @Test
    void aPaidPlanCountsOnlyTheCurrentCycleOnItsRenewalDay() throws Exception {
        var owner = accounts.owner("Paid Clinic", "paid-" + System.nanoTime() + "@example.com", "KE");
        var tenantId = owner.workspaceId();
        var agentId = agent(owner);
        var renewsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10).withNano(0);
        var tenant = tenants.findById(tenantId).orElseThrow();
        tenant.applyBillingSubscription("growth", 750, renewsAt, "user_1");
        tenants.save(tenant);
        var subscription = new BillingSubscription(tenantId, "whop", "mem_" + UUID.randomUUID());
        subscription.synchronize("user_1", "mem_1", "prod_1", "plan_1", "growth", "monthly", "active", false,
                renewsAt, null, null, OffsetDateTime.now(), "", "", "");
        subscriptions.save(subscription);

        call(agentId, "inbound", "completed", 600, renewsAt.minusMonths(1).minusHours(1)); // last cycle
        call(agentId, "inbound", "completed", 125, renewsAt.minusMonths(1).plusHours(1));  // this cycle: 3

        var current = usage.current(tenantId);
        assertThat(current.trial()).isFalse();
        assertThat(current.usedMinutes()).isEqualTo(3);
        assertThat(current.includedMinutes()).isEqualTo(750);
        assertThat(current.cycleStartsAt()).isEqualTo(renewsAt.minusMonths(1));
        assertThat(current.cycleEndsAt()).isEqualTo(renewsAt);
    }

    @Test
    void cyclesKeepTheRenewalDayAcrossShortMonths() {
        var anchor = OffsetDateTime.parse("2027-01-31T09:00:00Z");

        assertThat(WorkspaceUsageService.cycleStart(anchor, OffsetDateTime.parse("2026-12-05T00:00:00Z")))
                .isEqualTo(OffsetDateTime.parse("2026-11-30T09:00:00Z"));
        assertThat(WorkspaceUsageService.cycleStart(anchor, OffsetDateTime.parse("2027-03-01T00:00:00Z")))
                .isEqualTo(OffsetDateTime.parse("2027-02-28T09:00:00Z"));
        assertThat(WorkspaceUsageService.cycleStart(anchor, OffsetDateTime.parse("2027-01-31T08:59:00Z")))
                .isEqualTo(OffsetDateTime.parse("2026-12-31T09:00:00Z"));
        assertThat(WorkspaceUsageService.cycleStart(null, OffsetDateTime.parse("2026-11-23T15:00:00Z")))
                .isEqualTo(OffsetDateTime.parse("2026-11-01T00:00:00Z"));
    }

    private void call(UUID agentId, String direction, String outcome, int seconds, OffsetDateTime endedAt) {
        var agent = agents.findById(agentId).orElseThrow();
        var call = calls.saveAndFlush(new Call(agent.getTenant(), agent, "v3:usage-" + UUID.randomUUID(),
                "+254700000000", direction));
        jdbc.update("update calls set outcome = ?, duration_seconds = ?, started_at = ?, ended_at = ? where id = ?",
                outcome, seconds, endedAt.minusSeconds(seconds), endedAt, call.getId());
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
}
