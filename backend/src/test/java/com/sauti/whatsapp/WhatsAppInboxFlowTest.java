package com.sauti.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.call.Call;
import com.sauti.testsupport.TestAccounts;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The WhatsApp inbox against the real schema: staff see the reply window and what is waiting on them,
 * a retried message is not answered twice, quiet conversations are found, and unfinished messages are
 * picked up again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WhatsAppInboxFlowTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired WhatsAppInboxService inbox;
    @Autowired WhatsAppChannelService channel;
    @Autowired WhatsAppMessageRepository messages;
    @Autowired WhatsAppConversationRepository conversations;
    @Autowired WhatsAppInboundMessageRepository inboundMessages;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void staffSeeTheReplyWindowAndWhatIsWaitingOnThem() throws Exception {
        var owner = accounts.owner("Chat Clinic", "chat-" + System.nanoTime() + "@example.com", "KE");
        var call = whatsAppCall(owner);

        var messageId = "wamid-a-" + System.nanoTime();
        var recorded = inbox.recordInbound(call, messageId, "Amina", "text", "Hello", null, null);
        mvc.perform(get(owner.path("/whatsapp/conversations")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastCustomerMessageAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].replyWindowEndsAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].needsAttention").value(false));

        inbox.needsAttention(messageId, WhatsAppConversation.AGENT_FAILED);

        mvc.perform(get(owner.path("/whatsapp/conversations")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items[0].needsAttention").value(true))
                .andExpect(jsonPath("$.items[0].attentionReason").value("agent_failed"));
        mvc.perform(get(owner.path("/whatsapp/conversations/" + recorded.conversationId() + "/messages"))
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[0].author").value("customer"));
    }

    @Test
    void aRetriedMessageThatWasAnsweredIsNotAnsweredAgain() throws Exception {
        var owner = accounts.owner("Retry Clinic", "retry-" + System.nanoTime() + "@example.com", "KE");
        var call = whatsAppCall(owner);
        var providerId = "wamid-b-" + System.nanoTime();
        var recorded = inbox.recordInbound(call, providerId, "", "text", "Hello", null, null);
        entityManager.flush();
        jdbc.update("update whatsapp_messages set created_at = ? where provider_message_id = ?",
                OffsetDateTime.now().minusMinutes(1), providerId);
        entityManager.clear();

        assertThat(inbox.alreadyAnswered(providerId)).isFalse();

        var conversation = conversations.findById(recorded.conversationId()).orElseThrow();
        var failed = WhatsAppMessage.outbound(conversation, "ai", "text", "Hi", null);
        failed.status("failed", "Meta unavailable");
        messages.save(failed);
        entityManager.flush();
        assertThat(inbox.alreadyAnswered(providerId)).isFalse();

        messages.save(WhatsAppMessage.outbound(conversation, "ai", "text", "Hi again", null));
        entityManager.flush();
        assertThat(inbox.alreadyAnswered(providerId)).isTrue();
    }

    @Test
    void aQuietConversationIsFoundOnceAndClosesTheReplyWindow() throws Exception {
        var owner = accounts.owner("Quiet Clinic", "quiet-" + System.nanoTime() + "@example.com", "KE");
        var call = whatsAppCall(owner);
        var recorded = inbox.recordInbound(call, "wamid-c-" + System.nanoTime(), "", "text", "Hello", null, null);
        var cutoff = OffsetDateTime.now().minusHours(24);
        assertThat(inbox.quietOpenSessions(cutoff, 50))
                .noneMatch(item -> item.conversationId().equals(recorded.conversationId()));

        entityManager.flush();
        jdbc.update("update whatsapp_conversations set last_customer_message_at = ? where id = ?",
                OffsetDateTime.now().minusHours(25), recorded.conversationId());
        entityManager.clear();

        assertThat(inbox.quietOpenSessions(cutoff, 50))
                .anyMatch(item -> item.conversationId().equals(recorded.conversationId()));
        // The reply window has closed too, so a person cannot send a free reply.
        inbox.assign(owner.workspaceId(), recorded.conversationId(), "human");
        assertThatThrownBy(() -> inbox.sendHuman(owner.workspaceId(), recorded.conversationId(), "Hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24 hours");

        inbox.sessionEnded(recorded.conversationId());
        assertThat(inbox.quietOpenSessions(cutoff, 50))
                .noneMatch(item -> item.conversationId().equals(recorded.conversationId()));
    }

    @Test
    void unfinishedMessagesArePickedUpUntilTheirTriesRunOut() {
        var providerId = "wamid-d-" + System.nanoTime();
        var inbound = new WhatsAppInboundMessage(providerId, "phone-x", "254700000001", "text",
                "", "Hello", "", "");
        inbound.markProcessing();
        inboundMessages.save(inbound);
        entityManager.flush();
        jdbc.update("update whatsapp_inbound_messages set updated_at = ? where provider_message_id = ?",
                OffsetDateTime.now().minusMinutes(10), providerId);
        entityManager.clear();

        var now = OffsetDateTime.now();
        assertThat(inboundMessages.findRetryable(now.minusMinutes(5), now.minusMinutes(1),
                WhatsAppInboundMessage.MAX_ATTEMPTS, PageRequest.of(0, 50)))
                .anyMatch(item -> item.getProviderMessageId().equals(providerId));

        jdbc.update("update whatsapp_inbound_messages set attempts = 3 where provider_message_id = ?", providerId);
        entityManager.clear();
        assertThat(inboundMessages.findRetryable(now.minusMinutes(5), now.minusMinutes(1),
                WhatsAppInboundMessage.MAX_ATTEMPTS, PageRequest.of(0, 50)))
                .noneMatch(item -> item.getProviderMessageId().equals(providerId));
    }

    @Test
    void theInboxPagesSearchesAndKeepsClosedConversationsApart() throws Exception {
        var owner = accounts.owner("Paging Clinic", "paging-" + System.nanoTime() + "@example.com", "KE");
        var agent = whatsAppAgent(owner);
        var ids = new java.util.ArrayList<UUID>();
        for (var index = 1; index <= 3; index++) {
            var call = new Call(agent.getTenant(), agent, "WA-" + UUID.randomUUID(), "25471100000" + index, "whatsapp");
            entityManager.persist(call);
            ids.add(inbox.recordInbound(call, "wamid-p" + index + "-" + System.nanoTime(), "Customer " + index,
                    "text", "Message " + index, null, null).conversationId());
        }
        entityManager.flush();
        for (var index = 0; index < 3; index++) {
            jdbc.update("update whatsapp_conversations set last_message_at = ? where id = ?",
                    OffsetDateTime.now().minusMinutes(10 - index), ids.get(index));
        }
        entityManager.clear();

        var first = json.readTree(mvc.perform(get(owner.path("/whatsapp/conversations")).param("limit", "2")
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].customerNumber").value("254711000003"))
                .andExpect(jsonPath("$.items[1].customerNumber").value("254711000002"))
                .andReturn().getResponse().getContentAsString());
        var cursor = first.path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        mvc.perform(get(owner.path("/whatsapp/conversations")).param("limit", "2").param("cursor", cursor)
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].customerNumber").value("254711000001"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        // A number matches however it is typed, and so does part of a name.
        mvc.perform(get(owner.path("/whatsapp/conversations")).param("q", "+254 711 000 002")
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].customerNumber").value("254711000002"));
        mvc.perform(get(owner.path("/whatsapp/conversations")).param("q", "customer 3")
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1));

        // Closing ends its call record now and moves it to the closed view.
        mvc.perform(post(owner.path("/whatsapp/conversations/" + ids.get(0) + "/close"))
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));
        entityManager.flush();
        entityManager.clear();
        assertThat(jdbc.queryForObject("select outcome from calls where agent_id = ? and caller_number = ?",
                String.class, agent.getId(), "254711000001")).isEqualTo("conversation_closed");
        assertThat(conversations.findById(ids.get(0)).orElseThrow().isSessionOpen()).isFalse();
        mvc.perform(get(owner.path("/whatsapp/conversations")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get(owner.path("/whatsapp/conversations")).param("view", "closed")
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ids.get(0).toString()));

        // Staff can reopen it, and a customer writing again reopens it too.
        mvc.perform(post(owner.path("/whatsapp/conversations/" + ids.get(0) + "/reopen"))
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.status").value("open"));
        inbox.close(owner.workspaceId(), ids.get(0));
        var again = new Call(agent.getTenant(), agent, "WA-" + UUID.randomUUID(), "254711000001", "whatsapp");
        entityManager.persist(again);
        inbox.recordInbound(again, "wamid-p4-" + System.nanoTime(), "", "text", "Me again", null, null);
        assertThat(conversations.findById(ids.get(0)).orElseThrow().getStatus()).isEqualTo("open");
    }

    @Test
    void theInboxCanAskOnlyForWhatChanged() throws Exception {
        var owner = accounts.owner("Changes Clinic", "changes-" + System.nanoTime() + "@example.com", "KE");
        var call = whatsAppCall(owner);
        var recorded = inbox.recordInbound(call, "wamid-e-" + System.nanoTime(), "", "text", "Hello", null, null);
        entityManager.flush();

        var before = OffsetDateTime.now().minusMinutes(1).toString();
        var later = OffsetDateTime.now().plusHours(1).toString();
        mvc.perform(get(owner.path("/whatsapp/conversations/changes")).param("since", before)
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(recorded.conversationId().toString()))
                .andExpect(jsonPath("$.next").isNotEmpty());
        mvc.perform(get(owner.path("/whatsapp/conversations/changes")).param("since", later)
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get(owner.path("/whatsapp/conversations/" + recorded.conversationId() + "/messages"))
                        .param("since", before).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(owner.path("/whatsapp/conversations/" + recorded.conversationId() + "/messages"))
                        .param("since", later).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aQuietWeekClosesAConversationUnlessItIsWaitingOnTheTeam() throws Exception {
        var owner = accounts.owner("Week Clinic", "week-" + System.nanoTime() + "@example.com", "KE");
        var agent = whatsAppAgent(owner);
        var quietCall = new Call(agent.getTenant(), agent, "WA-" + UUID.randomUUID(), "254711000011", "whatsapp");
        var waitingCall = new Call(agent.getTenant(), agent, "WA-" + UUID.randomUUID(), "254711000012", "whatsapp");
        entityManager.persist(quietCall);
        entityManager.persist(waitingCall);
        var quiet = inbox.recordInbound(quietCall, "wamid-w1-" + System.nanoTime(), "", "text", "Hi", null, null);
        var waitingId = "wamid-w2-" + System.nanoTime();
        var waiting = inbox.recordInbound(waitingCall, waitingId, "", "text", "Hi", null, null);
        inbox.needsAttention(waitingId, WhatsAppConversation.WAITING_FOR_TEAM);
        entityManager.flush();
        jdbc.update("update whatsapp_conversations set last_message_at = ? where id in (?, ?)",
                OffsetDateTime.now().minusDays(8), quiet.conversationId(), waiting.conversationId());
        entityManager.clear();

        var closed = inbox.inactiveOpen(OffsetDateTime.now().minusDays(7), 50).stream()
                .filter(item -> item.tenantId().equals(owner.workspaceId())).toList();
        assertThat(closed).extracting(WhatsAppInboxService.InactiveConversation::conversationId)
                .containsExactly(quiet.conversationId());
        closed.forEach(item -> channel.close(item.tenantId(), item.conversationId(), WhatsAppConversation.CLOSED_INACTIVE));
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(owner.path("/whatsapp/conversations")).param("view", "closed")
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].closedReason").value("inactive"));
        assertThat(conversations.findById(waiting.conversationId()).orElseThrow().getStatus()).isEqualTo("open");
    }

    @Test
    void teamRepliesShowTheNameThePersonChose() throws Exception {
        var owner = accounts.owner("Name Clinic", "name-" + System.nanoTime() + "@example.com", "KE");
        var call = whatsAppCall(owner);
        var recorded = inbox.recordInbound(call, "wamid-n-" + System.nanoTime(), "", "text", "Hi", null, null);
        var conversation = conversations.findById(recorded.conversationId()).orElseThrow();
        messages.save(WhatsAppMessage.staff(conversation, "text", "Hello",
                new WhatsAppMessage.StaffSender(owner.userId(), owner.email()), null));
        entityManager.flush();
        var thread = owner.path("/whatsapp/conversations/" + recorded.conversationId() + "/messages");

        mvc.perform(get(thread).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[1].sentBy").value(owner.email()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/me/display-name")
                        .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"  Ana   Wanjiku \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Wanjiku"));
        mvc.perform(get(thread).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[1].sentBy").value("Ana Wanjiku"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/me/display-name")
                        .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + "x".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private Agent whatsAppAgent(TestAccounts.Member owner) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"agent":{"name":"Front desk","greetingMessage":"Hello.",
                                  "systemPrompt":"You answer messages.","defaultLanguage":"en","supportedLanguages":["en"]}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        var agentId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
        var agent = entityManager.find(Agent.class, agentId);
        agent.configureWhatsApp(true, "pn-" + System.nanoTime());
        entityManager.flush();
        return agent;
    }

    private Call whatsAppCall(TestAccounts.Member owner) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"agent":{"name":"Front desk","greetingMessage":"Hello.",
                                  "systemPrompt":"You answer messages.","defaultLanguage":"en","supportedLanguages":["en"]}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        var agentId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
        var agent = entityManager.find(Agent.class, agentId);
        agent.configureWhatsApp(true, "pn-" + System.nanoTime());
        entityManager.flush();
        return new Call(agent.getTenant(), agent, "WA-" + UUID.randomUUID(), "254711000000", "whatsapp");
    }
}
