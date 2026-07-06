package com.sauti.call;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;

final class WebVoiceRecordingWriter {
    private static final int SAMPLE_RATE = 16_000;
    private static final int WAV_HEADER_BYTES = 44;
    private final Path target;
    private final BufferedOutputStream output;
    private final ArrayDeque<Short> pendingAgentSamples = new ArrayDeque<>();
    private long sampleCount;
    private boolean finished;

    WebVoiceRecordingWriter(Path target) {
        this.target = target;
        try {
            Files.createDirectories(target.getParent());
            output = new BufferedOutputStream(Files.newOutputStream(
                    target,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            ));
            output.write(new byte[WAV_HEADER_BYTES]);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start the Web Voice recording", exception);
        }
    }

    synchronized void appendCaller(byte[] pcm16LittleEndian) {
        if (finished || pcm16LittleEndian == null) return;
        try {
            for (int index = 0; index + 1 < pcm16LittleEndian.length; index += 2) {
                int caller = sample(pcm16LittleEndian, index);
                int agent = pendingAgentSamples.isEmpty() ? 0 : pendingAgentSamples.removeFirst();
                writeSample(clamp(caller + agent));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write caller audio", exception);
        }
    }

    synchronized void appendAgent(byte[] pcm16LittleEndian) {
        if (finished || pcm16LittleEndian == null) return;
        for (int index = 0; index + 1 < pcm16LittleEndian.length; index += 2) {
            pendingAgentSamples.addLast((short) sample(pcm16LittleEndian, index));
        }
    }

    synchronized void clearPendingAgentAudio() {
        pendingAgentSamples.clear();
    }

    synchronized Path finish() {
        if (finished) return target;
        try {
            while (!pendingAgentSamples.isEmpty()) writeSample(pendingAgentSamples.removeFirst());
            output.flush();
            output.close();
            finished = true;
            writeHeader(target, sampleCount);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to finish the Web Voice recording", exception);
        }
    }

    private int sample(byte[] bytes, int index) {
        return (short) ((bytes[index] & 0xff) | (bytes[index + 1] << 8));
    }

    private int clamp(int value) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    private void writeSample(int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        sampleCount++;
    }

    private void writeHeader(Path path, long samples) throws IOException {
        long dataBytes = samples * 2;
        try (var file = new RandomAccessFile(path.toFile(), "rw")) {
            file.seek(0);
            file.writeBytes("RIFF");
            writeLittleEndian(file, 36 + dataBytes, 4);
            file.writeBytes("WAVEfmt ");
            writeLittleEndian(file, 16, 4);
            writeLittleEndian(file, 1, 2);
            writeLittleEndian(file, 1, 2);
            writeLittleEndian(file, SAMPLE_RATE, 4);
            writeLittleEndian(file, SAMPLE_RATE * 2L, 4);
            writeLittleEndian(file, 2, 2);
            writeLittleEndian(file, 16, 2);
            file.writeBytes("data");
            writeLittleEndian(file, dataBytes, 4);
        }
    }

    private void writeLittleEndian(RandomAccessFile file, long value, int bytes) throws IOException {
        for (int index = 0; index < bytes; index++) file.write((int) ((value >>> (index * 8)) & 0xff));
    }
}
