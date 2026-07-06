package com.sauti.call;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.springframework.stereotype.Component;

@Component
public class G711AudioCodecConverter implements AudioCodecConverter {
    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;

    @Override
    public byte[] twilioMulaw8kToPcm16k(byte[] mulawAudio) {
        if (mulawAudio == null || mulawAudio.length == 0) {
            return new byte[0];
        }
        var pcm8k = new short[mulawAudio.length];
        for (int i = 0; i < mulawAudio.length; i++) {
            pcm8k[i] = decodeMulaw(mulawAudio[i]);
        }
        var pcm16k = upsampleLinear(pcm8k);
        return shortsToLittleEndianBytes(pcm16k);
    }

    @Override
    public byte[] pcm16kToTwilioMulaw8k(byte[] pcmAudio) {
        if (pcmAudio == null || pcmAudio.length == 0) {
            return new byte[0];
        }
        var pcm16k = littleEndianBytesToShorts(pcmAudio);
        var pcm8k = downsampleByTwo(pcm16k);
        var mulaw = new byte[pcm8k.length];
        for (int i = 0; i < pcm8k.length; i++) {
            mulaw[i] = encodeMulaw(pcm8k[i]);
        }
        return mulaw;
    }

    @Override
    public byte[] telnyxL16ToPcm16k(byte[] l16Audio) {
        return swap16BitEndianness(l16Audio);
    }

    @Override
    public byte[] pcm16kToTelnyxL16(byte[] pcmAudio) {
        return swap16BitEndianness(pcmAudio);
    }

    private byte[] swap16BitEndianness(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }
        var usableLength = input.length - (input.length % 2);
        var output = new byte[usableLength];
        for (int index = 0; index < usableLength; index += 2) {
            output[index] = input[index + 1];
            output[index + 1] = input[index];
        }
        return output;
    }

    private short[] upsampleLinear(short[] input) {
        var output = new short[input.length * 2];
        for (int i = 0; i < input.length; i++) {
            output[i * 2] = input[i];
            if (i + 1 < input.length) {
                output[i * 2 + 1] = (short) ((input[i] + input[i + 1]) / 2);
            } else {
                output[i * 2 + 1] = input[i];
            }
        }
        return output;
    }

    private short[] downsampleByTwo(short[] input) {
        var output = new short[(input.length + 1) / 2];
        for (int i = 0; i < output.length; i++) {
            int first = input[i * 2];
            int second = i * 2 + 1 < input.length ? input[i * 2 + 1] : first;
            output[i] = (short) ((first + second) / 2);
        }
        return output;
    }

    private byte[] shortsToLittleEndianBytes(short[] samples) {
        var buffer = ByteBuffer.allocate(samples.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    private short[] littleEndianBytesToShorts(byte[] bytes) {
        var usableLength = bytes.length - (bytes.length % Short.BYTES);
        var buffer = ByteBuffer.wrap(bytes, 0, usableLength).order(ByteOrder.LITTLE_ENDIAN);
        var samples = new short[usableLength / Short.BYTES];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort();
        }
        return samples;
    }

    private short decodeMulaw(byte value) {
        int mulaw = ~value & 0xff;
        int sign = mulaw & 0x80;
        int exponent = (mulaw >> 4) & 0x07;
        int mantissa = mulaw & 0x0f;
        int sample = ((mantissa << 3) + BIAS) << exponent;
        sample -= BIAS;
        return (short) (sign == 0 ? sample : -sample);
    }

    private byte encodeMulaw(short sampleValue) {
        int sample = sampleValue;
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) {
            sample = -sample;
        }
        if (sample > CLIP) {
            sample = CLIP;
        }
        sample += BIAS;

        int exponent = 7;
        for (int mask = 0x4000; (sample & mask) == 0 && exponent > 0; mask >>= 1) {
            exponent--;
        }
        int mantissa = (sample >> (exponent + 3)) & 0x0f;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }
}
