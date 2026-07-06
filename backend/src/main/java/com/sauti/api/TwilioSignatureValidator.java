package com.sauti.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TwilioSignatureValidator {
    private final WebhookSignatureValidator delegate;

    public TwilioSignatureValidator(
            @Value("${sauti.twilio.auth-token}") String authToken,
            @Value("${sauti.twilio.validate-signature:true}") boolean validationEnabled,
            @Value("${sauti.twilio.public-base-url}") String publicBaseUrl
    ) {
        this.delegate = new WebhookSignatureValidator(authToken, validationEnabled, publicBaseUrl, "Twilio");
    }

    public boolean isValid(HttpServletRequest request, String signature, Map<String, String> form) {
        return delegate.isValid(request, signature, form);
    }
}
