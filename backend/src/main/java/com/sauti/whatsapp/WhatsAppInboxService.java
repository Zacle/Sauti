package com.sauti.whatsapp;

import com.sauti.billing.CommunicationUsageMeteringService;
import com.sauti.call.Call;
import com.sauti.integration.IntegrationService;
import com.sauti.integration.WhatsAppEmbeddedSignupService;
import com.sauti.integration.WhatsAppEmbeddedSignupService.TemplateOption;
import com.sauti.whatsapp.WhatsAppInboxDtos.ConversationChanges;
import com.sauti.whatsapp.WhatsAppInboxDtos.ConversationPage;
import com.sauti.whatsapp.WhatsAppInboxDtos.ConversationResponse;
import com.sauti.whatsapp.WhatsAppInboxDtos.MessageResponse;
import com.sauti.whatsapp.WhatsAppInboxDtos.TemplateField;
import com.sauti.whatsapp.WhatsAppInboxDtos.TemplateResponse;
import com.sauti.whatsapp.WhatsAppMessage.StaffSender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sauti.provisioning.PilotProvisioningPolicyService;

@Service
public class WhatsAppInboxService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppInboxService.class);
    private final WhatsAppConversationRepository conversations;
    private final WhatsAppMessageRepository messages;
    private final IntegrationService integrations;
    private final WhatsAppMessageSender sender;
    private final CommunicationUsageMeteringService usageMetering;
    private final PilotProvisioningPolicyService provisioningPolicies;
    private final WhatsAppEmbeddedSignupService templateCatalog;
    private final com.sauti.auth.application.AccountQueries accounts;
    private final org.springframework.context.ApplicationEventPublisher events;

    /** How far the changes cursor reaches back, so a change still being saved when it was read is not missed. */
    static final Duration CHANGES_OVERLAP = Duration.ofSeconds(30);
    static final int MAX_PAGE_SIZE = 100;
    private static final int CHANGES_BATCH = 200;
    private static final OffsetDateTime FIRST_PAGE_AT = OffsetDateTime.parse("9999-12-31T00:00:00Z");
    private static final UUID FIRST_PAGE_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    public WhatsAppInboxService(WhatsAppConversationRepository conversations,
                                WhatsAppMessageRepository messages,
                                IntegrationService integrations,
                                WhatsAppMessageSender sender,
                                CommunicationUsageMeteringService usageMetering,
                                PilotProvisioningPolicyService provisioningPolicies,
                                WhatsAppEmbeddedSignupService templateCatalog,
                                com.sauti.auth.application.AccountQueries accounts,
                                org.springframework.context.ApplicationEventPublisher events) {
        this.conversations = conversations;
        this.messages = messages;
        this.integrations = integrations;
        this.sender = sender;
        this.usageMetering = usageMetering;
        this.provisioningPolicies = provisioningPolicies;
        this.templateCatalog = templateCatalog;
        this.accounts = accounts;
        this.events = events;
    }

    private WhatsAppConversation saveConversation(WhatsAppConversation conversation) {
        var saved = conversations.save(conversation);
        events.publishEvent(new WhatsAppInboxChanged(saved.getTenantId(), saved.getId()));
        return saved;
    }

    private WhatsAppMessage saveMessage(WhatsAppMessage message) {
        var saved = messages.save(message);
        var conversation = saved.getConversation();
        if (conversation != null) {
            events.publishEvent(new WhatsAppInboxChanged(conversation.getTenantId(), conversation.getId()));
        }
        return saved;
    }

    /** A message as the inbox shows it, with the team member's current name when they have set one. */
    private MessageResponse response(WhatsAppMessage message) {
        var userId = message.getSentByUserId();
        var name = userId == null ? null : accounts.displayNames(List.of(userId)).get(userId);
        return MessageResponse.from(message, name);
    }

    /**
     * One page of the inbox, newest first.
     *
     * @param view   "open" (the default), "attention" (open and waiting on the team) or "closed"
     * @param search matches the customer's name or number, or the last message; blank for everything
     * @param cursor the previous page's nextCursor, or blank for the first page
     */
    @Transactional(readOnly = true)
    public ConversationPage page(UUID tenantId, String view, String search, String cursor, int limit) {
        var size = Math.max(1, Math.min(MAX_PAGE_SIZE, limit));
        var normalizedView = view == null ? "" : view.trim().toLowerCase(Locale.ROOT);
        var after = Cursor.parse(cursor);
        var rows = conversations.findInboxPage(tenantId, "closed".equals(normalizedView),
                "attention".equals(normalizedView), likePattern(search), after.at(), after.id(),
                PageRequest.of(0, size + 1));
        var items = rows.stream().limit(size).map(ConversationResponse::from).toList();
        String next = null;
        if (rows.size() > size) {
            var last = items.get(size - 1);
            next = new Cursor(last.lastMessageAt(), last.id()).encode();
        }
        return new ConversationPage(items, next);
    }

    /** Conversations in any view that changed after {@code since}, so the inbox can refresh without reloading. */
    @Transactional(readOnly = true)
    public ConversationChanges changes(UUID tenantId, OffsetDateTime since) {
        var now = OffsetDateTime.now();
        var from = since == null ? now.minus(CHANGES_OVERLAP) : since;
        var items = conversations.findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(
                tenantId, from, PageRequest.of(0, CHANGES_BATCH)).stream().map(ConversationResponse::from).toList();
        // A full batch may have more behind it: carry on from the last one returned.
        var next = items.size() == CHANGES_BATCH ? items.get(items.size() - 1).updatedAt() : now.minus(CHANGES_OVERLAP);
        return new ConversationChanges(items, next);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> messages(UUID tenantId, UUID conversationId) {
        return messages(tenantId, conversationId, null);
    }

    /** The thread, or with {@code since}, only messages added or changed (delivered, read, transcribed) after it. */
    @Transactional(readOnly = true)
    public List<MessageResponse> messages(UUID tenantId, UUID conversationId, OffsetDateTime since) {
        return messageResponses(tenantId, conversationId, since);
    }

    private List<MessageResponse> messageResponses(UUID tenantId, UUID conversationId, OffsetDateTime since) {
        requireConversation(tenantId, conversationId);
        var rows = since == null
                ? messages.findAllByConversation_IdAndTenantIdOrderByCreatedAtAsc(conversationId, tenantId)
                : messages.findAllByConversation_IdAndTenantIdAndUpdatedAtAfterOrderByCreatedAtAsc(
                        conversationId, tenantId, since);
        var names = accounts.displayNames(rows.stream().map(WhatsAppMessage::getSentByUserId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        return rows.stream().map(row -> MessageResponse.from(row,
                row.getSentByUserId() == null ? null : names.get(row.getSentByUserId()))).toList();
    }

    /**
     * Closes the conversation. When its call record is still open, returns it so the caller can end it
     * now (its summary and follow-ups run) instead of 24 hours after the customer's last message.
     */
    @Transactional
    public Closed close(UUID tenantId, UUID conversationId) {
        return close(tenantId, conversationId, WhatsAppConversation.CLOSED_BY_TEAM);
    }

    /** @param reason {@link WhatsAppConversation#CLOSED_BY_TEAM} or {@link WhatsAppConversation#CLOSED_INACTIVE} */
    @Transactional
    public Closed close(UUID tenantId, UUID conversationId, String reason) {
        var conversation = requireConversation(tenantId, conversationId);
        conversation.close(reason);
        var saved = saveConversation(conversation);
        var session = saved.isSessionOpen()
                ? new QuietSession(saved.getId(), saved.getAgentId(), saved.getCustomerNumber())
                : null;
        return new Closed(ConversationResponse.from(saved), session);
    }

    /** Open conversations with no message since the cutoff that are not waiting on the team. */
    @Transactional(readOnly = true)
    public List<InactiveConversation> inactiveOpen(OffsetDateTime cutoff, int limit) {
        return conversations.findInactiveOpen(cutoff, PageRequest.of(0, limit)).stream()
                .map(item -> new InactiveConversation(item.getTenantId(), item.getId())).toList();
    }

    @Transactional
    public ConversationResponse reopen(UUID tenantId, UUID conversationId) {
        var conversation = requireConversation(tenantId, conversationId);
        conversation.reopen();
        return ConversationResponse.from(saveConversation(conversation));
    }

    @Transactional(readOnly = true)
    public ConversationResponse conversation(UUID tenantId, UUID conversationId) {
        return ConversationResponse.from(requireConversation(tenantId, conversationId));
    }

    /** The approved templates of the WhatsApp number this conversation is on. */
    public List<TemplateResponse> templates(UUID tenantId, UUID conversationId) {
        return approvedTemplates(requireConversation(tenantId, conversationId)).stream()
                .map(WhatsAppInboxService::templateResponse).toList();
    }

    /**
     * Sends an approved template from the team. WhatsApp accepts templates after the 24-hour window has
     * closed. It counts as one message, and like other staff replies it is not held back when the
     * workspace's message allowance is used up.
     *
     * @param values the text for each placeholder, keyed by {@link TemplateField#key()}
     */
    public MessageResponse sendTemplate(UUID tenantId, UUID conversationId, StaffSender by,
                                        String name, String language, Map<String, String> values) {
        provisioningPolicies.authorize(tenantId, "whatsapp");
        var conversation = requireConversation(tenantId, conversationId);
        if (!"human".equals(conversation.getMode())) {
            throw new IllegalStateException("Take over this conversation before sending a template");
        }
        var template = approvedTemplates(conversation).stream()
                .filter(item -> item.name().equals(name) && item.language().equals(language))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "That template isn't approved for this WhatsApp number. Refresh the list and try again."));
        var filled = new LinkedHashMap<String, String>();
        for (var field : template.parameters()) {
            var value = values == null ? null : values.get(field.key());
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Fill in " + field.placeholder() + " in the template " + field.component()
                        + " before sending.");
            }
            filled.put(field.key(), value.trim());
        }
        var text = renderTemplate(template, filled);
        var pending = saveMessage(WhatsAppMessage.staff(conversation, "template", text, by, template.name()));
        try {
            var result = sender.sendTemplateTracked(conversation.getPhoneNumberId(),
                    conversation.getCustomerNumber(), templatePayload(template, filled), token(conversation));
            pending.providerAccepted(result.providerMessageId());
            usageMetering.meterOutboundMessage(tenantId, conversation.getAgentId(), "whatsapp",
                    result.providerMessageId(), pending.getId().toString(), "template");
            conversation.sent(text, OffsetDateTime.now());
            conversation.answered();
            saveConversation(conversation);
            return response(saveMessage(pending));
        } catch (RuntimeException exception) {
            pending.status("failed", safeFailure(exception));
            saveMessage(pending);
            throw exception;
        }
    }

    private List<TemplateOption> approvedTemplates(WhatsAppConversation conversation) {
        var runtime = integrations.runtime(conversation.getTenantId(), conversation.getAgentId(), "whatsapp");
        return templateCatalog.templates(conversation.getTenantId(), runtime.connectionId());
    }

    private static TemplateResponse templateResponse(TemplateOption template) {
        return new TemplateResponse(template.name(), template.language(), template.category(),
                template.headerText(), template.bodyText(), template.parameters().stream()
                .map(field -> new TemplateField(field.key(), field.component(), field.name(), field.placeholder()))
                .toList());
    }

    /** What the customer reads: the header and body with each placeholder filled in. */
    static String renderTemplate(TemplateOption template, Map<String, String> values) {
        var header = fill(template.headerText(), "header", template, values);
        var body = fill(template.bodyText(), "body", template, values);
        if (header.isBlank()) return body;
        return body.isBlank() ? header : header + "\n\n" + body;
    }

    private static String fill(String text, String component, TemplateOption template, Map<String, String> values) {
        var result = text == null ? "" : text;
        for (var field : template.parameters()) {
            if (component.equals(field.component()) && values.containsKey(field.key())) {
                result = result.replace(field.placeholder(), values.get(field.key()));
            }
        }
        return result.trim();
    }

    /** The Graph API template object: header parameters first, then body, in placeholder order. */
    static Map<String, Object> templatePayload(TemplateOption template, Map<String, String> values) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", template.name());
        payload.put("language", Map.of("code", template.language()));
        var components = new ArrayList<Map<String, Object>>();
        for (var component : List.of("header", "body")) {
            var parameters = new ArrayList<Map<String, Object>>();
            template.parameters().stream()
                    .filter(field -> component.equals(field.component()))
                    .sorted(java.util.Comparator.comparingInt(
                            WhatsAppEmbeddedSignupService.TemplateParameter::position))
                    .forEach(field -> {
                        var parameter = new LinkedHashMap<String, Object>();
                        parameter.put("type", "text");
                        parameter.put("text", values.get(field.key()));
                        if ("NAMED".equalsIgnoreCase(template.parameterFormat())) {
                            parameter.put("parameter_name", field.name());
                        }
                        parameters.add(parameter);
                    });
            if (!parameters.isEmpty()) components.add(Map.of("type", component, "parameters", parameters));
        }
        if (!components.isEmpty()) payload.put("components", components);
        return payload;
    }

    @Transactional(readOnly = true)
    public MediaContent media(UUID tenantId, UUID messageId) {
        var message = messages.findByIdAndTenantId(messageId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("WhatsApp message not found"));
        if (message.getMediaId() == null) throw new IllegalArgumentException("This message has no downloadable media");
        var media = sender.downloadMedia(message.getMediaId(), token(message.getConversation()));
        return new MediaContent(media.bytes(), media.contentType(), downloadName(message));
    }

    @Transactional
    public InboundResult recordInbound(Call call, String providerMessageId, String customerName,
                                       String type, String body, String mediaId, String mediaMimeType) {
        if (messages.existsByProviderMessageId(providerMessageId)) {
            var existing = messages.findByProviderMessageId(providerMessageId).orElseThrow();
            return new InboundResult(existing.getConversation().getId(), existing.getConversation().getMode(), false);
        }
        var conversation = conversations.findByAgentIdAndCustomerNumber(
                        call.getAgent().getId(), call.getCallerNumber())
                .orElseGet(() -> new WhatsAppConversation(
                        call.getTenant().getId(), call.getAgent().getId(),
                        call.getAgent().getWhatsappPhoneNumberId(), call.getCallerNumber(), customerName));
        var preview = preview(type, body);
        conversation.receive(customerName, preview, OffsetDateTime.now());
        conversation = saveConversation(conversation);
        saveMessage(new WhatsAppMessage(conversation, providerMessageId, "inbound", type,
                body, mediaId, mediaMimeType, "received"));
        return new InboundResult(conversation.getId(), conversation.getMode(), true);
    }

    /** When this customer last wrote to this business number, before the message being handled now. */
    @Transactional(readOnly = true)
    public Optional<OffsetDateTime> lastCustomerMessageAt(String phoneNumberId, String customerNumber) {
        return conversations.findFirstByPhoneNumberIdAndCustomerNumberOrderByLastMessageAtDesc(
                        phoneNumberId, customerNumber)
                .map(WhatsAppConversation::getLastCustomerMessageAt);
    }

    /** A voice note kept before it was transcribed gains its words, in the thread and the list preview. */
    @Transactional
    public void transcribed(String providerMessageId, String transcript) {
        if (transcript == null || transcript.isBlank()) return;
        messages.findByProviderMessageId(providerMessageId).ifPresent(message -> {
            message.transcribed(transcript);
            saveMessage(message);
            var conversation = message.getConversation();
            if (preview("audio", "").equals(conversation.getLastMessagePreview())) {
                conversation.previewTranscript(transcript);
                saveConversation(conversation);
            }
        });
    }

    /** Marks the conversation holding this customer message as waiting on the team. */
    @Transactional
    public void needsAttention(String providerMessageId, String reason) {
        messages.findByProviderMessageId(providerMessageId).ifPresent(message -> {
            var conversation = message.getConversation();
            conversation.needsAttention(reason);
            saveConversation(conversation);
        });
    }

    /**
     * Whether the customer message has already been answered, by the agent or the team, so a message
     * processed again after a failure is not answered twice.
     */
    @Transactional(readOnly = true)
    public boolean alreadyAnswered(String providerMessageId) {
        return messages.findByProviderMessageId(providerMessageId)
                .filter(message -> message.getCreatedAt() != null)
                .map(message -> messages.existsByConversation_IdAndDirectionAndStatusNotAndCreatedAtAfter(
                        message.getConversation().getId(), "outbound", "failed", message.getCreatedAt()))
                .orElse(false);
    }

    /** Conversations whose customer has been quiet since the cutoff while their call record is open. */
    @Transactional(readOnly = true)
    public List<QuietSession> quietOpenSessions(OffsetDateTime cutoff, int limit) {
        return conversations.findQuietOpenSessions(cutoff, PageRequest.of(0, limit)).stream()
                .map(item -> new QuietSession(item.getId(), item.getAgentId(), item.getCustomerNumber()))
                .toList();
    }

    @Transactional
    public void sessionEnded(UUID conversationId) {
        conversations.findById(conversationId).ifPresent(conversation -> {
            conversation.sessionEnded();
            saveConversation(conversation);
        });
    }

    public MessageResponse sendAiText(Call call, UUID conversationId, String text) {
        return send(call.getTenant().getId(), conversationId, text, null);
    }

    public MessageResponse sendAiVoice(Call call, UUID conversationId, String text, byte[] oggOpus) {
        provisioningPolicies.authorize(call.getTenant().getId(), "whatsapp");
        var conversation = requireConversation(call.getTenant().getId(), conversationId);
        var pending = saveMessage(WhatsAppMessage.outbound(conversation, "ai", "audio", text, "audio/ogg"));
        try {
            var result = sender.sendVoiceNoteTracked(conversation.getPhoneNumberId(),
                    conversation.getCustomerNumber(), oggOpus, token(conversation));
            pending.providerAccepted(result.providerMessageId());
            usageMetering.meterOutboundMessage(
                    call.getTenant().getId(), call.getAgent().getId(), "whatsapp",
                    result.providerMessageId(), pending.getId().toString(), "audio");
            conversation.sent("Voice reply", OffsetDateTime.now());
            conversation.answered();
            markProviderRead(conversation);
            return response(saveMessage(pending));
        } catch (RuntimeException exception) {
            pending.status("failed", safeFailure(exception));
            saveMessage(pending);
            throw exception;
        }
    }

    public MessageResponse sendHuman(UUID tenantId, UUID conversationId, String text) {
        return sendHuman(tenantId, conversationId, text, null);
    }

    public MessageResponse sendHuman(UUID tenantId, UUID conversationId, String text, StaffSender by) {
        var conversation = requireConversation(tenantId, conversationId);
        if (!"human".equals(conversation.getMode())) {
            throw new IllegalStateException("Take over this conversation before sending a human reply");
        }
        if (!conversation.replyWindowOpen(OffsetDateTime.now())) {
            throw new IllegalStateException(
                    "WhatsApp only allows a free reply within 24 hours of the customer's last message. "
                            + "You can reply again when the customer writes.");
        }
        return send(tenantId, conversationId, text, by == null ? new StaffSender(null, null) : by);
    }

    /** Sends a text reply from the agent, or from the team when {@code staff} is given. */
    private MessageResponse send(UUID tenantId, UUID conversationId, String text, StaffSender staff) {
        var markRead = staff == null;
        var type = "text";
        provisioningPolicies.authorize(tenantId, "whatsapp");
        var conversation = requireConversation(tenantId, conversationId);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Message text is required");
        var pending = saveMessage(markRead
                ? WhatsAppMessage.outbound(conversation, "ai", type, text.trim(), null)
                : WhatsAppMessage.staff(conversation, type, text.trim(), staff, null));
        try {
            var result = sender.sendTextTracked(conversation.getPhoneNumberId(),
                    conversation.getCustomerNumber(), text.trim(), token(conversation));
            pending.providerAccepted(result.providerMessageId());
            usageMetering.meterOutboundMessage(
                    tenantId, conversation.getAgentId(), "whatsapp", result.providerMessageId(),
                    pending.getId().toString(), type);
            conversation.sent(text, OffsetDateTime.now());
            conversation.answered();
            saveConversation(conversation);
            if (markRead) markProviderRead(conversation);
            return response(saveMessage(pending));
        } catch (RuntimeException exception) {
            pending.status("failed", safeFailure(exception));
            saveMessage(pending);
            throw exception;
        }
    }

    @Transactional
    public ConversationResponse assign(UUID tenantId, UUID conversationId, String mode) {
        var conversation = requireConversation(tenantId, conversationId);
        conversation.assign(mode == null ? "" : mode.trim().toLowerCase(java.util.Locale.ROOT));
        if ("human".equals(conversation.getMode())) markProviderRead(conversation);
        return ConversationResponse.from(saveConversation(conversation));
    }

    @Transactional
    public ConversationResponse markRead(UUID tenantId, UUID conversationId) {
        var conversation = requireConversation(tenantId, conversationId);
        markProviderRead(conversation);
        return ConversationResponse.from(saveConversation(conversation));
    }

    @Transactional
    public void providerStatus(String providerMessageId, String status, String failureReason) {
        if (providerMessageId == null || providerMessageId.isBlank()) return;
        messages.findByProviderMessageId(providerMessageId).ifPresent(message -> {
            message.status(normalizeStatus(status), failureReason);
            saveMessage(message);
        });
    }

    private void markProviderRead(WhatsAppConversation conversation) {
        conversation.markRead();
        saveConversation(conversation);
        var latest = messages.findFirstByConversation_IdAndDirectionOrderByCreatedAtDesc(
                conversation.getId(), "inbound").orElse(null);
        if (latest != null && latest.getProviderMessageId() != null) {
            try {
                sender.markRead(conversation.getPhoneNumberId(), latest.getProviderMessageId(), token(conversation));
                latest.status("read", null);
                saveMessage(latest);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not send WhatsApp read receipt conversationId={}", conversation.getId(), exception);
            }
        }
    }

    private String token(WhatsAppConversation conversation) {
        return String.valueOf(integrations.runtime(conversation.getTenantId(), conversation.getAgentId(), "whatsapp")
                .credentials().getOrDefault("accessToken", ""));
    }

    private WhatsAppConversation requireConversation(UUID tenantId, UUID id) {
        return conversations.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("WhatsApp conversation not found"));
    }

    private static String preview(String type, String body) {
        if (body != null && !body.isBlank()) return body.trim();
        return switch (type == null ? "" : type) {
            case "audio" -> "Voice note";
            case "image" -> "Image";
            case "video" -> "Video";
            case "document" -> "Document";
            case "location" -> "Location";
            case "contacts" -> "Contact";
            default -> "WhatsApp message";
        };
    }

    private static String normalizeStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase(java.util.Locale.ROOT)) {
            case "sent", "delivered", "read", "failed" -> status.toLowerCase(java.util.Locale.ROOT);
            default -> "sent";
        };
    }
    private static String safeFailure(RuntimeException exception) {
        var message = exception.getMessage() == null ? "WhatsApp delivery failed" : exception.getMessage();
        return message.substring(0, Math.min(1000, message.length()));
    }

    public record InboundResult(UUID conversationId, String mode, boolean created) { }
    public record QuietSession(UUID conversationId, UUID agentId, String customerNumber) { }
    /** A closed conversation, and its call record when that still has to be ended. */
    public record Closed(ConversationResponse conversation, QuietSession openSession) { }
    public record InactiveConversation(UUID tenantId, UUID conversationId) { }

    /** Where a page of the inbox ends: the last row's message time and id. */
    record Cursor(OffsetDateTime at, UUID id) {
        static Cursor parse(String value) {
            if (value == null || value.isBlank()) return new Cursor(FIRST_PAGE_AT, FIRST_PAGE_ID);
            try {
                var decoded = new String(Base64.getUrlDecoder().decode(value.trim()), StandardCharsets.UTF_8);
                var split = decoded.lastIndexOf('|');
                return new Cursor(OffsetDateTime.parse(decoded.substring(0, split)),
                        UUID.fromString(decoded.substring(split + 1)));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("That page of the inbox is no longer valid. Reload and try again.");
            }
        }

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** A lower-case LIKE pattern for the search box; numbers match with or without spaces and "+". */
    static String likePattern(String search) {
        var value = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "%";
        var digits = value.replaceAll("[\\s+()-]", "");
        var term = digits.matches("\\d+") ? digits : value;
        return "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    public record MediaContent(byte[] bytes, String contentType, String fileName) { }

    private static String downloadName(WhatsAppMessage message) {
        var extension = switch (message.getMessageType()) {
            case "image", "sticker" -> ".jpg";
            case "video" -> ".mp4";
            case "audio" -> ".ogg";
            default -> "";
        };
        return "whatsapp-" + message.getId() + extension;
    }
}
