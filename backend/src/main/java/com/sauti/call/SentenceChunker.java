package com.sauti.call;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SentenceChunker {
    private static final int MIN_CLAUSE_LENGTH = 55;
    private static final int TARGET_CHUNK_LENGTH = 180;
    private static final int EMERGENCY_CHUNK_LENGTH = 240;
    private static final Set<String> ABBREVIATIONS = Set.of(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr",
            "m", "mme", "mlle", "no", "tel", "tél", "st"
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
            if (isSentenceBoundary(text, i)
                    || isNaturalClauseBoundary(current, buffer.length())
                    || isSafeLengthBoundary(current, buffer.length())
                    || buffer.length() >= EMERGENCY_CHUNK_LENGTH) {
                addChunk(chunks, buffer);
            }
        }
        addChunk(chunks, buffer);
        return chunks;
    }

    private boolean isSentenceBoundary(String text, int index) {
        var current = text.charAt(index);
        if (current == '\n') {
            return true;
        }
        if (current != '.' && current != '?' && current != '!' && current != '…') {
            return false;
        }
        if (current == '.' && index < text.length() - 1 && isAbbreviation(text, index)) {
            return false;
        }
        return index == text.length() - 1 || Character.isWhitespace(text.charAt(index + 1));
    }

    private boolean isNaturalClauseBoundary(char current, int length) {
        return length >= MIN_CLAUSE_LENGTH
                && (current == ',' || current == ';' || current == ':' || current == '—');
    }

    private boolean isSafeLengthBoundary(char current, int length) {
        return length >= TARGET_CHUNK_LENGTH && Character.isWhitespace(current);
    }

    private boolean isAbbreviation(String text, int periodIndex) {
        var start = periodIndex - 1;
        while (start >= 0 && Character.isLetter(text.charAt(start))) {
            start--;
        }
        if (start == periodIndex - 1) {
            return false;
        }
        var token = text.substring(start + 1, periodIndex).toLowerCase(Locale.ROOT);
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
