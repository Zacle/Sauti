package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.whatsapp.WhatsAppInboxDtos.AssignmentRequest;
import com.sauti.whatsapp.WhatsAppInboxDtos.ConversationResponse;
import com.sauti.whatsapp.WhatsAppInboxDtos.MessageResponse;
import com.sauti.whatsapp.WhatsAppInboxDtos.SendMessageRequest;
import com.sauti.whatsapp.WhatsAppInboxService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/whatsapp/conversations")
public class WhatsAppInboxController {
    private final WhatsAppInboxService inbox;

    public WhatsAppInboxController(WhatsAppInboxService inbox) { this.inbox = inbox; }

    @GetMapping
    List<ConversationResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return inbox.list(user.tenantId());
    }

    @GetMapping("/{id}/messages")
    List<MessageResponse> messages(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return inbox.messages(user.tenantId(), id);
    }

    @PostMapping("/{id}/messages")
    MessageResponse send(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                         @RequestBody SendMessageRequest request) {
        return inbox.sendHuman(user.tenantId(), id, request.text());
    }

    @PutMapping("/{id}/assignment")
    ConversationResponse assign(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                @RequestBody AssignmentRequest request) {
        return inbox.assign(user.tenantId(), id, request.mode());
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
}
