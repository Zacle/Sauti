package com.sauti.call;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.calendar.Booking;
import com.sauti.calendar.BookingRepository;
import com.sauti.testsupport.TestAccounts;
import java.time.OffsetDateTime;
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

/** The owner finds calls by what was said, what was booked and when, one page at a time. */
@SpringBootTest
@AutoConfigureMockMvc
class CallSearchApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired AgentRepository agents;
    @Autowired CallRepository calls;
    @Autowired BookingRepository bookings;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void findsCallsByWhatWasSaidBookedOrSummarised() throws Exception {
        var owner = accounts.owner("Search Clinic", "search-" + System.nanoTime() + "@example.com", "KE");
        var agentId = agent(owner);
        var cleaning = call(agentId, "inbound", "completed", "Caller: I need a cleaning on Monday.\nAgent: Booked.\n",
                null, "book_cleaning", hoursAgo(1));
        call(agentId, "inbound", "faq_answered", "Caller: Are you open on Sunday?\nAgent: We are closed.\n",
                "Asked about weekend opening hours.", "opening_hours", hoursAgo(2));
        call(agentId, "test", "completed", "Caller: Testing 100% of the flow.\n", null, null, hoursAgo(3));
        call(agentId, "inbound", "no_answer", "", null, null, hoursAgo(50));
        booking(cleaning, "Wanjiru Mwangi");

        search(owner, "q=sunday").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].intent").value("opening_hours"))
                .andExpect(jsonPath("$.items[0].matchedText").value("Caller: Are you open on Sunday?"))
                .andExpect(jsonPath("$.items[0].transcript").doesNotExist());
        search(owner, "q=weekend").andExpect(jsonPath("$.total").value(1));
        search(owner, "q=wanjiru").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].booking.serviceType").value("Dental cleaning"))
                .andExpect(jsonPath("$.items[0].booking.callerName").value("Wanjiru Mwangi"));
        search(owner, "q=book cleaning").andExpect(jsonPath("$.total").value(1));
        // A wildcard in the text is matched literally.
        mvc.perform(get(owner.path("/calls/search")).param("q", "100%").header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        // Only the two snake_case intents contain an underscore; unescaped, "_" would match every call.
        search(owner, "q=_").andExpect(jsonPath("$.total").value(2));

        search(owner, "type=phone").andExpect(jsonPath("$.total").value(3));
        search(owner, "type=test").andExpect(jsonPath("$.total").value(1));
        search(owner, "status=missed").andExpect(jsonPath("$.total").value(1));
        search(owner, "from=" + hoursAgo(24)).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.answered").value(3))
                .andExpect(jsonPath("$.bookings").value(1));

        search(owner, "size=2&page=1").andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].outcome").value("no_answer"));

        mvc.perform(get(owner.path("/calls/" + cleaning.getId())).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.callerName").value("Wanjiru Mwangi"));
    }

    @Test
    void neverReturnsAnotherWorkspacesCalls() throws Exception {
        var owner = accounts.owner("Own Clinic", "own-" + System.nanoTime() + "@example.com", "KE");
        var other = accounts.owner("Other Clinic", "other-" + System.nanoTime() + "@example.com", "KE");
        call(agent(other), "inbound", "completed", "Caller: Private matter.\n", null, null, hoursAgo(1));
        agent(owner);

        search(owner, "q=private").andExpect(jsonPath("$.total").value(0));
        search(owner, "").andExpect(jsonPath("$.total").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions search(TestAccounts.Member owner, String query) throws Exception {
        var request = get(owner.path("/calls/search")).header("Authorization", owner.bearer());
        for (var pair : query.split("&")) {
            if (pair.isBlank()) continue;
            var split = pair.indexOf('=');
            request.param(pair.substring(0, split), pair.substring(split + 1));
        }
        return mvc.perform(request).andExpect(status().isOk());
    }

    private Call call(UUID agentId, String direction, String outcome, String transcript, String summary,
                      String intent, OffsetDateTime startedAt) {
        return new TransactionTemplate(transactions).execute(status -> {
            var agent = agents.findById(agentId).orElseThrow();
            var call = new Call(agent.getTenant(), agent, "v3:search-" + UUID.randomUUID(), "+254700000000", direction);
            call.replaceTranscript(transcript);
            call.complete(outcome, OffsetDateTime.now().plusSeconds(90));
            call.applyAnalysis(summary, null, null, intent);
            var saved = calls.save(call);
            calls.flush();
            jdbc.update("update calls set started_at = ?, ended_at = ? where id = ?",
                    startedAt, startedAt.plusSeconds(90), saved.getId());
            return saved;
        });
    }

    private void booking(Call call, String name) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var saved = calls.findById(call.getId()).orElseThrow();
            bookings.save(new Booking(saved.getTenant(), saved.getAgent(), saved, name, "+254700000000", null,
                    "Dental cleaning", OffsetDateTime.now().plusDays(2), 30, "{}"));
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

    private static OffsetDateTime hoursAgo(int hours) {
        return OffsetDateTime.now().minusHours(hours).withNano(0);
    }
}
