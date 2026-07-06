package com.sauti.knowledge;

import java.util.UUID;

final class DatabaseOnlyDocumentObjectStorage implements DocumentObjectStorage {
    @Override
    public StoredObject upload(
            UUID tenantId,
            UUID agentId,
            UUID documentId,
            String fileName,
            String mediaType,
            byte[] content
    ) {
        return StoredObject.notStored();
    }

    @Override
    public void delete(String bucket, String objectName) {
        // No external object exists in database-only mode.
    }
}
