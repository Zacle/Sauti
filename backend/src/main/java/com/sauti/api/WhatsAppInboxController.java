package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.whatsapp.WhatsAppChannelService;
import com.sauti.whatsapp.WhatsAppInboxDtos.AssignmentRequest;
import com.sauti.whatsapp.WhatsAppInboxDtos.ConversationChanges;
import com.sauti.whatsapp.WhatsAppInboxDtos.ConversationPage;
import com.sauti.whatsapp.WhatsAppInboxDtos.ConversationResponse;
import com.sauti.whatsapp.WhatsAppInboxDtos.MessageResponse;
import com.sauti.whatsapp.WhatsAppInboxDtos.SendMessageRequest;
import com.sauti.whatsapp.WhatsAppInboxDtos.SendTemplateRequest;
import com.sauti.whatsapp.WhatsAppInboxDtos.TemplateResponse;
import com.sauti.whatsapp.WhatsAppInboxService;
import com.sauti.whatsapp.WhatsAppMessage.StaffSender;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/whatsapp/conversations")
public class WhatsAppInboxController {
    private final WhatsAppInboxService inbox;
    private final WhatsAppChannelService channel;

    public WhatsAppInboxController(WhatsAppInboxService inbox, WhatsAppChannelService channel) {
        this.inbox = inbox;
        this.channel = channel;
    }

    /** One page of the inbox: view is open (default), attention or closed. */
    @GetMapping
    ConversationPage list(@AuthenticationPrincipal AuthenticatedUser user,
                          @RequestParam(defaultValue = "open") String view,
                          @RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "") String cursor,
                          @RequestParam(defaultValue = "30") int limit) {
        return inbox.page(user.tenantId(), view, q, cursor, limit);
    }

    /** Conversations changed since the given time, for refreshing the inbox without reloading it. */
    @GetMapping("/changes")
    ConversationChanges changes(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since) {
        return inbox.changes(user.tenantId(), since);
    }

    /** The thread, or with since, only the messages added or changed after it. */
    @GetMapping("/{id}/messages")
    List<MessageResponse> messages(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since) {
        return inbox.messages(user.tenantId(), id, since);
    }

    @PostMapping("/{id}/messages")
    MessageResponse send(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                         @RequestBody SendMessageRequest request) {
        return inbox.sendHuman(user.tenantId(), id, request.text(), sender(user));
    }

    @GetMapping("/{id}/templates")
    List<TemplateResponse> templates(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return inbox.templates(user.tenantId(), id);
    }

    @PostMapping("/{id}/templates")
    MessageResponse sendTemplate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                 @RequestBody SendTemplateRequest request) {
        return inbox.sendTemplate(user.tenantId(), id, sender(user), request.name(), request.language(),
                request.values());
    }

    @PutMapping("/{id}/assignment")
    ConversationResponse assign(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                @RequestBody AssignmentRequest request) {
        return inbox.assign(user.tenantId(), id, request.mode());
    }

    @PostMapping("/{id}/close")
    ConversationResponse close(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return channel.close(user.tenantId(), id);
    }

    @PostMapping("/{id}/reopen")
    ConversationResponse reopen(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return inbox.reopen(user.tenantId(), id);
    }

    @PostMapping("/{id}/read")
    ConversationResponse markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return inbox.markRead(user.tenantId(), id);
    }

    @GetMapping("/messages/{messageId}/media")
    ResponseEntity<byte[]> media(@AuthenticationPrincipal AuthenticatedUser user,
                                 @PathVariable UUID messageId) {
        var media = inbox.media(user.tenantId(), messageId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + media.fileName().replace("\"", "") + "\"")
                .body(media.bytes());
    }

    /** Who is replying, as shown on the message: the person's email, or the API key's name. */
    private static StaffSender sender(AuthenticatedUser user) {
        var label = user.email() == null ? "" : user.email();
        if (user.isApiKey()) label = "API key " + label.replaceFirst("^api-key:", "");
        return new StaffSender(user.userId(), label);
    }
}
