package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The record of every side-effecting action during a call, and the guard that runs each one at most
 * once. A voice provider may deliver the same tool call twice (a timeout, a retry, a restart in
 * between); the second delivery finds the first one's row and is told the outcome instead of
 * booking, texting or charging again.
 *
 * <p>Rows join the caller's transaction when there is one (Sauti's own model loop) and are written
 * straight away otherwise (voice provider deliveries, the only ones that can repeat).
 */
@Service
public class ToolActionLog {
    private final CallToolActionRepository actions;

    @Autowired
    public ToolActionLog(CallToolActionRepository actions) {
        this.actions = actions;
    }

    private ToolActionLog() {
        this.actions = null;
    }

    /** Records nothing and never blocks; for tests and code paths without storage. */
    public static ToolActionLog disabled() {
        return new ToolActionLog();
    }

    public record ActionView(String toolName, String effect, String status, String summary,
                             java.time.OffsetDateTime at) {
    }

    /**
     * Claims this tool call before it runs.
     *
     * @return the result to give back instead of running it, when this exact call already ran or
     *         may have; empty when it should run now
     */
    public Optional<LlmToolResult> begin(Call call, AgentTool tool, ToolActionEffect effect, LlmToolCall toolCall,
                                         String invocation) {
        if (!enabled(call)) return Optional.empty();
        return run(() -> {
            var existing = actions.findByCallIdAndInvocationId(call.getId(), invocation);
            if (existing.isPresent()) {
                var row = existing.get();
                if (CallToolAction.PERFORMED.equals(row.getStatus())) return Optional.of(alreadyDone(toolCall, effect));
                if (CallToolAction.STARTED.equals(row.getStatus())) return Optional.of(outcomeUnknown(toolCall, effect));
                row.record(CallToolAction.STARTED, row.getSummary());
                actions.save(row);
                return Optional.<LlmToolResult>empty();
            }
            try {
                actions.saveAndFlush(new CallToolAction(call.getTenant().getId(), call.getId(), invocation,
                        tool.getToolName(), effect.value()));
                return Optional.<LlmToolResult>empty();
            } catch (DataIntegrityViolationException concurrentDelivery) {
                return Optional.of(outcomeUnknown(toolCall, effect));
            }
        });
    }

    /** Records how a claimed tool call ended. */
    public void finish(Call call, LlmToolCall toolCall, String invocation, LlmToolResult result) {
        if (!enabled(call)) return;
        var status = status(result);
        var summary = summary(toolCall.name(), status, result);
        run(() -> {
            actions.findByCallIdAndInvocationId(call.getId(), invocation).ifPresent(row -> {
                row.record(status, summary);
                actions.save(row);
            });
            return null;
        });
    }

    /** Records an action that was read back to the caller and now waits for their answer. */
    public void awaitingConfirmation(Call call, AgentTool tool, ToolActionEffect effect, String invocation) {
        if (!enabled(call)) return;
        run(() -> {
            var row = actions.findByCallIdAndInvocationId(call.getId(), invocation)
                    .orElseGet(() -> new CallToolAction(call.getTenant().getId(), call.getId(), invocation,
                            tool.getToolName(), effect.value()));
            row.record(CallToolAction.AWAITING_CONFIRMATION, "Read the details back for the caller to confirm");
            try {
                actions.saveAndFlush(row);
            } catch (DataIntegrityViolationException alreadyRecorded) {
                // A repeated delivery of the same proposal; the first row already says so.
            }
            return null;
        });
    }

    public List<ActionView> forCall(UUID tenantId, UUID callId) {
        if (actions == null) return List.of();
        return actions.findByTenantIdAndCallIdOrderByCreatedAtAsc(tenantId, callId).stream()
                .map(row -> new ActionView(row.getToolName(), row.getEffect(), row.getStatus(), row.getSummary(),
                        row.getCreatedAt()))
                .toList();
    }

    static String status(LlmToolResult result) {
        if (!result.success()) return CallToolAction.FAILED;
        var facts = result.result();
        if (!Boolean.FALSE.equals(facts.get("actionPerformed"))) return CallToolAction.PERFORMED;
        var marker = (String.valueOf(facts.getOrDefault("status", "")) + " "
                + facts.getOrDefault("reason", "")).toLowerCase(Locale.ROOT);
        return marker.contains("review") || marker.contains("confirm")
                ? CallToolAction.AWAITING_CONFIRMATION
                : CallToolAction.NOT_PERFORMED;
    }

    /** A short line for the owner. Built only from the tool name and non-personal facts. */
    static String summary(String toolName, String status, LlmToolResult result) {
        var facts = result.result();
        var booking = text(facts.get("bookingNumber"));
        var at = readableTime(text(facts.get("appointmentAt")));
        return switch (status) {
            case CallToolAction.AWAITING_CONFIRMATION -> "Read the details back for the caller to confirm";
            case CallToolAction.FAILED -> "Could not be completed";
            case CallToolAction.NOT_PERFORMED -> "Not done: " + humanize(text(facts.getOrDefault("status",
                    facts.getOrDefault("reason", "declined"))));
            default -> switch (toolName) {
                case "book_slot" -> join("Booked", booking, at.isBlank() ? "" : "for " + at);
                case "reschedule_booking" -> join("Rescheduled", booking, at.isBlank() ? "" : "to " + at);
                case "cancel_booking" -> join("Cancelled", booking, "");
                case "update_booking" -> join("Updated", booking, "");
                case "send_confirmation_sms" -> "Sent an SMS";
                case "send_whatsapp_message" -> "Sent a WhatsApp message";
                case "transfer_to_human" -> "Started a transfer to the team";
                case "request_mpesa_payment" -> "Requested an M-Pesa payment";
                case "update_google_sheet_row" -> "Updated the Google Sheet";
                case "end_call" -> "Ended the call";
                default -> "Ran " + toolName;
            };
        };
    }

    private static LlmToolResult alreadyDone(LlmToolCall toolCall, ToolActionEffect effect) {
        return LlmToolResult.success(toolCall, Map.of(
                "status", "already_completed",
                "actionPerformed", true,
                "effect", effect.value(),
                "action", toolCall.name(),
                "nextAction", "reply",
                "instruction", "This exact action already ran earlier in this call. Do not run it again. "
                        + "Tell the caller it is done."
        ));
    }

    private static LlmToolResult outcomeUnknown(LlmToolCall toolCall, ToolActionEffect effect) {
        return LlmToolResult.success(toolCall, Map.of(
                "status", "outcome_unknown",
                "actionPerformed", false,
                "outcomeUnknown", true,
                "effect", effect.value(),
                "action", toolCall.name(),
                "nextAction", "reply",
                "instruction", "This exact action was already started and its result is not known. Do not run it "
                        + "again. For a booking, look it up first; otherwise tell the caller the team will confirm."
        ));
    }

    private boolean enabled(Call call) {
        return actions != null && call != null && call.getId() != null && call.getTenant() != null;
    }

    /**
     * The key a tool call is recorded under. A voice provider's delivery id is stable across its
     * retries, so it is used as is and a repeat finds the first row. Sauti's own model loop has no
     * redelivery and may reuse ids across turns, so each of its calls gets a key of its own.
     */
    public static String invocationKey(LlmToolCall toolCall) {
        var id = toolCall.id() == null ? "" : toolCall.id().trim();
        var key = providerDelivered(id) ? id : "local:" + UUID.randomUUID();
        return key.length() <= 255 ? key : key.substring(0, 255);
    }

    static boolean providerDelivered(String id) {
        return id.startsWith("telnyx:");
    }

    private static <T> T run(Supplier<T> work) {
        return work.get();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /** "2026-11-24T10:30:00+03:00" as "24 Nov 2026, 10:30", in the time zone it was given in. */
    static String readableTime(String value) {
        if (value.isBlank()) return value;
        var format = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
        try {
            return java.time.OffsetDateTime.parse(value).format(format);
        } catch (java.time.format.DateTimeParseException notOffset) {
            try {
                return java.time.LocalDateTime.parse(value).format(format);
            } catch (java.time.format.DateTimeParseException notLocal) {
                return value;
            }
        }
    }

    private static String humanize(String value) {
        var words = value.replace('_', ' ').trim();
        return words.isEmpty() ? "declined" : words;
    }

    private static String join(String verb, String booking, String tail) {
        var line = verb + (booking.isBlank() ? " the booking" : " " + booking);
        return tail.isBlank() ? line : line + " " + tail;
    }
}
