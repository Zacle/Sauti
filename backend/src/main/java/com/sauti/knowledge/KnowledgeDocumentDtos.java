package com.sauti.knowledge;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class KnowledgeDocumentDtos {
    private KnowledgeDocumentDtos() {
    }

    public record KnowledgeDocumentResponse(
            UUID id,
            String fileName,
            String mediaType,
            String status,
            int characterCount,
            int chunkCount,
            boolean originalStored,
            Long originalSizeBytes,
            String errorMessage,
            OffsetDateTime createdAt
    ) {
        public static KnowledgeDocumentResponse from(KnowledgeDocument document) {
            return new KnowledgeDocumentResponse(
                    document.getId(),
                    document.getFileName(),
                    document.getMediaType(),
                    document.getStatus(),
                    document.getCharacterCount(),
                    document.getChunkCount(),
                    document.isOriginalStored(),
                    document.getOriginalSizeBytes(),
                    document.getErrorMessage(),
                    document.getCreatedAt()
            );
        }
    }
}
