package com.sauti.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.sauti.call.VoiceRuntimeUnavailableException;
import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTest {
    @Test
    void returnsAUsefulServiceUnavailableResponseForVoiceRuntimeConfiguration() {
        var response = new ApiExceptionHandler().voiceRuntimeUnavailable(
                new VoiceRuntimeUnavailableException("vapi test calls are not configured")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isEqualTo(new ApiError(
                "voice_runtime_unavailable",
                "vapi test calls are not configured"
        ));
    }
}
