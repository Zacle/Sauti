package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** No tool that changes something can run without the caller's confirmation, whatever is stored. */
class ToolSafetyFloorTest {
    @Test
    void builtInActionsCannotBeMarkedReadOnly() {
        assertThat(ToolSafetyFloor.effect("sauti_sms", "send_confirmation_sms", ToolActionEffect.READ_ONLY))
                .isEqualTo(ToolActionEffect.EXTERNAL_COMMUNICATION);
        assertThat(ToolSafetyFloor.effect("call_transfer", "transfer_to_human", ToolActionEffect.DATA_WRITE))
                .isEqualTo(ToolActionEffect.TRANSFER);
        assertThat(ToolSafetyFloor.effect("sauti_integration", "request_mpesa_payment", ToolActionEffect.DATA_WRITE))
                .isEqualTo(ToolActionEffect.FINANCIAL);
        assertThat(ToolSafetyFloor.effect("sauti_calendar", "cancel_booking", ToolActionEffect.READ_ONLY))
                .isEqualTo(ToolActionEffect.DATA_WRITE);
        assertThat(ToolSafetyFloor.effect("noop", "end_call", ToolActionEffect.READ_ONLY))
                .isEqualTo(ToolActionEffect.TERMINAL);
    }

    @Test
    void customWebhooksKeepTheOwnersChoiceEvenWithABuiltInName() {
        assertThat(ToolSafetyFloor.effect("webhook", "book_slot", ToolActionEffect.READ_ONLY))
                .isEqualTo(ToolActionEffect.READ_ONLY);
        assertThat(ToolSafetyFloor.effect("webhook", "lookup_order", ToolActionEffect.DATA_WRITE))
                .isEqualTo(ToolActionEffect.DATA_WRITE);
        assertThat(ToolSafetyFloor.minimumEffect("webhook", "lookup_order")).isNull();
    }

    @Test
    void sideEffectsAlwaysNeedConfirmation() {
        assertThat(ToolSafetyFloor.confirmation("webhook", ToolActionEffect.DATA_WRITE, ToolConfirmationPolicy.NONE))
                .isEqualTo(ToolConfirmationPolicy.EXPLICIT);
        assertThat(ToolSafetyFloor.confirmation("sauti_sms", ToolActionEffect.EXTERNAL_COMMUNICATION,
                ToolConfirmationPolicy.NONE)).isEqualTo(ToolConfirmationPolicy.EXPLICIT);
        assertThat(ToolSafetyFloor.confirmation("webhook", ToolActionEffect.READ_ONLY, ToolConfirmationPolicy.EXPLICIT))
                .isEqualTo(ToolConfirmationPolicy.NONE);
    }

    @Test
    void verifiedReviewCountsOnlyWhereAReviewExists() {
        assertThat(ToolSafetyFloor.confirmation("sauti_calendar", ToolActionEffect.DATA_WRITE,
                ToolConfirmationPolicy.VERIFIED_REVIEW)).isEqualTo(ToolConfirmationPolicy.VERIFIED_REVIEW);
        assertThat(ToolSafetyFloor.confirmation("webhook", ToolActionEffect.DATA_WRITE,
                ToolConfirmationPolicy.VERIFIED_REVIEW)).isEqualTo(ToolConfirmationPolicy.EXPLICIT);
    }
}
