package com.sauti.call;

public interface AudioCodecConverter {
    byte[] twilioMulaw8kToPcm16k(byte[] mulawAudio);

    byte[] pcm16kToTwilioMulaw8k(byte[] pcmAudio);

    byte[] telnyxL16ToPcm16k(byte[] l16Audio);

    byte[] pcm16kToTelnyxL16(byte[] pcmAudio);
}
