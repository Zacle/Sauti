package com.sauti.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonNameNormalizerTest {
    @Test
    void normalizesUnicodeCanonicallyWithoutTranslatingOrLatinizing() {
        assertThat(PersonNameNormalizer.normalize("  Jose\u0301   Ng\u0169g\u0129  "))
                .isEqualTo("Jos\u00E9 Ng\u0169g\u0129");
        assertThat(PersonNameNormalizer.normalize("\u0645\u064f\u062d\u064e\u0645\u064e\u0651\u062f"))
                .isEqualTo("\u0645\u064f\u062d\u064e\u0645\u064e\u0651\u062f");
        assertThat(PersonNameNormalizer.normalize("\u5C71\u7530\u30FB\u592A\u90CE"))
                .isEqualTo("\u5C71\u7530\u30FB\u592A\u90CE");
    }

    @Test
    void rejectsValuesThatAreNotStructurallyPersonNames() {
        assertThat(PersonNameNormalizer.normalize("")).isEmpty();
        assertThat(PersonNameNormalizer.normalize("12345")).isEmpty();
        assertThat(PersonNameNormalizer.normalize("Zacari@example.com")).isEmpty();
        assertThat(PersonNameNormalizer.normalize("Zacari \uD83D\uDE80")).isEmpty();
    }
}
