package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebVoiceRecordingWriterTest {
    @TempDir
    Path directory;

    @Test
    void writesAValidMonoPcmWavAndMixesAgentWithCaller() throws Exception {
        var target = directory.resolve("call.wav");
        var writer = new WebVoiceRecordingWriter(target);
        writer.appendAgent(samples(1000, -1000));
        writer.appendCaller(samples(2000, 2000, 3000));

        writer.finish();

        var wav = Files.readAllBytes(target);
        assertThat(new String(wav, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(wav, 8, 4)).isEqualTo("WAVE");
        assertThat(new String(wav, 36, 4)).isEqualTo("data");
        assertThat(ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()).isEqualTo(16_000);
        assertThat(ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()).isEqualTo(6);
        assertThat(ByteBuffer.wrap(wav, 44, 2).order(ByteOrder.LITTLE_ENDIAN).getShort()).isEqualTo((short) 3000);
        assertThat(ByteBuffer.wrap(wav, 46, 2).order(ByteOrder.LITTLE_ENDIAN).getShort()).isEqualTo((short) 1000);
        assertThat(ByteBuffer.wrap(wav, 48, 2).order(ByteOrder.LITTLE_ENDIAN).getShort()).isEqualTo((short) 3000);
    }

    private byte[] samples(int... values) {
        var buffer = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) buffer.putShort((short) value);
        return buffer.array();
    }
}
