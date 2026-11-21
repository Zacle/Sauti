package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** How the caller's speech is heard: when a pause ends their turn, and words to listen out for. */
@Embeddable
public class SpeechRecognition {
    @Column(name = "stt_endpointing_ms", nullable = false)
    private int endpointingMs = 300;

    @Column(name = "stt_vocabulary_domain")
    private String vocabularyDomain;

    @Column(name = "stt_boosted_keywords")
    private String boostedKeywords;

    protected SpeechRecognition() {
    }

    static SpeechRecognition defaults() {
        return new SpeechRecognition();
    }

    void configure(Integer endpointingMs, String vocabularyDomain, String boostedKeywords) {
        this.endpointingMs = Ranges.clamp(endpointingMs, 100, 3000, 300);
        this.vocabularyDomain = vocabularyDomain;
        this.boostedKeywords = boostedKeywords;
    }

    public int getEndpointingMs() { return endpointingMs; }
    public String getVocabularyDomain() { return vocabularyDomain; }
    public String getBoostedKeywords() { return boostedKeywords; }
}
