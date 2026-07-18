package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.sauti.tenant.Tenant;
import org.junit.jupiter.api.Test;

class AgentBusinessIdentityTest {
    @Test
    void infersBusinessFromExplicitAssistantRole() {
        var agent = new Agent(
                new Tenant("Tranquil AI", "owner@example.com", "KE"),
                "Alec",
                "Hello",
                "You are Alec, the virtual assistant for X-Fit. You help people start their fitness journey."
        );

        assertThat(AgentBusinessIdentity.fromPrompt(agent)).isEqualTo("X-Fit");
    }

    @Test
    void infersBusinessFromStructuredInformation() {
        var agent = new Agent(
                new Tenant("Tranquil AI", "owner@example.com", "KE"),
                "Alec",
                "Hello",
                "## Gym Information\n- Name: X-Fit\n- Address: Not provided"
        );

        assertThat(AgentBusinessIdentity.fromPrompt(agent)).isEqualTo("X-Fit");
    }

    @Test
    void doesNotTreatWorkspaceAsAgentBusiness() {
        var agent = new Agent(
                new Tenant("Tranquil AI", "owner@example.com", "KE"),
                "Sarah",
                "Bonjour",
                "You are Sarah, a professional AI voice agent."
        );

        assertThat(AgentBusinessIdentity.fromPrompt(agent)).isBlank();
    }
}
