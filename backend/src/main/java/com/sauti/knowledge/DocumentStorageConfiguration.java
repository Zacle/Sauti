package com.sauti.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DocumentStorageConfiguration {
    @Bean
    @ConditionalOnProperty(name = "sauti.document-storage.provider", havingValue = "firebase")
    DocumentObjectStorage firebaseDocumentObjectStorage(
            @Value("${sauti.document-storage.firebase.project-id:}") String projectId,
            @Value("${sauti.document-storage.firebase.bucket:}") String bucket
    ) {
        return new FirebaseDocumentObjectStorage(projectId, bucket);
    }

    @Bean
    @ConditionalOnMissingBean(DocumentObjectStorage.class)
    DocumentObjectStorage databaseOnlyDocumentObjectStorage() {
        return new DatabaseOnlyDocumentObjectStorage();
    }
}
