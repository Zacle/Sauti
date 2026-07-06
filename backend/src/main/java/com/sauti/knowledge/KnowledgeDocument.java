package com.sauti.knowledge;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument extends Auditable {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID agentId;
    @Column(nullable = false)
    private String fileName;
    private String mediaType;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private int characterCount;
    @Column(nullable = false)
    private int chunkCount;
    private String errorMessage;
    private String storageBucket;
    @Column(length = 1000)
    private String storageObjectName;
    private Long originalSizeBytes;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(
            UUID tenantId,
            UUID agentId,
            String fileName,
            String mediaType,
            int characterCount,
            int chunkCount
    ) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.status = "ready";
        this.characterCount = characterCount;
        this.chunkCount = chunkCount;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgentId() { return agentId; }
    public String getFileName() { return fileName; }
    public String getMediaType() { return mediaType; }
    public String getStatus() { return status; }
    public int getCharacterCount() { return characterCount; }
    public int getChunkCount() { return chunkCount; }
    public String getErrorMessage() { return errorMessage; }
    public String getStorageBucket() { return storageBucket; }
    public String getStorageObjectName() { return storageObjectName; }
    public Long getOriginalSizeBytes() { return originalSizeBytes; }
    public boolean isOriginalStored() {
        return storageObjectName != null && !storageObjectName.isBlank();
    }

    public void attachStoredObject(DocumentObjectStorage.StoredObject storedObject) {
        if (storedObject == null || !storedObject.stored()) return;
        this.storageBucket = storedObject.bucket();
        this.storageObjectName = storedObject.objectName();
        this.originalSizeBytes = storedObject.sizeBytes();
    }
}
