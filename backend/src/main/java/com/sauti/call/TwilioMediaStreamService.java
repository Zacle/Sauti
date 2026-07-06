package com.sauti.call;

import java.util.Map;

public interface TwilioMediaStreamService {
    void start(
            String callSid,
            String streamSid,
            TwilioMediaFormat mediaFormat,
            Map<String, String> parameters,
            TwilioOutboundMediaSender outboundMediaSender
    );

    void acceptInboundAudio(
            String callSid,
            String streamSid,
            String sequenceNumber,
            String chunk,
            String timestamp,
            byte[] mulawAudio
    );

    void markReceived(String callSid, String streamSid, String markName);

    default void acceptDtmf(String callSid, String digit) {
    }

    void stop(String callSid, String streamSid);
}
