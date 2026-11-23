package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.notification.WorkspaceNotificationRepository;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import com.sauti.tenant.Tenant;
import com.sauti.testsupport.TestAccounts;
import com.sauti.integration.MessagingRecipientResolver;
import com.sauti.tool.SautiSmsFulfillment;
import java.time.OffsetDateTime;
import java.util.Map;
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
 * Outbound SMS and WhatsApp messages share an allowance per cycle. At the limit Sauti stops sending on
 * its own; calls, and staff replies from the inbox, carry on.
 */
@SpringBootTest(properties = "sauti.admin.emails=messages-admin@sauti.test")
@AutoConfigureMockMvc
@Transactional
class MessageAllowanceTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired CommunicationUsageMeteringService metering;
    @Autowired BillingAccessPolicy access;
    @Autowired WorkspaceUsageService usage;
    @Autowired BillingUsageAlertService alerts;
    @Autowired WorkspaceNotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void aTrialStopsAutomatedMessagesAtFiftyButKeepsCalling() throws Exception {
        var owner = accounts.owner("Text Clinic", "text-" + System.nanoTime() + "@example.com", "KE");
        var tenantId = owner.workspaceId();
        var agentId = agent(owner);

        send(tenantId, agentId, "sms", 30);
        send(tenantId, agentId, "whatsapp", 19);
        mvc.perform(get(owner.path("/billing/usage")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagesIncluded").value(50))
                .andExpect(jsonPath("$.messagesUsed").value(49))
                .andExpect(jsonPath("$.messagesRemaining").value(1))
                .andExpect(jsonPath("$.messagesBlocked").value(false));
        assertThat(access.automatedMessages(tenantId).allowed()).isTrue();
        assertThat(alerts.check(tenantId)).singleElement().satisfies(alert -> {
            assertThat(alert.getKind()).isEqualTo(BillingUsageAlert.MESSAGES);
            assertThat(alert.getThresholdPercent()).isEqualTo(80);
        });

        send(tenantId, agentId, "whatsapp", 1);

        var blocked = access.automatedMessages(tenantId);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.blockedReason()).isEqualTo("messages_over_limit");
        assertThat(access.automatedMessagesBlockedReason(tenantId)).contains("used its 50 trial messages");
        // Calls have their own allowance.
        assertThat(access.decision(tenantId).allowed()).isTrue();
        mvc.perform(get(owner.path("/billing/usage")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.messagesBlocked").value(true))
                .andExpect(jsonPath("$.messagesBlockedReason").value("messages_over_limit"))
                .andExpect(jsonPath("$.accessBlocked").value(false));
        assertThat(alerts.check(tenantId)).singleElement()
                .satisfies(alert -> assertThat(alert.getThresholdPercent()).isEqualTo(100));
        assertThat(notifications.findTop50ByTenant_IdOrderByCreatedAtDesc(tenantId))
                .extracting(n -> n.getType())
                .contains("billing.messages_warning", "billing.messages_used_up");

        // A platform admin grants a pilot more messages; minutes stay as they were.
        var admin = accounts.owner("Platform", "messages-admin@sauti.test", "KE");
        mvc.perform(patch("/api/v1/admin/workspaces/" + tenantId + "/extra-allowance")
                        .header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":200,\"reason\":\"Pilot sends booking texts\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagesUsed").value(50))
                .andExpect(jsonPath("$.messagesLimit").value(250))
                .andExpect(jsonPath("$.extraMessages").value(200))
                .andExpect(jsonPath("$.extraMinutes").value(0));
        assertThat(access.automatedMessages(tenantId).allowed()).isTrue();
    }

    @Test
    void paidPlansGetTheirOwnMessageAllowance() {
        assertThat(WorkspaceUsageService.planMessages("trial", 60)).isEqualTo(50);
        assertThat(WorkspaceUsageService.planMessages("launch", 100)).isEqualTo(250);
        assertThat(WorkspaceUsageService.planMessages("growth", 750)).isEqualTo(1_000);
        assertThat(WorkspaceUsageService.planMessages("scale", 2_500)).isEqualTo(5_000);
        assertThat(WorkspaceUsageService.planMessages("pilot", 750)).isEqualTo(1_000);
    }

    @Test
    void theSmsToolTellsTheAgentWhenMessagesArePaused() {
        var policy = mock(BillingAccessPolicy.class);
        when(policy.automatedMessagesBlockedReason(any()))
                .thenReturn("This workspace has used its 50 trial messages.");
        var recipients = mock(MessagingRecipientResolver.class);
        var meteringMock = mock(CommunicationUsageMeteringService.class);
        var sms = new SautiSmsFulfillment(new ObjectMapper(), recipients, meteringMock,
                mock(PilotProvisioningPolicyService.class), policy, "telnyx-key", "", "https://sauti.test");
        var tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(UUID.randomUUID());
        var call = mock(Call.class);
        when(call.getTenant()).thenReturn(tenant);

        var result = sms.execute(call, null, new LlmToolCall("tool-1", "send_sms",
                Map.of("message", "Your booking is confirmed")));

        assertThat(result.success()).isTrue();
        assertThat(result.result()).containsEntry("sent", false).containsEntry("reason", "messages_paused");
        verifyNoInteractions(recipients, meteringMock);
    }

    private void send(UUID tenantId, UUID agentId, String channel, int count) {
        for (var i = 0; i < count; i++) {
            metering.meterOutboundMessage(tenantId, agentId, channel, channel + "-" + UUID.randomUUID(),
                    "fallback-" + UUID.randomUUID(), "text");
        }
    }

    private UUID agent(TestAccounts.Member owner) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"agent":{"name":"Front desk","greetingMessage":"Hello.",
                                  "systemPrompt":"You answer the phone.","defaultLanguage":"en","supportedLanguages":["en"]}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        jdbc.update("update tenants set created_at = ? where id = ?", OffsetDateTime.now().minusDays(2), owner.workspaceId());
        entityManager.clear();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
    }
}
