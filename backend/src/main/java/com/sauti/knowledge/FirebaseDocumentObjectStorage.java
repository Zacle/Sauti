package com.sauti.knowledge;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import java.util.UUID;

final class FirebaseDocumentObjectStorage implements DocumentObjectStorage {
    private final Bucket bucket;

    FirebaseDocumentObjectStorage(String projectId, String bucketName) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID is required when Firebase document storage is enabled");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("FIREBASE_STORAGE_BUCKET is required when Firebase document storage is enabled");
        }
        try {
            var options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(projectId)
                    .setStorageBucket(normalizeBucket(bucketName))
                    .build();
            var app = FirebaseApp.getApps().stream()
                    .filter(candidate -> "sauti-document-storage".equals(candidate.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, "sauti-document-storage"));
            this.bucket = StorageClient.getInstance(app).bucket();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not initialize Firebase document storage. Check GOOGLE_APPLICATION_CREDENTIALS.",
                    exception
            );
        }
    }

    @Override
    public StoredObject upload(
            UUID tenantId,
            UUID agentId,
            UUID documentId,
            String fileName,
            String mediaType,
            byte[] content
    ) {
        var objectName = "documents/%s/%s/%s/%s".formatted(
                tenantId,
                agentId,
                documentId,
                fileName.replace("\\", "_").replace("/", "_")
        );
        try {
            bucket.create(
                    objectName,
                    content,
                    mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType
            );
            return new StoredObject(bucket.getName(), objectName, content.length);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not store the original document in Firebase Storage", exception);
        }
    }

    @Override
    public void delete(String bucketName, String objectName) {
        if (objectName == null || objectName.isBlank()) return;
        try {
            var object = bucket.get(objectName);
            if (object != null && !object.delete()) {
                throw new IllegalStateException("Firebase Storage did not delete the document object");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not delete the original document from Firebase Storage", exception);
        }
    }

    private static String normalizeBucket(String value) {
        return value.trim().replaceFirst("^gs://", "").replaceFirst("/+$", "");
    }
}
