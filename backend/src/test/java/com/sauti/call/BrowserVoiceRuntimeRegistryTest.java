package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class BrowserVoiceRuntimeRegistryTest {
    @Test
    void reportsMissingProviderCredentialsAsARuntimeConfigurationProblem() {
        var provider = mock(BrowserVoiceRuntimeProvider.class);
        when(provider.provider()).thenReturn("vapi");
        when(provider.isConfigured()).thenReturn(false);
        var registry = new BrowserVoiceRuntimeRegistry(List.of(provider));

        assertThatThrownBy(() -> registry.requireConfigured("vapi"))
                .isInstanceOf(VoiceRuntimeUnavailableException.class)
                .hasMessageContaining("process environment")
                .hasMessageNotContaining("API key");
    }
}
