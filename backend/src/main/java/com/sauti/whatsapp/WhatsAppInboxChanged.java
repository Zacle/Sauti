package com.sauti.whatsapp;

import java.util.UUID;

/** A WhatsApp conversation or one of its messages was saved. */
public record WhatsAppInboxChanged(UUID tenantId, UUID conversationId) { }
