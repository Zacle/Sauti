package com.sauti.tool;

import com.sauti.agent.Agent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DefaultToolSeeder {
    private final AgentToolRepository agentToolRepository;

    public DefaultToolSeeder(AgentToolRepository agentToolRepository) {
        this.agentToolRepository = agentToolRepository;
    }

    public void seedDefaults(Agent agent) {
        seed(agent, "get_business_hours", "Return the configured operating schedule when the caller asks about opening days or hours in any language.",
                schema(Map.of(), List.of()), "sauti_calendar", "noop_calendar", 5);
        seed(agent, "check_availability", "Always check live calendar availability and business opening hours after the caller gives a date or time, before proposing or confirming a slot.",
                schema(Map.of(
                        "date", property("string", "Preferred date in yyyy-MM-dd format", "date"),
                        "time_preference", property("string", "Exact preferred time in HH:mm when provided, otherwise a period such as morning", ""),
                        "duration_minutes", property("integer", "Appointment duration in minutes", "")
                ), List.of("date")), "sauti_calendar", "noop_calendar", 10);
        seed(agent, "book_slot", "Book only after all required details and availability are complete, then the agent performs one consolidated final readback and the caller confirms it.",
                schema(Map.ofEntries(
                        Map.entry("appointment_at", property("string", "Confirmed ISO-8601 appointment datetime", "date-time")),
                        Map.entry("caller_name", property("string", "Caller full name", "")),
                        Map.entry("caller_phone", property("string", "Caller phone number", "phone")),
                        Map.entry("caller_email", property("string", "Caller email when required by this agent", "email")),
                        Map.entry("caller_name_spelling_confirmed", property("boolean", "True only after all intake is complete and the caller confirms the agent's consolidated final review containing the NATO name readback; never ask the caller to use NATO", "")),
                        Map.entry("caller_phone_digits_confirmed", property("boolean", "True only after all intake is complete and the caller confirms the agent's consolidated final review containing the digit-by-digit phone readback", "")),
                        Map.entry("caller_email_spelling_confirmed", property("boolean", "True only after all intake is complete and the caller confirms the agent's consolidated final review containing the NATO email readback; never ask the caller to use NATO", "")),
                        Map.entry("final_booking_review_confirmed", property("boolean", "True only after every required field and availability are complete, the agent reads the entire booking back once, and the caller confirms the complete final review", "")),
                        Map.entry("service_type", property("string", "Service being booked", "")),
                        Map.entry("duration_minutes", property("integer", "Appointment duration in minutes", "")),
                        Map.entry("customer_details", property("object", "Additional fields required by this agent's booking workflow", ""))
                ), List.of(
                        "appointment_at", "caller_name", "caller_phone", "service_type",
                        "caller_name_spelling_confirmed", "caller_phone_digits_confirmed",
                        "final_booking_review_confirmed"
                )), "sauti_calendar", "noop_calendar", 20);
        seed(agent, "reschedule_booking", "Reschedule a booking only after checking the new time with check_availability and receiving caller confirmation.",
                schema(Map.of(
                        "booking_number", property("string", "Customer-facing Sauti booking number, for example SAT-AB12CD34", ""),
                        "appointment_at", property("string", "Confirmed new ISO-8601 appointment datetime", "date-time"),
                        "duration_minutes", property("integer", "Appointment duration in minutes", "")
                ), List.of("booking_number", "appointment_at")), "sauti_calendar", "noop_calendar", 21);
        seed(agent, "cancel_booking", "Cancel a booking only after the caller explicitly confirms cancellation.",
                schema(Map.of(
                        "booking_number", property("string", "Customer-facing Sauti booking number, for example SAT-AB12CD34", "")
                ), List.of("booking_number")), "sauti_calendar", "noop_calendar", 22);
        seed(agent, "send_confirmation_sms", "Send a booking confirmation SMS. If SMS is unavailable, explain that "
                        + "to the caller and offer WhatsApp when that tool is available.",
                schema(Map.of(
                        "phone", property("string", "Destination phone number", "phone"),
                        "message", property("string", "SMS body", "")
                ), List.of("phone", "message")), "sauti_sms", null, 30);
        seed(agent, "transfer_to_human", "Transfer the call to a human agent.",
                schema(Map.of("reason", property("string", "Reason for escalation", "")), List.of("reason")), "twilio_transfer", null, 40);
        seed(agent, "end_call", "End the call with an outcome summary.",
                schema(Map.of(
                        "outcome", property("string", "Final outcome code", ""),
                        "summary", property("string", "Short call summary", "")
                ), List.of("outcome")), "noop", null, 50);
        seed(agent, "send_whatsapp_message", "Send an approved WhatsApp template to the caller.",
                schema(Map.of("phone", property("string", "Recipient phone number", "phone")),
                        List.of("phone")), "sauti_integration", null, 60);
        seed(agent, "lookup_google_sheet_row", "Look up a configured Google Sheets row.",
                schema(Map.of("lookup_value", property("string", "Value in the configured lookup column", "")),
                        List.of("lookup_value")), "sauti_integration", null, 70);
        seed(agent, "update_google_sheet_row", "Replace a matching configured Google Sheets row after explicit caller confirmation.",
                schema(Map.of(
                        "lookup_value", property("string", "Value in the configured lookup column", ""),
                        "replacement_values", arrayProperty("Complete replacement row values in configured column order"),
                        "confirmed", property("boolean", "True only after the caller explicitly confirms the update", "")
                ), List.of("lookup_value", "replacement_values", "confirmed")), "sauti_integration", null, 71);
        seed(agent, "request_mpesa_payment", "Request a caller-confirmed M-Pesa STK Push. If the result is pending, "
                        + "ask the caller to wait briefly and use check_mpesa_payment before claiming success.",
                schema(Map.of(
                        "phone", property("string", "Confirmed Kenyan recipient number", "phone"),
                        "amount", property("number", "Caller-confirmed amount", ""),
                        "amount_confirmed", property("boolean", "True only after explicit confirmation", ""),
                        "account_reference", property("string", "Payment account reference", ""),
                        "description", property("string", "Short payment description", "")
                ), List.of("phone", "amount", "amount_confirmed", "account_reference", "description")),
                "sauti_integration", null, 80);
        seed(agent, "check_mpesa_payment", "Check whether an M-Pesa request from this call completed.",
                schema(Map.of(
                        "payment_request_id", property("string", "ID returned by request_mpesa_payment", "")
                ), List.of("payment_request_id")), "sauti_integration", null, 85);
        seed(agent, "call_custom_webhook", "Send structured data to the workspace's configured HTTPS webhook.",
                schema(Map.of("payload", property("object", "Structured request payload", "")),
                        List.of("payload")), "sauti_integration", null, 90);
        synchronizeAddedTools(agent);
        synchronizeCapabilities(agent);
    }

    private void synchronizeAddedTools(Agent agent) {
        var tools = agentToolRepository.findByAgent_IdOrderByDisplayOrderAsc(agent.getId());
        var calendarTools = Set.of("check_availability", "book_slot", "reschedule_booking", "cancel_booking");
        var connectedCalendar = tools.stream()
                .filter(tool -> calendarTools.contains(tool.getToolName()))
                .filter(tool -> "google".equalsIgnoreCase(tool.getCalendarType()))
                .filter(tool -> tool.getCalendarCredentialId() != null)
                .findFirst();
        if (connectedCalendar.isPresent()) {
            var credentialId = connectedCalendar.get().getCalendarCredentialId();
            tools.stream().filter(tool -> calendarTools.contains(tool.getToolName()))
                    .forEach(tool -> tool.connectCalendar("google", credentialId));
        } else {
            tools.stream().filter(tool -> Set.of("reschedule_booking", "cancel_booking").contains(tool.getToolName()))
                    .forEach(tool -> tool.configureForDraft(agent.isBookingEnabled(), "noop_calendar"));
        }
        var lookup = tools.stream().filter(tool -> "lookup_google_sheet_row".equals(tool.getToolName())).findFirst().orElse(null);
        if (lookup != null) {
            tools.stream().filter(tool -> "update_google_sheet_row".equals(tool.getToolName()))
                    .forEach(tool -> tool.configureForDraft(lookup.isActive(), null));
        }
    }

    public void configureOnboardingDraft(Agent agent) {
        synchronizeCapabilities(agent);
    }

    /** Keeps runtime tools aligned when booking or transfer settings change. */
    public void synchronizeCapabilities(Agent agent) {
        var calendarTools = Set.of("check_availability", "book_slot", "reschedule_booking", "cancel_booking");
        for (var tool : agentToolRepository.findByAgent_IdOrderByDisplayOrderAsc(agent.getId())) {
            if (calendarTools.contains(tool.getToolName())) {
                // Preserve an attached Google credential. Draft tools already
                // carry noop_calendar from seeding and remain locally testable.
                tool.configureForDraft(agent.isBookingEnabled(), null);
            } else if ("send_confirmation_sms".equals(tool.getToolName())) {
                tool.configureForDraft(agent.isBookingEnabled(), null);
            } else if ("transfer_to_human".equals(tool.getToolName())) {
                tool.configureForDraft(agent.getHumanTransferNumber() != null
                        && !agent.getHumanTransferNumber().isBlank(), null);
            } else if ("end_call".equals(tool.getToolName())) {
                tool.configureForDraft(true, null);
            } else if ("get_business_hours".equals(tool.getToolName())) {
                tool.configureForDraft(true, null);
            }
        }
    }

    private void seed(Agent agent, String name, String description, Map<String, Object> schema, String fulfillmentType, String calendarType, int order) {
        if (agentToolRepository.existsByAgent_IdAndToolName(agent.getId(), name)) {
            return;
        }
        var tool = new AgentTool(agent, name, description, schema, fulfillmentType, false, order);
        tool.update(name, description, schema, fulfillmentType, null, "POST", "none", null, null, calendarType, null, false, order);
        agentToolRepository.save(tool);
    }

    private Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private Map<String, Object> property(String type, String description, String format) {
        if (format == null || format.isBlank()) {
            return Map.of("type", type, "description", description);
        }
        return Map.of("type", type, "description", description, "format", format);
    }

    private Map<String, Object> arrayProperty(String description) {
        return Map.of("type", "array", "description", description, "items", Map.of("type", "string"));
    }
}
