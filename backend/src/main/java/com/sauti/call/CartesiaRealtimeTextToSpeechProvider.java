package com.sauti.call;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.tts.streaming-provider", havingValue = "cartesia", matchIfMissing = true)
public class CartesiaRealtimeTextToSpeechProvider implements RealtimeTextToSpeechProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(CartesiaRealtimeTextToSpeechProvider.class);

    private final CartesiaRealtimeTextToSpeechClient client;
    private final String defaultVoiceId;

    public CartesiaRealtimeTextToSpeechProvider(
            CartesiaRealtimeTextToSpeechClient client,
            @Value("${sauti.tts.cartesia.default-voice-id:}") String defaultVoiceId
    ) {
        this.client = client;
        this.defaultVoiceId = normalize(defaultVoiceId);
    }

    @Override
    public CompletableFuture<RealtimeTtsSession> open(
            String language,
            String voiceId,
            TtsAudioListener listener
    ) {
        var selectedVoiceId = voiceId == null ? "" : voiceId.trim();
        if (!selectedVoiceId.startsWith(CartesiaRealtimeTextToSpeechClient.VOICE_PREFIX)) {
            if (defaultVoiceId.isBlank()) {
                throw new IllegalStateException(
                        "The agent does not have a Cartesia voice. Select one in Agent Studio or configure CARTESIA_DEFAULT_VOICE_ID."
                );
            }
            LOGGER.warn("Replacing a non-Cartesia agent voice with the configured Cartesia default");
            selectedVoiceId = defaultVoiceId;
        }
        return client.open(language, selectedVoiceId, listener);
    }

    private String normalize(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) return "";
        var normalized = voiceId.trim();
        return normalized.startsWith(CartesiaRealtimeTextToSpeechClient.VOICE_PREFIX)
                ? normalized
                : CartesiaRealtimeTextToSpeechClient.VOICE_PREFIX + normalized;
    }
}
