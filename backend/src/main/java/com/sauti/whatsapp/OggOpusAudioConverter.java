package com.sauti.whatsapp;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OggOpusAudioConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OggOpusAudioConverter.class);
    private final String ffmpegExecutable;

    public OggOpusAudioConverter(@Value("${sauti.whatsapp.ffmpeg-executable:ffmpeg}") String ffmpegExecutable) {
        this.ffmpegExecutable = ffmpegExecutable;
    }

    @PostConstruct
    void probeFfmpeg() {
        Process process = null;
        try {
            process = new ProcessBuilder(ffmpegExecutable, "-version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOGGER.error("ffmpeg startup probe timed out. WhatsApp voice-note replies will fail.");
                return;
            }
            if (process.exitValue() != 0) {
                var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                LOGGER.error("ffmpeg startup probe failed with exit code {}. WhatsApp voice-note replies will fail. {}",
                        process.exitValue(), output.trim());
            } else {
                LOGGER.info("ffmpeg is available for WhatsApp voice-note conversion");
            }
        } catch (Exception exception) {
            LOGGER.error("ffmpeg executable '{}' is unavailable. Install ffmpeg or set "
                    + "sauti.whatsapp.ffmpeg-executable; WhatsApp voice-note replies will fail.",
                    ffmpegExecutable, exception);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    public byte[] fromMp3(byte[] mp3Audio) {
        if (mp3Audio == null || mp3Audio.length == 0) {
            throw new IllegalArgumentException("TTS returned no audio");
        }
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("sauti-whatsapp-", ".mp3");
            output = Files.createTempFile("sauti-whatsapp-", ".ogg");
            Files.write(input, mp3Audio);
            var process = new ProcessBuilder(
                    ffmpegExecutable,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    "-i", input.toString(),
                    "-vn",
                    "-c:a", "libopus",
                    "-b:a", "32k",
                    "-vbr", "on",
                    "-application", "voip",
                    output.toString()
            ).redirectErrorStream(true).start();
            if (!process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("WhatsApp audio conversion timed out");
            }
            var diagnostics = process.getInputStream().readAllBytes();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("WhatsApp audio conversion failed: "
                        + new String(diagnostics, java.nio.charset.StandardCharsets.UTF_8));
            }
            return Files.readAllBytes(output);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp audio conversion was interrupted", exception);
        } catch (Exception exception) {
            throw exception instanceof IllegalStateException state
                    ? state
                    : new IllegalStateException("Unable to convert WhatsApp audio to OGG Opus", exception);
        } finally {
            delete(input);
            delete(output);
        }
    }

    private void delete(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
