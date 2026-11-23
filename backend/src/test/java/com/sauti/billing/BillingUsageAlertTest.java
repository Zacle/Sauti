package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.notification.WorkspaceNotificationRepository;
import com.sauti.tenant.TenantRepository;
import com.sauti.testsupport.TestAccounts;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;

/** Owners hear about their call minutes at 80% and 100%, once each per cycle and allowance. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillingUsageAlertTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired AgentRepository agents;
    @Autowired CallRepository calls;
    @Autowired TenantRepository tenants;
    @Autowired BillingUsageAlertService service;
    @Autowired BillingUsageAlertRepository alerts;
    @Autowired WorkspaceNotificationRepository notifications;
    @Autowired TemplateEngine templates;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void aTrialIsWarnedAtEightyPercentThenAtTheLimitOnceEach() throws Exception {
        var owner = accounts.owner("Alert Clinic", "alert-" + System.nanoTime() + "@example.com", "KE");
        var tenantId = owner.workspaceId();
        var agentId = agent(owner);

        call(agentId, 47 * 60);
        assertThat(service.check(tenantId)).isEmpty();
        assertThat(service.recentlyActiveWorkspaces()).contains(tenantId);

        call(agentId, 60); // 48 of 60 minutes: 80%
        var warned = service.check(tenantId);
        assertThat(warned).singleElement().satisfies(alert -> {
            assertThat(alert.getThresholdPercent()).isEqualTo(80);
            assertThat(alert.getStatus()).isEqualTo("pending");
            assertThat(alert.isTrial()).isTrue();
            assertThat(alert.getRecipientEmail()).isEqualTo(owner.email());
        });
        // Checking again, as every scan does, doesn't alert again.
        assertThat(service.check(tenantId)).isEmpty();

        call(agentId, 12 * 60);
        assertThat(service.check(tenantId)).singleElement()
                .satisfies(alert -> assertThat(alert.getThresholdPercent()).isEqualTo(100));
        assertThat(service.check(tenantId)).isEmpty();

        var bell = notifications.findTop50ByTenant_IdOrderByCreatedAtDesc(tenantId);
        assertThat(bell).extracting(n -> n.getType())
                .containsExactlyInAnyOrder("billing.minutes_warning", "billing.minutes_used_up");
        assertThat(bell).allSatisfy(n -> assertThat(n.getHref()).isEqualTo("/billing"));
    }

    @Test
    void jumpingPastBothThresholdsSendsOnlyTheLimitAlert() throws Exception {
        var owner = accounts.owner("Jump Clinic", "jump-" + System.nanoTime() + "@example.com", "KE");
        var tenantId = owner.workspaceId();
        call(agent(owner), 61 * 60);

        assertThat(service.check(tenantId)).singleElement()
                .satisfies(alert -> assertThat(alert.getThresholdPercent()).isEqualTo(100));
        assertThat(alerts.findAllByTenantIdAndKindOrderByThresholdPercent(tenantId, BillingUsageAlert.MINUTES))
                .extracting(BillingUsageAlert::getStatus).containsExactly("skipped", "pending");
        assertThat(notifications.findTop50ByTenant_IdOrderByCreatedAtDesc(tenantId)).hasSize(1);
    }

    @Test
    void extraMinutesStartAFreshAllowanceThatCanAlertAgain() throws Exception {
        var owner = accounts.owner("Grant Clinic", "grant-" + System.nanoTime() + "@example.com", "KE");
        var tenantId = owner.workspaceId();
        var agentId = agent(owner);
        call(agentId, 60 * 60);
        assertThat(service.check(tenantId)).hasSize(1);

        var tenant = tenants.findById(tenantId).orElseThrow();
        tenant.setExtraMinutes(40); // 60 of 100
        tenants.saveAndFlush(tenant);
        assertThat(service.check(tenantId)).isEmpty();

        call(agentId, 20 * 60); // 80 of 100
        assertThat(service.check(tenantId)).singleElement().satisfies(alert -> {
            assertThat(alert.getThresholdPercent()).isEqualTo(80);
            assertThat(alert.getIncludedAmount()).isEqualTo(100);
        });
    }

    @Test
    void theEmailSaysWhatHappensAndLinksToPlans() throws Exception {
        var owner = accounts.owner("Mail Clinic", "mail-" + System.nanoTime() + "@example.com", "KE");
        var tenantId = owner.workspaceId();
        call(agent(owner), 50 * 60);
        var alert = service.check(tenantId).get(0);

        var mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        new BillingUsageAlertEmailService(mailSender, templates, "noreply@sauti.test", "Sauti",
                "support@sauti.test", "https://sauti.test/").send(alert);

        var sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        var message = sent.getValue();
        assertThat(message.getSubject()).isEqualTo("You've used 80% of your Sauti trial minutes");
        message.saveChanges();
        var out = new java.io.ByteArrayOutputStream();
        message.writeTo(out);
        var raw = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(raw).contains(owner.email());
        var html = decoded(message);
        assertThat(html)
                .contains("50 of the 60 trial minutes for Mail Clinic")
                .contains("https://sauti.test/billing?tab=plans")
                .contains("Choose a plan")
                .contains("never charges for extra minutes");
    }

    @Test
    void aPaidLimitEmailNamesTheDayMinutesReturn() {
        var usage = new WorkspaceUsageService.Usage(false, 750, 0, 750,
                OffsetDateTime.parse("2026-11-05T10:00:00Z"), OffsetDateTime.parse("2026-12-05T10:00:00Z"));
        var alert = new BillingUsageAlert(UUID.randomUUID(), BillingUsageAlert.MINUTES, usage, 100, "owner@example.com", "Clinic", false);

        var content = BillingUsageAlertEmailService.content(alert);

        assertThat(content.subject()).isEqualTo("Your Sauti call minutes are used up");
        assertThat(content.body()).contains("paused until 5 December 2026").contains("move to a larger plan");
    }

    @Test
    void theWorkerMarksAnAlertSentOnlyAfterTheEmailGoesOut() {
        var repository = mock(BillingUsageAlertRepository.class);
        var email = mock(BillingUsageAlertEmailService.class);
        var usage = new WorkspaceUsageService.Usage(true, 60, 0, 48, OffsetDateTime.now(), null);
        var alert = new BillingUsageAlert(UUID.randomUUID(), BillingUsageAlert.MINUTES, usage, 80, "owner@example.com", "Clinic", false);
        when(repository.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(any(), any()))
                .thenReturn(java.util.List.of(alert));
        org.mockito.Mockito.doThrow(new IllegalStateException("SMTP down")).when(email).send(alert);

        new BillingUsageAlertWorker(mock(BillingUsageAlertService.class), repository, email).sendDue();

        assertThat(alert.getStatus()).isEqualTo("retrying");
        assertThat(alert.getAttempts()).isEqualTo(1);
    }

    private static String decoded(MimeMessage message) throws Exception {
        var multipart = (jakarta.mail.internet.MimeMultipart) message.getContent();
        return text(multipart);
    }

    private static String text(jakarta.mail.Multipart multipart) throws Exception {
        var result = new StringBuilder();
        for (var i = 0; i < multipart.getCount(); i++) {
            var content = multipart.getBodyPart(i).getContent();
            if (content instanceof jakarta.mail.Multipart nested) result.append(text(nested));
            else result.append(content);
        }
        return result.toString().replace("&#39;", "'");
    }

    private void call(UUID agentId, int seconds) {
        var agent = agents.findById(agentId).orElseThrow();
        var call = calls.saveAndFlush(new Call(agent.getTenant(), agent, "v3:alert-" + UUID.randomUUID(),
                "+254700000000", "inbound"));
        var endedAt = OffsetDateTime.now().minusMinutes(1);
        jdbc.update("update calls set outcome = ?, duration_seconds = ?, started_at = ?, ended_at = ? where id = ?",
                "completed", seconds, endedAt.minusSeconds(seconds), endedAt, call.getId());
        entityManager.clear();
    }

    private UUID agent(TestAccounts.Member owner) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"agent":{"name":"Front desk","greetingMessage":"Hello.",
                                  "systemPrompt":"You answer the phone.","defaultLanguage":"en","supportedLanguages":["en"]}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        // The trial counts calls from workspace creation; open it before the test's calls.
        jdbc.update("update tenants set created_at = ? where id = ?", OffsetDateTime.now().minusDays(2), owner.workspaceId());
        entityManager.clear();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
    }
}
