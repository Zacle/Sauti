package com.sauti.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {
    static final int CHUNK_SIZE = 1_200;
    static final int OVERLAP = 160;
    static final int MAX_CHUNKS = 100;

    public List<String> chunks(String text) {
        var normalized = text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (normalized.isBlank()) return List.of();
        var chunks = new ArrayList<String>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            if (end < normalized.length()) {
                int boundary = Math.max(
                        normalized.lastIndexOf("\n\n", end),
                        normalized.lastIndexOf(". ", end)
                );
                if (boundary > start + CHUNK_SIZE / 2) end = boundary + 1;
            }
            var chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= normalized.length()) break;
            start = Math.max(start + 1, end - OVERLAP);
        }
        return List.copyOf(chunks);
    }
}
