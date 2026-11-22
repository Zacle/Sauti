package com.sauti.call;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reads what was said on a phone call answered by the provider's managed assistant. The provider
 * hears the caller and speaks for the agent, so after the call it is the only one holding the
 * conversation.
 */
public interface ProviderConversations {
    Lookup conversation(String providerCallId);

    enum Status {
        /** The conversation was found; {@code messages} may still be empty. */
        FOUND,
        /** Not there yet, or the provider could not be reached: ask again later. */
        NOT_READY,
        /** The provider cannot answer for this call at all (not configured, not its call). */
        UNAVAILABLE
    }

    /** One spoken line. {@code role} is {@code caller} or {@code agent}. */
    record Message(String role, String text, OffsetDateTime sentAt) {
    }

    record Lookup(Status status, List<Message> messages) {
        public static Lookup found(List<Message> messages) { return new Lookup(Status.FOUND, List.copyOf(messages)); }
        public static Lookup notReady() { return new Lookup(Status.NOT_READY, List.of()); }
        public static Lookup unavailable() { return new Lookup(Status.UNAVAILABLE, List.of()); }
    }
}
