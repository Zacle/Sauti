package com.sauti.call;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SentenceChunker {
    private static final int MIN_CLAUSE_LENGTH = 72;
    private static final int TARGET_CHUNK_LENGTH = 140;
    private static final Set<String> ABBREVIATIONS = Set.of(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr",
            "m", "mme", "mlle", "no", "tel", "tél", "st", "am", "pm",
            "eg", "ie", "us", "uk", "inc", "ltd"
    );

    public List<String> chunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var chunks = new ArrayList<String>();
        var buffer = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            var current = text.charAt(i);
            buffer.append(current);
            if (isSentenceBoundary(text, i, true)
                    || isNaturalClauseBoundary(current, buffer.length())
                    || isSafeLengthBoundary(current, buffer.length())) {
                addChunk(chunks, buffer);
            }
        }
        addChunk(chunks, buffer);
        return chunks;
    }

    static boolean isSentenceBoundary(CharSequence text, int index, boolean complete) {
        var current = text.charAt(index);
        if (current == '\n') {
            return true;
        }
        if (current != '.' && current != '?' && current != '!' && current != '…') {
            return false;
        }
        if (current == '.' && isAbbreviation(text, index)) {
            return false;
        }
        if (index == text.length() - 1) {
            return complete;
        }
        return Character.isWhitespace(text.charAt(index + 1));
    }

    private boolean isNaturalClauseBoundary(char current, int length) {
        return (length >= MIN_CLAUSE_LENGTH && (current == ';' || current == ':' || current == '—'))
                || (length >= TARGET_CHUNK_LENGTH && current == ',');
    }

    private boolean isSafeLengthBoundary(char current, int length) {
        return length >= TARGET_CHUNK_LENGTH && Character.isWhitespace(current);
    }

    static boolean isAbbreviation(CharSequence text, int periodIndex) {
        var start = periodIndex - 1;
        while (start >= 0 && (Character.isLetter(text.charAt(start)) || text.charAt(start) == '.')) {
            start--;
        }
        if (start == periodIndex - 1) {
            return false;
        }
        var token = text.subSequence(start + 1, periodIndex).toString()
                .replace(".", "")
                .toLowerCase(Locale.ROOT);
        return ABBREVIATIONS.contains(token);
    }

    private void addChunk(List<String> chunks, StringBuilder buffer) {
        var chunk = buffer.toString().trim();
        if (!chunk.isBlank()) {
            chunks.add(chunk + " ");
        }
        buffer.setLength(0);
    }
}
