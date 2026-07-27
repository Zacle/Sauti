package com.sauti.session;

import java.text.Normalizer;

/**
 * Language-independent storage normalization for a name entity that has
 * already been extracted semantically from the caller's utterance.
 *
 * <p>This class deliberately does not remove phrases such as "my name is".
 * Determining which words are an introduction is a language-understanding
 * concern. The semantic conversation boundary supplies only the name entity;
 * this boundary preserves its script and diacritics while rejecting values
 * that are not structurally safe person names.</p>
 */
public final class PersonNameNormalizer {
    private static final int MAX_CODE_POINTS = 120;

    private PersonNameNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{Z}\\s]+", " ")
                .strip();
        if (normalized.isBlank()
                || normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            return "";
        }

        var hasLetter = false;
        for (var index = 0; index < normalized.length();) {
            var codePoint = normalized.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                hasLetter = true;
                continue;
            }
            var type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK
                    || type == Character.DASH_PUNCTUATION
                    || codePoint == ' '
                    || codePoint == '\''
                    || codePoint == '\u2019'
                    || codePoint == '.'
                    || codePoint == '\u00B7'
                    || codePoint == '\u30FB') {
                continue;
            }
            return "";
        }
        return hasLetter ? normalized : "";
    }
}
