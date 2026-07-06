package com.sauti.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class OnboardingDtos {
    private OnboardingDtos() {
    }

    public record CompleteOnboardingRequest(
            @NotBlank @Size(max = 100) String businessType,
            @NotBlank @Size(max = 100) String primaryUseCase,
            @Size(max = 500) String businessWebsite,
            @NotEmpty List<@NotBlank @Size(max = 200) String> bookableServices,
            @NotBlank @Size(max = 100) String timezone,
            @NotBlank @Size(max = 100) String calendarProvider,
            @NotBlank @Size(max = 100) String routingPolicy,
            @NotBlank @Size(max = 100) String agentName,
            @NotBlank @Size(max = 10) String defaultLanguage,
            @NotEmpty List<@NotBlank @Size(max = 10) String> supportedLanguages,
            @Size(max = 200) String ttsVoiceId,
            @Size(max = 200) String voiceProfile
    ) {
    }
}
