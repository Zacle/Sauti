package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class G711AudioCodecConverterTest {
    private final G711AudioCodecConverter converter = new G711AudioCodecConverter();

    @Test
    void convertsTwilioMulaw8kToPcm16k() {
        var pcm = converter.twilioMulaw8kToPcm16k(new byte[160]);

        assertThat(pcm).hasSize(640);
    }

    @Test
    void convertsPcm16kToTwilioMulaw8k() {
        var pcm = new byte[640];

        var mulaw = converter.pcm16kToTwilioMulaw8k(pcm);

        assertThat(mulaw).hasSize(160);
    }

    @Test
    void handlesEmptyAudio() {
        assertThat(converter.twilioMulaw8kToPcm16k(new byte[0])).isEmpty();
        assertThat(converter.pcm16kToTwilioMulaw8k(new byte[0])).isEmpty();
        assertThat(converter.telnyxL16ToPcm16k(new byte[0])).isEmpty();
        assertThat(converter.pcm16kToTelnyxL16(new byte[0])).isEmpty();
    }

    @Test
    void convertsTelnyxNetworkOrderL16ToLittleEndianPcmAndBack() {
        var telnyxL16 = new byte[] {0x12, 0x34, (byte) 0xab, (byte) 0xcd};

        var pcm = converter.telnyxL16ToPcm16k(telnyxL16);

        assertThat(pcm).containsExactly(0x34, 0x12, (byte) 0xcd, (byte) 0xab);
        assertThat(converter.pcm16kToTelnyxL16(pcm)).containsExactly(telnyxL16);
    }

    @Test
    void decodesKnownMulawValues() {
        assertThat(firstPcmSample((byte) 0xff)).isZero();
        assertThat(firstPcmSample((byte) 0x7f)).isZero();
        assertThat(firstPcmSample((byte) 0x80)).isEqualTo((short) 32124);
        assertThat(firstPcmSample((byte) 0x00)).isEqualTo((short) -32124);
    }

    @Test
    void roundTripsAllMulawValuesExceptNegativeZero() {
        for (int value = 0; value <= 255; value++) {
            byte mulaw = (byte) value;
            byte[] pcm = converter.twilioMulaw8kToPcm16k(new byte[] {mulaw});
            byte[] encoded = converter.pcm16kToTwilioMulaw8k(pcm);
            if ((mulaw & 0xff) == 0x7f) {
                assertThat(encoded[0] & 0xff).isEqualTo(0xff);
            } else {
                assertThat(encoded[0]).isEqualTo(mulaw);
            }
        }
    }

    private short firstPcmSample(byte mulaw) {
        var pcm = converter.twilioMulaw8kToPcm16k(new byte[] {mulaw});
        return ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }
}
