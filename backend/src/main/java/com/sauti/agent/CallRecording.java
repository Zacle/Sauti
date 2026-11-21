package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** What is kept from a call: the transcript (on unless turned off) and the audio (off unless turned on). */
@Embeddable
public class CallRecording {
    @Column(name = "save_transcript", nullable = false)
    private boolean saveTranscript = true;

    @Column(name = "record_calls", nullable = false)
    private boolean recordCalls = false;

    protected CallRecording() {
    }

    static CallRecording defaults() {
        return new CallRecording();
    }

    void configure(Boolean saveTranscript, Boolean recordCalls) {
        this.saveTranscript = saveTranscript == null || saveTranscript;
        this.recordCalls = recordCalls != null && recordCalls;
    }

    public boolean isSaveTranscript() { return saveTranscript; }
    public boolean isRecordCalls() { return recordCalls; }
}
