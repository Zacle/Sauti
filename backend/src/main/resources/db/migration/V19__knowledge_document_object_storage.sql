ALTER TABLE knowledge_documents
    ADD COLUMN storage_bucket VARCHAR(255);

ALTER TABLE knowledge_documents
    ADD COLUMN storage_object_name VARCHAR(1000);

ALTER TABLE knowledge_documents
    ADD COLUMN original_size_bytes BIGINT;
