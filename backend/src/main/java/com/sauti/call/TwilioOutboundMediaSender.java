package com.sauti.call;

public interface TwilioOutboundMediaSender {
    void send(String textFrame);

    default void close() {
    }
}
