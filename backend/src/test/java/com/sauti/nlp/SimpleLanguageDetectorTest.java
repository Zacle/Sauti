package com.sauti.nlp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleLanguageDetectorTest {
    private final SimpleLanguageDetector detector = new SimpleLanguageDetector();

    @Test
    void detectsSwahiliWhenSupported() {
        assertThat(detector.detect("Habari, nataka miadi kesho", "en", List.of("en", "sw")))
                .isEqualTo("sw");
    }

    @Test
    void detectsArabicUnicodeWhenSupported() {
        assertThat(detector.detect("مرحبا، أريد موعدا غدا", "en", List.of("en", "ar")))
                .isEqualTo("ar");
    }

    @Test
    void fallsBackToAgentDefaultInsteadOfEnglish() {
        assertThat(detector.detect("Jambo", "sw", List.of("sw", "en")))
                .isEqualTo("sw");
    }

    @Test
    void detectsNaturalFrenchAgreementWithoutGreetingKeywords() {
        assertThat(detector.detect("Exactement, absolument, c'est bien sûr", "en", List.of("en", "fr")))
                .isEqualTo("fr");
    }

    @Test
    void preservesCurrentLanguageForAnUnrecognizedShortAnswer() {
        assertThat(detector.detect("D'accord", "fr", List.of("en", "fr")))
                .isEqualTo("fr");
    }

    @Test
    void detectsShortEnglishGreetingWhenSupported() {
        assertThat(detector.detect("Hi", "fr", List.of("en", "fr")))
                .isEqualTo("en");
    }
}
