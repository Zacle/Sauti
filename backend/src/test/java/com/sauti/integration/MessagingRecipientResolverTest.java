package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sauti.agent.Agent;
import com.sauti.call.Call;
import com.sauti.tenant.Tenant;
import org.junit.jupiter.api.Test;

class MessagingRecipientResolverTest {
    private final MessagingRecipientResolver resolver = new MessagingRecipientResolver();

    @Test
    void reusesProviderCallingNumberForRealCalls() {
        var recipient = resolver.resolve(call("GB", "+447911123456", "inbound"), null);

        assertThat(recipient.e164()).isEqualTo("+447911123456");
        assertThat(recipient.masked()).endsWith("3456");
        assertThat(recipient.source()).isEqualTo("calling_number");
    }

    @Test
    void normalizesBrowserNumberUsingBusinessCountry() {
        var recipient = resolver.resolve(call("EG", "Web visitor", "web"), "01012345678");

        assertThat(recipient.e164()).isEqualTo("+201012345678");
        assertThat(recipient.source()).isEqualTo("provided_number");
    }

    @Test
    void browserCallWithoutAUsableDestinationExplainsWhatToCollect() {
        assertThatThrownBy(() -> resolver.resolve(call("GB", "Web visitor", "web"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete destination number");
    }

    private Call call(String country, String callerNumber, String direction) {
        var tenant = new Tenant("Studio", "owner@example.com", country);
        var agent = new Agent(tenant, "Amina", "Hello", "Prompt");
        return new Call(tenant, agent, "call-" + direction, callerNumber, direction);
    }
}
