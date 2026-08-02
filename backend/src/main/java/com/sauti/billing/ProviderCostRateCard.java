package com.sauti.billing;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProviderCostRateCard {
    private final String currency;
    private final BigDecimal voiceMinute;
    private final BigDecimal smsMessage;
    private final BigDecimal whatsappMessage;

    public ProviderCostRateCard(
            @Value("${sauti.billing.fallback.currency:USD}") String currency,
            @Value("${sauti.billing.fallback.voice-minute:0}") String voiceMinute,
            @Value("${sauti.billing.fallback.sms-message:0}") String smsMessage,
            @Value("${sauti.billing.fallback.whatsapp-message:0}") String whatsappMessage) {
        this.currency = currency == null ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
        if (!this.currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Fallback currency is invalid");
        this.voiceMinute = amount(voiceMinute);
        this.smsMessage = amount(smsMessage);
        this.whatsappMessage = amount(whatsappMessage);
    }

    public Optional<Estimate> estimate(String resourceType, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) return Optional.empty();
        var unitRate = switch (resourceType) {
            case "voice_session" -> voiceMinute;
            case "sms_message" -> smsMessage;
            case "whatsapp_message" -> whatsappMessage;
            default -> BigDecimal.ZERO;
        };
        if (unitRate.signum() <= 0) return Optional.empty();
        return Optional.of(new Estimate(unitRate.multiply(quantity), currency, unitRate));
    }

    private static BigDecimal amount(String value) {
        try {
            var amount = new BigDecimal(value == null || value.isBlank() ? "0" : value.trim());
            if (amount.signum() < 0) throw new IllegalArgumentException("Fallback rate cannot be negative");
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Fallback rate must be a decimal amount", exception);
        }
    }

    public record Estimate(BigDecimal amount, String currency, BigDecimal unitRate) { }
}
