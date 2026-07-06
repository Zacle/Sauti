package com.sauti.knowledge;

import java.util.UUID;

public interface DocumentObjectStorage {
    StoredObject upload(
            UUID tenantId,
            UUID agentId,
            UUID documentId,
            String fileName,
            String mediaType,
            byte[] content
    );

    void delete(String bucket, String objectName);

    record StoredObject(String bucket, String objectName, long sizeBytes) {
        public static StoredObject notStored() {
            return new StoredObject(null, null, 0);
        }

        public boolean stored() {
            return objectName != null && !objectName.isBlank();
        }
    }
}
