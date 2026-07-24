package com.sauti.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.sauti.call.VoiceRuntimeUnavailableException;
import com.sauti.call.ManagedVoiceProviderException;
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

    @Test
    void returnsASafeProviderSetupErrorWithoutLeakingTheResponseBody() {
        var response = new ApiExceptionHandler().managedVoiceProvider(
                new ManagedVoiceProviderException("ElevenLabs", 401)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).isEqualTo(new ApiError(
                "managed_voice_provider_error",
                "ElevenLabs rejected managed voice setup with status 401. "
                        + "Check the API key and its agent/tool permissions."
        ));
    }
}
