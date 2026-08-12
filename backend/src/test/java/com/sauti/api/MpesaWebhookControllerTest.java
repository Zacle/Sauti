package com.sauti.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.integration.DuringCallIntegrationFulfillment;
import com.sauti.integration.MpesaCallbackTokenService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class MpesaWebhookControllerTest {
    private final DuringCallIntegrationFulfillment fulfillment = mock(DuringCallIntegrationFulfillment.class);
    private final MpesaCallbackTokenService tokens = mock(MpesaCallbackTokenService.class);
    private final MpesaWebhookController controller = new MpesaWebhookController(fulfillment, tokens);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsUnsignedCallbackBeforeChangingPaymentState() throws Exception {
        var connectionId = UUID.randomUUID();
        var payload = objectMapper.readTree("{\"Body\":{}}");

        assertThatThrownBy(() -> controller.callback(connectionId, null, payload))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(fulfillment);
    }

    @Test
    void acceptsCallbackWithConnectionBoundToken() throws Exception {
        var connectionId = UUID.randomUUID();
        var payload = objectMapper.readTree("{\"Body\":{}}");
        when(tokens.isValid(connectionId, "signed")).thenReturn(true);

        controller.callback(connectionId, "signed", payload);

        verify(fulfillment).callback(connectionId, payload);
    }
}
