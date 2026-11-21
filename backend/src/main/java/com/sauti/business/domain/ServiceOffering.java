package com.sauti.business.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** One service a business offers, with an optional typed price and duration. */
@Embeddable
public class ServiceOffering {
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "price_amount", precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "description", length = 300)
    private String description;

    protected ServiceOffering() {
    }

    public ServiceOffering(String name, BigDecimal priceAmount, String currency, Integer durationMinutes,
                           String description) {
        var cleanName = Text.clean(name);
        if (cleanName == null) throw new BusinessProfileException("Every service needs a name");
        if (cleanName.length() > 120) throw new BusinessProfileException("Service names are at most 120 characters");
        var cleanCurrency = Text.clean(currency) == null ? null : currency.trim().toUpperCase(Locale.ROOT);
        if (priceAmount != null) {
            if (priceAmount.signum() < 0) throw new BusinessProfileException("A price can't be negative: " + cleanName);
            if (cleanCurrency == null || !CURRENCY.matcher(cleanCurrency).matches()) {
                throw new BusinessProfileException("Give a 3-letter currency, such as KES or EUR, for " + cleanName);
            }
        }
        if (durationMinutes != null && (durationMinutes < 5 || durationMinutes > 480)) {
            throw new BusinessProfileException("Durations are between 5 and 480 minutes: " + cleanName);
        }
        this.name = cleanName;
        this.priceAmount = priceAmount;
        this.currency = priceAmount == null ? null : cleanCurrency;
        this.durationMinutes = durationMinutes;
        this.description = Text.limit(Text.clean(description), 300, "Service descriptions");
    }

    /** "Cleaning — KES 3,500 — 30 min — includes polish": one line callers can be told as is. */
    public String spoken() {
        var line = new StringBuilder(name);
        if (priceAmount != null) line.append(" — ").append(currency).append(' ').append(amount(priceAmount));
        if (durationMinutes != null) line.append(" — ").append(durationMinutes).append(" min");
        if (description != null) line.append(" — ").append(description);
        return line.toString();
    }

    private static String amount(BigDecimal value) {
        var format = new DecimalFormat(value.stripTrailingZeros().scale() > 0 ? "#,##0.00" : "#,##0",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(value);
    }

    public String getName() { return name; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public String getCurrency() { return currency; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getDescription() { return description; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ServiceOffering that && Objects.equals(name, that.name)
                && Objects.equals(priceAmount, that.priceAmount) && Objects.equals(currency, that.currency)
                && Objects.equals(durationMinutes, that.durationMinutes) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, priceAmount, currency, durationMinutes, description);
    }
}
