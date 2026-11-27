package com.sauti.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.BrowserSpeechToTextService;
import com.sauti.call.CallPipelineService;
import com.sauti.voice.VoiceCatalogService;
import com.sauti.integration.IntegrationService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.sauti.provisioning.PilotProvisioningPolicyService;

@Service
public class WhatsAppChannelService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppChannelService.class);
    /** A message still received or processing after this long was dropped by a restart. */
    static final Duration STUCK_AFTER = Duration.ofMinutes(5);
    /** Wait before trying a failed message again. */
    static final Duration RETRY_FAILED_AFTER = Duration.ofMinutes(1);
    private static final int RETRY_BATCH = 20;
    private final ObjectMapper objectMapper;
    private final WhatsAppInboundMessageRepository messageRepository;
    private final CallPipelineService callPipelineService;
    private final WhatsAppMessageSender messageSender;
    private final WhatsAppInboxService inbox;
    private final BrowserSpeechToTextService speechToTextService;
    private final VoiceCatalogService voiceCatalogService;
    private final OggOpusAudioConverter audioConverter;
    private final IntegrationService integrations;
    private final PilotProvisioningPolicyService provisioningPolicies;
    private final com.sauti.billing.BillingAccessPolicy billingAccess;
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
            IntegrationService integrations,
            PilotProvisioningPolicyService provisioningPolicies,
            com.sauti.billing.BillingAccessPolicy billingAccess
    ) {
        this.billingAccess = billingAccess;
        this.objectMapper = objectMapper;
        this.messageRepository = messageRepository;
        this.callPipelineService = callPipelineService;
        this.messageSender = messageSender;
        this.inbox = inbox;
        this.speechToTextService = speechToTextService;
        this.voiceCatalogService = voiceCatalogService;
        this.audioConverter = audioConverter;
        this.integrations = integrations;
        this.provisioningPolicies = provisioningPolicies;
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
            String body;
            String mediaId;
            String mime;
            if ("text".equals(type)) {
                body = message.path("text").path("body").asText("");
                if (body.isBlank()) continue;
                mediaId = "";
                mime = "";
            } else if ("audio".equals(type)) {
                body = "";
                mediaId = message.path("audio").path("id").asText("");
                if (mediaId.isBlank()) continue;
                mime = message.path("audio").path("mime_type").asText("");
            } else {
                body = richBody(type, message);
                mediaId = message.path(type).path("id").asText("");
                mime = message.path(type).path("mime_type").asText("");
            }
            if (claim(new WhatsAppInboundMessage(messageId, phoneNumberId, customerNumber, type,
                    customerName, body, mediaId, mime))) {
                executor.execute(() -> process(messageId));
            }
        }
    }

    /**
     * The team closes a conversation. Its call record is ended now, so the summary and follow-ups run
     * without waiting for 24 quiet hours. A new customer message reopens it.
     */
    public WhatsAppInboxDtos.ConversationResponse close(java.util.UUID tenantId, java.util.UUID conversationId) {
        return close(tenantId, conversationId, WhatsAppConversation.CLOSED_BY_TEAM);
    }

    /** Closes the conversation for the given reason, ending its call record if that is still open. */
    public WhatsAppInboxDtos.ConversationResponse close(java.util.UUID tenantId, java.util.UUID conversationId,
                                                        String reason) {
        var closed = inbox.close(tenantId, conversationId, reason);
        var session = closed.openSession();
        if (session != null) {
            callPipelineService.endWhatsAppConversation(session.agentId(), session.customerNumber(), "conversation_closed");
            inbox.sessionEnded(session.conversationId());
        }
        return inbox.conversation(tenantId, conversationId);
    }

    /**
     * Processes again the customer messages that a restart left unfinished, and failures with tries left.
     * Each is queued once; a message still waiting in the queue is not picked up again.
     */
    public int retryUnfinished() {
        var now = OffsetDateTime.now();
        var due = messageRepository.findRetryable(now.minus(STUCK_AFTER), now.minus(RETRY_FAILED_AFTER),
                WhatsAppInboundMessage.MAX_ATTEMPTS, PageRequest.of(0, RETRY_BATCH));
        for (var inbound : due) {
            inbound.queuedAgain();
            messageRepository.save(inbound);
            var messageId = inbound.getProviderMessageId();
            LOGGER.info("Processing a WhatsApp message again messageId={} attempt={}", messageId,
                    inbound.getAttempts() + 1);
            executor.execute(() -> process(messageId));
        }
        return due.size();
    }

    /**
     * Handles one customer message. It reaches the inbox first, whatever happens next, so staff always see
     * it. The agent then answers unless a person has taken over or automatic replies are paused; in those
     * cases, and when the agent fails for the last time, the conversation is marked as waiting on the team.
     */
    void process(String messageId) {
        var inbound = messageRepository.findByProviderMessageId(messageId).orElse(null);
        if (inbound == null || "completed".equals(inbound.getStatus())) return;
        try {
            inbound.markProcessing();
            messageRepository.save(inbound);
            var phoneNumberId = inbound.getPhoneNumberId();
            var customerNumber = inbound.getCustomerNumber();
            var type = inbound.getMessageType();
            var previous = inbox.lastCustomerMessageAt(phoneNumberId, customerNumber).orElse(null);
            var call = callPipelineService.startWhatsAppConversation(phoneNumberId, customerNumber, previous);
            provisioningPolicies.authorize(call.getTenant().getId(), "whatsapp");
            var recorded = inbox.recordInbound(call, messageId, inbound.getCustomerName(), type,
                    inbound.getBody(), inbound.getMediaId(), inbound.getMediaMimeType());
            if (!recorded.created() && inbox.alreadyAnswered(messageId)) {
                inbound.markCompleted();
                return;
            }
            var human = "human".equals(recorded.mode());
            if (!aiMayReply(call)) {
                // Kept for staff in the inbox; a voice note is not transcribed, since that is paid work too.
                inbox.needsAttention(messageId, human
                        ? WhatsAppConversation.WAITING_FOR_TEAM : WhatsAppConversation.AGENT_PAUSED);
                inbound.markCompleted();
                return;
            }
            var prompt = inbound.getBody();
            String audioContentType = null;
            if ("audio".equals(type)) {
                var media = messageSender.downloadMedia(inbound.getMediaId(), workspaceToken(call));
                prompt = speechToTextService.transcribe(call.getAgent(), media.bytes(), media.contentType());
                audioContentType = media.contentType();
                inbox.transcribed(messageId, prompt);
            } else if (!"text".equals(type) && prompt.isBlank()) {
                prompt = "[The customer sent a WhatsApp " + type + "]";
            }
            if (human) {
                inbox.needsAttention(messageId, WhatsAppConversation.WAITING_FOR_TEAM);
                inbound.markCompleted();
                return;
            }
            var turn = callPipelineService.processLiveTranscriptTurn(call, prompt);
            if (!turn.text().isBlank()) {
                var voiceId = audioContentType == null ? null
                        : resolveVoiceId(call.getAgent().getTtsVoiceId(), turn.language());
                if (voiceId == null) {
                    inbox.sendAiText(call, recorded.conversationId(), turn.text());
                } else {
                    var mp3 = voiceCatalogService.synthesize(voiceId, turn.language(), turn.text());
                    inbox.sendAiVoice(call, recorded.conversationId(), turn.text(), audioConverter.fromMp3(mp3));
                }
            }
            inbound.markCompleted();
        } catch (Exception exception) {
            LOGGER.warn("WhatsApp message processing failed messageId={} attempt={}",
                    messageId, inbound.getAttempts(), exception);
            inbound.markFailed(exception.getMessage());
            if (!inbound.canRetry()) {
                leaveForStaff(messageId);
            }
        } finally {
            saveFinalState(inbound, messageId);
        }
    }

    private void leaveForStaff(String messageId) {
        try {
            inbox.needsAttention(messageId, WhatsAppConversation.AGENT_FAILED);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not flag a WhatsApp conversation for staff messageId={}", messageId, exception);
        }
    }

    /**
     * Whether the agent may answer: not when the workspace's subscription is unpaid or its messages for
     * the cycle are used. Running out of call minutes does not stop replies. A held message still reaches
     * the inbox, so staff can reply themselves.
     */
    private boolean aiMayReply(com.sauti.call.Call call) {
        var decision = billingAccess.automatedMessages(call.getTenant().getId());
        if (!decision.allowed()) {
            LOGGER.info("WhatsApp reply held for staff: {}", decision.blockedReason());
        }
        return decision.allowed();
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

    private boolean claim(WhatsAppInboundMessage message) {
        if (messageRepository.existsByProviderMessageId(message.getProviderMessageId())) {
            return false;
        }
        try {
            messageRepository.save(message);
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
