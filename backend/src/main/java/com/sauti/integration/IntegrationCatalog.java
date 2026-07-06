package com.sauti.integration;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IntegrationCatalog {
    private final List<Entry> entries = List.of(
            new Entry("google_calendar", "Google Calendar", "Calendar and scheduling",
                    "Check availability and create bookings during calls.", true, false, true,
                    List.of(), List.of()),
            new Entry("telnyx_sms", "SMS", "Messaging",
                    "Send SMS using Sauti's configured Telnyx account.", true, false, false,
                    List.of(), List.of()),
            new Entry("custom_webhook", "Custom webhook", "Developer tools",
                    "Call your HTTPS endpoint during calls or after analysis.", true, true, true,
                    List.of("webhookUrl", "authType"), List.of("authToken", "apiKey", "hmacSecret")),
            new Entry("whatsapp", "WhatsApp", "Messaging",
                    "Send approved templates during calls and follow up after calls.", true, true, true,
                    List.of("wabaId", "phoneNumberId", "templateName", "templateLanguage"),
                    List.of("accessToken")),
            new Entry("email", "Email alerts", "Notifications",
                    "Email call outcomes using Sauti's SMTP service.", false, true, false,
                    List.of("recipients"), List.of()),
            new Entry("slack", "Slack", "Notifications",
                    "Post call alerts to a Slack incoming webhook.", false, true, true,
                    List.of(), List.of("webhookUrl")),
            new Entry("google_sheets", "Google Sheets", "Data",
                    "Look up rows during calls and append analysed calls.", true, true, true,
                    List.of("spreadsheetId", "range", "lookupColumn", "returnColumns", "appendColumns"),
                    List.of()),
            new Entry("hubspot", "HubSpot", "CRM",
                    "Upsert contacts and attach a call note.", false, true, true,
                    List.of(), List.of()),
            new Entry("salesforce", "Salesforce", "CRM",
                    "Upsert contacts and attach a call note.", false, true, true,
                    List.of(), List.of()),
            new Entry("mpesa", "M-Pesa", "Payments",
                    "Request a caller-confirmed Daraja STK Push during a call.", true, false, true,
                    List.of("shortcode", "environment", "minimumAmount", "maximumAmount"),
                    List.of("consumerKey", "consumerSecret", "passkey"))
    );

    public List<Entry> all() { return entries; }

    public Entry require(String provider) {
        return entries.stream().filter(entry -> entry.provider().equals(provider)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown integration provider"));
    }

    public record Entry(String provider, String name, String category, String description,
                        boolean duringCall, boolean postCall, boolean requiresConnection,
                        List<String> configurationFields, List<String> credentialFields) {
        public Map<String, Boolean> capabilities() {
            return Map.of("duringCall", duringCall, "postCall", postCall);
        }
    }
}
