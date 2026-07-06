package com.sauti.voice;

import java.util.List;
import java.util.Map;

public final class VoiceCatalogDtos {
    private VoiceCatalogDtos() {
    }

    public record VoiceOption(
            String provider,
            String id,
            String name,
            String description,
            String category,
            String previewUrl,
            List<String> languages,
            Map<String, String> traits,
            boolean owned
    ) {
    }

    public record VoiceCatalogResponse(
            List<String> enabledProviders,
            List<VoiceOption> voices
    ) {
    }
}
