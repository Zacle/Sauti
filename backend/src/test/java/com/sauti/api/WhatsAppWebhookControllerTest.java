package com.sauti.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.whatsapp.WhatsAppChannelService;
import com.sauti.whatsapp.WhatsAppSignatureValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WhatsAppWebhookControllerTest {
    @Test
    void returnsMetaChallengeForMatchingVerifyToken() {
        var controller = new WhatsAppWebhookController(
                mock(WhatsAppChannelService.class),
                mock(WhatsAppSignatureValidator.class),
                "verify-me"
        );

        assertThat(controller.verify("subscribe", "verify-me", "challenge-123"))
                .isEqualTo("challenge-123");
    }

    @Test
    void rejectsInvalidWebhookSignatureBeforeProcessingPayload() {
        var channelService = mock(WhatsAppChannelService.class);
        var signatureValidator = mock(WhatsAppSignatureValidator.class);
        var controller = new WhatsAppWebhookController(channelService, signatureValidator, "verify-me");
        when(signatureValidator.isValid("{}", "sha256=bad")).thenReturn(false);

        assertThatThrownBy(() -> controller.receive("{}", "sha256=bad"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void acceptsSignedPayloadAndHandsItToChannelService() {
        var channelService = mock(WhatsAppChannelService.class);
        var signatureValidator = mock(WhatsAppSignatureValidator.class);
        var controller = new WhatsAppWebhookController(channelService, signatureValidator, "verify-me");
        when(signatureValidator.isValid("{}", "sha256=valid")).thenReturn(true);

        controller.receive("{}", "sha256=valid");

        verify(channelService).accept("{}");
    }
}
