package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What the owner reads about each action, and which deliveries count as repeats. */
class ToolActionLogTest {
    private final LlmToolCall call = new LlmToolCall("telnyx:webhook:1", "book_slot", Map.of());

    @Test
    void aBookingReadsLikeAReceipt() {
        var result = LlmToolResult.success(call, Map.of(
                "bookingCreated", true, "bookingNumber", "SAT-7Q2K9M4XW1PZ",
                "appointmentAt", "2026-11-24T10:30:00+03:00", "caller_phone", "+254700000000"));

        var status = ToolActionLog.status(result);

        assertThat(status).isEqualTo(CallToolAction.PERFORMED);
        assertThat(ToolActionLog.summary("book_slot", status, result))
                .isEqualTo("Booked SAT-7Q2K9M4XW1PZ for 24 Nov 2026, 10:30")
                .doesNotContain("254700000000");
    }

    @Test
    void aReviewWaitsForConfirmationAndADeclineSaysWhy() {
        var review = LlmToolResult.success(call, Map.of("actionPerformed", false, "status", "booking_review_required"));
        var taken = LlmToolResult.success(call, Map.of("actionPerformed", false, "status", "slot_unavailable"));

        assertThat(ToolActionLog.status(review)).isEqualTo(CallToolAction.AWAITING_CONFIRMATION);
        assertThat(ToolActionLog.status(taken)).isEqualTo(CallToolAction.NOT_PERFORMED);
        assertThat(ToolActionLog.summary("book_slot", CallToolAction.NOT_PERFORMED, taken))
                .isEqualTo("Not done: slot unavailable");
    }

    @Test
    void aFailureNeverRepeatsTheProvidersErrorText() {
        var failed = LlmToolResult.error(call, "SMS to +254700000000 rejected");

        assertThat(ToolActionLog.status(failed)).isEqualTo(CallToolAction.FAILED);
        assertThat(ToolActionLog.summary("send_confirmation_sms", CallToolAction.FAILED, failed))
                .isEqualTo("Could not be completed");
    }

    @Test
    void onlyProviderDeliveriesAreTreatedAsRepeats() {
        assertThat(ToolActionLog.invocationKey(call)).isEqualTo("telnyx:webhook:1");
        var local = new LlmToolCall("call_0", "book_slot", Map.of());
        assertThat(ToolActionLog.invocationKey(local)).startsWith("local:")
                .isNotEqualTo(ToolActionLog.invocationKey(local));
    }
}
