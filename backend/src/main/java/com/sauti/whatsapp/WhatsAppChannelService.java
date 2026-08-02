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
    private final WhatsAppInboxService inbox;
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
            WhatsAppInboxService inbox,
            BrowserSpeechToTextService speechToTextService,
            VoiceCatalogService voiceCatalogService,
            OggOpusAudioConverter audioConverter,
            IntegrationService integrations
    ) {
        this.objectMapper = objectMapper;
        this.messageRepository = messageRepository;
        this.callPipelineService = callPipelineService;
        this.messageSender = messageSender;
        this.inbox = inbox;
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
        for (var status : value.withArray("statuses")) {
            var error = status.path("errors").path(0);
            inbox.providerStatus(status.path("id").asText(""), status.path("status").asText(""),
                    error.path("title").asText(error.path("message").asText("")));
        }
        var phoneNumberId = value.path("metadata").path("phone_number_id").asText("");
        if (phoneNumberId.isBlank()) {
            return;
        }
        var customerName = value.path("contacts").path(0).path("profile").path("name").asText("");
        for (var message : value.withArray("messages")) {
            var messageId = message.path("id").asText("");
            var customerNumber = message.path("from").asText("");
            var type = message.path("type").asText("");
            if (messageId.isBlank() || customerNumber.isBlank() || type.isBlank()) {
                continue;
            }
            if ("text".equals(type)) {
                scheduleText(messageId, phoneNumberId, customerNumber, customerName,
                        message.path("text").path("body").asText(""));
            } else if ("audio".equals(type)) {
                scheduleAudio(
                        messageId,
                        phoneNumberId,
                        customerNumber,
                        customerName,
                        message.path("audio").path("id").asText("")
                );
            } else {
                scheduleRichMessage(messageId, phoneNumberId, customerNumber, customerName, type, message);
            }
        }
    }

    private void scheduleText(String messageId, String phoneNumberId, String customerNumber,
                              String customerName, String text) {
        if (text.isBlank() || !claim(messageId, phoneNumberId, customerNumber, "text")) {
            return;
        }
        executor.execute(() -> processText(messageId, phoneNumberId, customerNumber, customerName, text));
    }

    private void processText(String messageId, String phoneNumberId, String customerNumber,
                             String customerName, String text) {
        var inbound = messageRepository.findByProviderMessageId(messageId).orElseThrow();
        try {
            inbound.markProcessing();
            messageRepository.save(inbound);
            var call = callPipelineService.startWhatsAppConversation(phoneNumberId, customerNumber);
            var recorded = inbox.recordInbound(call, messageId, customerName, "text", text, null, null);
            if ("human".equals(recorded.mode())) {
                inbound.markCompleted();
                return;
            }
            var turn = callPipelineService.processLiveTranscriptTurn(call, text);
            if (!turn.text().isBlank()) {
                inbox.sendAiText(call, recorded.conversationId(), turn.text());
            }
            inbound.markCompleted();
        } catch (Exception exception) {
            LOGGER.warn("WhatsApp message processing failed messageId={}", messageId, exception);
            inbound.markFailed(exception.getMessage());
        } finally {
            saveFinalState(inbound, messageId);
        }
    }

    private void scheduleAudio(String messageId, String phoneNumberId, String customerNumber,
                               String customerName, String mediaId) {
        if (mediaId.isBlank() || !claim(messageId, phoneNumberId, customerNumber, "audio")) {
            return;
        }
        executor.execute(() -> processAudio(messageId, phoneNumberId, customerNumber, customerName, mediaId));
    }

    private void processAudio(String messageId, String phoneNumberId, String customerNumber,
                              String customerName, String mediaId) {
        var inbound = messageRepository.findByProviderMessageId(messageId).orElseThrow();
        try {
            inbound.markProcessing();
            messageRepository.save(inbound);
            var call = callPipelineService.startWhatsAppConversation(phoneNumberId, customerNumber);
            var token = workspaceToken(call);
            var media = messageSender.downloadMedia(mediaId, token);
            var transcript = speechToTextService.transcribe(call.getAgent(), media.bytes(), media.contentType());
            var recorded = inbox.recordInbound(call, messageId, customerName, "audio", transcript,
                    mediaId, media.contentType());
            if ("human".equals(recorded.mode())) {
                inbound.markCompleted();
                return;
            }
            var turn = callPipelineService.processLiveTranscriptTurn(call, transcript);
            if (!turn.text().isBlank()) {
                var voiceId = resolveVoiceId(call.getAgent().getTtsVoiceId(), turn.language());
                if (voiceId == null) {
                    inbox.sendAiText(call, recorded.conversationId(), turn.text());
                } else {
                    var mp3 = voiceCatalogService.synthesize(voiceId, turn.language(), turn.text());
                    inbox.sendAiVoice(call, recorded.conversationId(), turn.text(), audioConverter.fromMp3(mp3));
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

    private void scheduleRichMessage(String messageId, String phoneNumberId, String customerNumber,
                                     String customerName, String type, JsonNode message) {
        if (!claim(messageId, phoneNumberId, customerNumber, type)) return;
        var body = richBody(type, message);
        var mediaId = message.path(type).path("id").asText("");
        var mime = message.path(type).path("mime_type").asText("");
        executor.execute(() -> processRichMessage(messageId, phoneNumberId, customerNumber,
                customerName, type, body, mediaId, mime));
    }

    private void processRichMessage(String messageId, String phoneNumberId, String customerNumber,
                                    String customerName, String type, String body,
                                    String mediaId, String mime) {
        var inbound = messageRepository.findByProviderMessageId(messageId).orElseThrow();
        try {
            inbound.markProcessing();
            messageRepository.save(inbound);
            var call = callPipelineService.startWhatsAppConversation(phoneNumberId, customerNumber);
            var recorded = inbox.recordInbound(call, messageId, customerName, type, body, mediaId, mime);
            if (!"human".equals(recorded.mode())) {
                var prompt = body.isBlank() ? "[The customer sent a WhatsApp " + type + "]" : body;
                var turn = callPipelineService.processLiveTranscriptTurn(call, prompt);
                if (!turn.text().isBlank()) inbox.sendAiText(call, recorded.conversationId(), turn.text());
            }
            inbound.markCompleted();
        } catch (Exception exception) {
            LOGGER.warn("WhatsApp rich-message processing failed messageId={}", messageId, exception);
            inbound.markFailed(exception.getMessage());
        } finally {
            saveFinalState(inbound, messageId);
        }
    }

    private String richBody(String type, JsonNode message) {
        return switch (type) {
            case "image", "video", "document" -> message.path(type).path("caption").asText("");
            case "button" -> message.path("button").path("text").asText("");
            case "interactive" -> {
                var interactive = message.path("interactive");
                var value = interactive.path("button_reply").path("title").asText("");
                if (value.isBlank()) value = interactive.path("list_reply").path("title").asText("");
                yield value;
            }
            case "location" -> "Location: " + message.path("location").path("latitude").asText("")
                    + ", " + message.path("location").path("longitude").asText("");
            case "contacts" -> message.path("contacts").path(0).path("name").path("formatted_name").asText("");
            default -> "";
        };
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
