package com.sauti.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SignalWireSignatureValidator {
    private final WebhookSignatureValidator delegate;

    public SignalWireSignatureValidator(
            @Value("${sauti.signalwire.auth-token:}") String authToken,
            @Value("${sauti.signalwire.validate-signature:true}") boolean validationEnabled,
            @Value("${sauti.signalwire.public-base-url:${sauti.twilio.public-base-url}}") String publicBaseUrl
    ) {
        this.delegate = new WebhookSignatureValidator(authToken, validationEnabled, publicBaseUrl, "SignalWire");
    }

    public boolean isValid(HttpServletRequest request, String signature, Map<String, String> form) {
        return delegate.isValid(request, signature, form);
    }
}
