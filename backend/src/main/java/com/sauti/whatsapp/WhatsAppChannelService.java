package com.sauti.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.BrowserSpeechToTextService;
import com.sauti.call.CallPipelineService;
import com.sauti.voice.VoiceCatalogService;
import com.sauti.integration.IntegrationService;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppChannelService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppChannelService.class);
    private final ObjectMapper objectMapper;
    private final WhatsAppInboundMessageRepository messageRepository;
    private final CallPipelineService callPipelineService;
    private final WhatsAppMessageSender messageSender;
    private final BrowserSpeechToTextService speechToTextService;
    private final VoiceCatalogService voiceCatalogService;
    private final OggOpusAudioConverter audioConverter;
    private final IntegrationService integrations;
    private final AtomicInteger workerSequence = new AtomicInteger();
    private final ExecutorService executor = new ThreadPoolExecutor(
            4,
            4,
            0,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(200),
            runnable -> {
        var thread = new Thread(runnable, "whatsapp-channel-" + workerSequence.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    },
            (task, pool) -> {
                LOGGER.warn("WhatsApp processing queue is full; applying backpressure on the webhook thread");
                if (!pool.isShutdown()) task.run();
            }
    );

    public WhatsAppChannelService(
            ObjectMapper objectMapper,
            WhatsAppInboundMessageRepository messageRepository,
            CallPipelineService callPipelineService,
            WhatsAppMessageSender messageSender,
            BrowserSpeechToTextService speechToTextService,
            VoiceCatalogService voiceCatalogService,
            OggOpusAudioConverter audioConverter,
            IntegrationService integrations
    ) {
        this.objectMapper = objectMapper;
        this.messageRepository = messageRepository;
        this.callPipelineService = callPipelineService;
        this.messageSender = messageSender;
        this.speechToTextService = speechToTextService;
        this.voiceCatalogService = voiceCatalogService;
        this.audioConverter = audioConverter;
        this.integrations = integrations;
    }

    public void accept(String payload) {
        try {
            var root = objectMapper.readTree(payload);
            for (var entry : root.withArray("entry")) {
                for (var change : entry.withArray("changes")) {
                    acceptValue(change.path("value"));
                }
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid WhatsApp webhook payload", exception);
        }
    }

    private void acceptValue(JsonNode value) {
        var phoneNumberId = value.path("metadata").path("phone_number_id").asText("");
        if (phoneNumberId.isBlank()) {
            return;
        }
        for (var message : value.withArray("messages")) {
            var messageId = message.path("id").asText("");
            var customerNumber = message.path("from").asText("");
            var type = message.path("type").asText("");
            if (messageId.isBlank() || customerNumber.isBlank() || type.isBlank()) {
                continue;
            }
            if ("text".equals(type)) {
                scheduleText(messageId, phoneNumberId, customerNumber, message.path("text").path("body").asText(""));
            } else if ("audio".equals(type)) {
                scheduleAudio(
                        messageId,
                        phoneNumberId,
                        customerNumber,
                        message.path("audio").path("id").asText("")
                );
            }
        }
    }

    private void scheduleText(String messageId, String phoneNumberId, String customerNumber, String text) {
        if (text.isBlank() || !claim(messageId, phoneNumberId, customerNumber, "text")) {
            return;
        }
        executor.execute(() -> processText(messageId, phoneNumberId, customerNumber, text));
    }

    private void processText(String messageId, String phoneNumberId, String customerNumber, String text) {
        var inbound = messageRepository.findByProviderMessageId(messageId).orElseThrow();
        try {
            inbound.markProcessing();
            messageRepository.save(inbound);
            var call = callPipelineService.startWhatsAppConversation(phoneNumberId, customerNumber);
            var turn = callPipelineService.processLiveTranscriptTurn(call, text);
            if (!turn.text().isBlank()) {
                messageSender.sendText(phoneNumberId, customerNumber, turn.text(), workspaceToken(call));
            }
            inbound.markCompleted();
        } catch (Exception exception) {
            LOGGER.warn("WhatsApp message processing failed messageId={}", messageId, exception);
            inbound.markFailed(exception.getMessage());
        } finally {
            saveFinalState(inbound, messageId);
        }
    }

    private void scheduleAudio(String messageId, String phoneNumberId, String customerNumber, String mediaId) {
        if (mediaId.isBlank() || !claim(messageId, phoneNumberId, customerNumber, "audio")) {
            return;
        }
        executor.execute(() -> processAudio(messageId, phoneNumberId, customerNumber, mediaId));
    }

    private void processAudio(String messageId, String phoneNumberId, String customerNumber, String mediaId) {
        var inbound = messageRepository.findByProviderMessageId(messageId).orElseThrow();
        try {
            inbound.markProcessing();
            messageRepository.save(inbound);
            var call = callPipelineService.startWhatsAppConversation(phoneNumberId, customerNumber);
            var token = workspaceToken(call);
            var media = messageSender.downloadMedia(mediaId, token);
            var transcript = speechToTextService.transcribe(call.getAgent(), media.bytes(), media.contentType());
            var turn = callPipelineService.processLiveTranscriptTurn(call, transcript);
            if (!turn.text().isBlank()) {
                var voiceId = resolveVoiceId(call.getAgent().getTtsVoiceId(), turn.language());
                if (voiceId == null) {
                    messageSender.sendText(phoneNumberId, customerNumber, turn.text(), token);
                } else {
                    var mp3 = voiceCatalogService.synthesize(voiceId, turn.language(), turn.text());
                    messageSender.sendVoiceNote(
                            phoneNumberId, customerNumber, audioConverter.fromMp3(mp3), token);
                }
            }
            inbound.markCompleted();
        } catch (Exception exception) {
            LOGGER.warn("WhatsApp voice-note processing failed messageId={}", messageId, exception);
            inbound.markFailed(exception.getMessage());
        } finally {
            saveFinalState(inbound, messageId);
        }
    }

    private void saveFinalState(WhatsAppInboundMessage inbound, String messageId) {
        try {
            messageRepository.save(inbound);
        } catch (Exception exception) {
            LOGGER.error("Could not persist final WhatsApp message state messageId={}", messageId, exception);
        }
    }

    private String resolveVoiceId(String configuredVoiceId, String language) {
        if (configuredVoiceId != null && !configuredVoiceId.isBlank()) {
            return configuredVoiceId;
        }
        return voiceCatalogService.list().voices().stream()
                .filter(voice -> voice.languages().contains(language))
                .map(voice -> voice.id())
                .findFirst()
                .orElse(null);
    }

    private String workspaceToken(com.sauti.call.Call call) {
        try {
            return String.valueOf(integrations.runtime(
                    call.getTenant().getId(), call.getAgent().getId(), "whatsapp"
            ).credentials().getOrDefault("accessToken", ""));
        } catch (RuntimeException exception) {
            LOGGER.warn("No enabled workspace WhatsApp connection for agentId={}; using legacy environment fallback",
                    call.getAgent().getId());
            return "";
        }
    }

    private boolean claim(String messageId, String phoneNumberId, String customerNumber, String type) {
        if (messageRepository.existsByProviderMessageId(messageId)) {
            return false;
        }
        try {
            messageRepository.save(new WhatsAppInboundMessage(messageId, phoneNumberId, customerNumber, type));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}
