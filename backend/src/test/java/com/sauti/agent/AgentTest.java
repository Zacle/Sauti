package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.sauti.tenant.Tenant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentTest {

    @Test
    void changingNameSynchronizesANameEmbeddedInTheFixedGreeting() {
        var agent = new Agent(
                new Tenant("Tranquil AI", "owner@example.com", "FR"),
                "Amélie",
                "Bonjour, c'est Amélie de Tranquil AI. Comment puis-je vous aider ?",
                "Help callers."
        );

        agent.update(
                "Sarah", null,
                "Bonjour, c'est Amélie de Tranquil AI. Comment puis-je vous aider ?",
                "Help callers.", "fr", List.of("fr"), "cartesia:voice", null,
                List.of(), true, "Europe/Paris", "", "always", 300, true, false
        );

        assertThat(agent.getName()).isEqualTo("Sarah");
        assertThat(agent.getGreetingMessage())
                .isEqualTo("Bonjour, c'est Sarah de Tranquil AI. Comment puis-je vous aider ?");
    }
}
