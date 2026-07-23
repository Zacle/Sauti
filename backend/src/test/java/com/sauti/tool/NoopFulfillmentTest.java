package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoopFulfillmentTest {
    @Test
    void returnsTheModelAuthoredFarewellAsGuardedDeterministicSpeech() {
        var result = new NoopFulfillment().execute(
                mock(Call.class),
                mock(AgentTool.class),
                new LlmToolCall("end-1", "end_call", Map.of(
                        "outcome", "completed",
                        "summary", "Booking completed",
                        "spoken_farewell", "You're welcome, Harry. Thank you for calling, and have a good day."
                ))
        );

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("ended", true)
                .containsEntry("outcome", "completed")
                .containsEntry(
                        "spokenResponse",
                        "You're welcome, Harry. Thank you for calling, and have a good day."
                );
    }

    @Test
    void rejectsProtocolShapedFarewellText() {
        var result = new NoopFulfillment().execute(
                mock(Call.class),
                mock(AgentTool.class),
                new LlmToolCall("end-2", "end_call", Map.of(
                        "outcome", "completed",
                        "spoken_farewell", "analysis: call end"
                ))
        );

        assertThat(result.result()).doesNotContainKey("spokenResponse");
    }
}
