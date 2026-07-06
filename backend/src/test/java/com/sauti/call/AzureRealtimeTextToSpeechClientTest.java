package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AzureRealtimeTextToSpeechClientTest {

    @Test
    void wrapsSpeechInConfigurableProsodyAndEscapesCallerContent() {
        var client = new AzureRealtimeTextToSpeechClient("key", "region", "-3%", "+1%");

        var ssml = client.ssml("en-KE-AsiliaNeural", " Oh, I see… A & B < C. ");

        assertThat(ssml)
                .contains("xml:lang=\"en-KE\"")
                .contains("<voice name=\"en-KE-AsiliaNeural\">")
                .contains("<prosody rate=\"-3%\" pitch=\"+1%\">")
                .contains("Oh, I see… A &amp; B &lt; C.")
                .doesNotContain(" A & B < C.");
    }

    @Test
    void rejectsUnsafeProsodyConfiguration() {
        var client = new AzureRealtimeTextToSpeechClient(
                "key",
                "region",
                "\"><break time=\"10s\"/>",
                "fast"
        );

        assertThat(client.ssml("en-KE-AsiliaNeural", "Hello"))
                .contains("<prosody rate=\"-2%\" pitch=\"+0%\">");
    }
}
