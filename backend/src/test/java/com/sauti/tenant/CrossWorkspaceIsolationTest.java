package com.sauti.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.calendar.Booking;
import com.sauti.calendar.BookingRepository;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.testsupport.TestAccounts;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * One workspace must never read or change another workspace's records, even when it knows their
 * ids. The attacker calls every id-based route inside its own workspace with the victim's ids;
 * nothing may succeed or crash, and the victim's data must be untouched afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrossWorkspaceIsolationTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired TenantRepository tenants;
    @Autowired AgentRepository agents;
    @Autowired CallRepository calls;
    @Autowired BookingRepository bookings;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void idsFromAnotherWorkspaceAreNeverReadableOrChangeable() throws Exception {
        var victim = accounts.owner("Victim Clinic", "victim-owner@example.com", "KE");
        var attacker = accounts.owner("Attacker Clinic", "attacker-owner@example.com", "KE");
        var tenant = tenants.findById(victim.workspaceId()).orElseThrow();
        var agent = agents.save(new Agent(tenant, "Victim agent", "Hello", "Help callers"));
        var call = calls.save(new Call(tenant, agent, "isolation-call-sid", "+254700000001", "inbound"));
        var booking = bookings.save(new Booking(tenant, agent, call, "Wanjiru", "+254700000002", null,
                "Cleaning", OffsetDateTime.now().plusDays(2), 30, "{}"));

        var agentBody = """
                {"name":"Hijacked","greetingMessage":"Hi","systemPrompt":"x","defaultLanguage":"en","supportedLanguages":["en"]}
                """;
        var routes = List.of(
                route(HttpMethod.GET, "/agents/" + agent.getId(), null),
                route(HttpMethod.GET, "/agents/" + agent.getId() + "/readiness", null),
                route(HttpMethod.PUT, "/agents/" + agent.getId(), agentBody),
                route(HttpMethod.PATCH, "/agents/" + agent.getId() + "/timezone", "{\"timezone\":\"UTC\"}"),
                route(HttpMethod.POST, "/agents/" + agent.getId() + "/activate", null),
                route(HttpMethod.POST, "/agents/" + agent.getId() + "/deactivate", null),
                route(HttpMethod.GET, "/agents/" + agent.getId() + "/variables", null),
                route(HttpMethod.GET, "/agents/" + agent.getId() + "/knowledge-documents", null),
                route(HttpMethod.GET, "/agents/" + agent.getId() + "/tools", null),
                route(HttpMethod.DELETE, "/agents/" + agent.getId(), null),
                route(HttpMethod.GET, "/calls/" + call.getId(), null),
                route(HttpMethod.GET, "/calls/" + call.getId() + "/turns", null),
                route(HttpMethod.GET, "/calls/" + call.getId() + "/recording", null),
                route(HttpMethod.GET, "/bookings/" + booking.getId(), null),
                route(HttpMethod.PATCH, "/bookings/" + booking.getId(), "{\"status\":\"cancelled\"}"),
                route(HttpMethod.DELETE, "/bookings/" + booking.getId(), null),
                route(HttpMethod.DELETE, "/bookings/" + booking.getId() + "/permanent", null));

        var leaks = new ArrayList<String>();
        for (var route : routes) {
            var builder = request(route.method(), attacker.path(route.path())).header("Authorization", attacker.bearer());
            if (route.body() != null) builder.contentType(MediaType.APPLICATION_JSON).content(route.body());
            var status = mvc.perform(builder).andReturn().getResponse().getStatus();
            if (status < 400 || status >= 500) leaks.add(route.method() + " " + route.path() + " -> " + status);
        }
        assertThat(leaks).as("routes that answered another workspace's ids").isEmpty();

        var reloadedAgent = agents.findById(agent.getId()).orElseThrow();
        assertThat(reloadedAgent.getName()).isEqualTo("Victim agent");
        assertThat(reloadedAgent.isActive()).isFalse();
        assertThat(bookings.findById(booking.getId())).isPresent();
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(booking.getStatus());
    }

    @Test
    void anotherWorkspacesUrlIsNotFoundEvenForReading() throws Exception {
        var victim = accounts.owner("Hidden Clinic", "hidden-owner@example.com", "KE");
        var attacker = accounts.owner("Curious Clinic", "curious-owner@example.com", "KE");

        for (var path : List.of("/agents", "/calls", "/bookings", "/members", "/activity", "/api-keys", "/export")) {
            var status = mvc.perform(request(HttpMethod.GET, victim.path(path)).header("Authorization", attacker.bearer()))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(path).isEqualTo(404);
        }
    }

    private static Route route(HttpMethod method, String path, String body) {
        return new Route(method, path, body);
    }

    private record Route(HttpMethod method, String path, String body) {
    }
}
