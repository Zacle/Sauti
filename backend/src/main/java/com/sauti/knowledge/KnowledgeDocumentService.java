package com.sauti.knowledge;

import com.sauti.agent.AgentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeDocumentService {
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_DOCUMENTS_PER_AGENT = 20;
    private final KnowledgeDocumentRepository documentRepository;
    private final AgentRepository agentRepository;
    private final DocumentChunker chunker;
    private final GeminiEmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentObjectStorage objectStorage;
    private final Tika tika = new Tika();
    private final int maxDocumentCharacters;
    private final boolean postgres;

    public KnowledgeDocumentService(
            KnowledgeDocumentRepository documentRepository,
            AgentRepository agentRepository,
            DocumentChunker chunker,
            GeminiEmbeddingService embeddingService,
            JdbcTemplate jdbcTemplate,
            DocumentObjectStorage objectStorage,
            DataSource dataSource,
            @Value("${sauti.rag.max-document-characters:100000}") int maxDocumentCharacters
    ) {
        this.documentRepository = documentRepository;
        this.agentRepository = agentRepository;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectStorage = objectStorage;
        this.maxDocumentCharacters = maxDocumentCharacters;
        this.postgres = isPostgres(dataSource);
        this.tika.setMaxStringLength(maxDocumentCharacters + 1);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> list(UUID tenantId, UUID agentId) {
        requireAgent(tenantId, agentId);
        return documentRepository.findAllByTenantIdAndAgentIdOrderByCreatedAtDesc(tenantId, agentId);
    }

    public KnowledgeDocument upload(UUID tenantId, UUID agentId, MultipartFile file) {
        requireAgent(tenantId, agentId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a non-empty document");
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("Document must be 10 MB or smaller");
        if (documentRepository.countByTenantIdAndAgentId(tenantId, agentId) >= MAX_DOCUMENTS_PER_AGENT) {
            throw new IllegalArgumentException("An agent can have at most 20 knowledge documents");
        }
        var fileName = safeFileName(file.getOriginalFilename());
        var original = read(file);
        var text = extract(original);
        if (text.isBlank()) throw new IllegalArgumentException("No readable text was found in " + fileName);
        if (text.length() > maxDocumentCharacters) {
            throw new IllegalArgumentException(
                    "Extracted document exceeds the " + maxDocumentCharacters + " character limit"
            );
        }
        var chunks = chunker.chunks(text);
        if (chunks.isEmpty()) throw new IllegalArgumentException("No indexable text was found in " + fileName);
        var embeddings = chunks.stream().map(embeddingService::embedDocument).toList();
        var document = new KnowledgeDocument(
                tenantId, agentId, fileName, file.getContentType(), text.length(), chunks.size()
        );
        var storedObject = objectStorage.upload(
                tenantId,
                agentId,
                document.getId(),
                fileName,
                file.getContentType(),
                original
        );
        document.attachStoredObject(storedObject);
        document = documentRepository.saveAndFlush(document);
        try {
            for (int index = 0; index < chunks.size(); index++) {
                var insert = postgres ? """
                                INSERT INTO knowledge_chunks
                                    (id, document_id, tenant_id, agent_id, chunk_index, content,
                                     character_count, embedding, created_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?)
                                """ : """
                                INSERT INTO knowledge_chunks
                                    (id, document_id, tenant_id, agent_id, chunk_index, content,
                                     character_count, embedding, created_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """;
                jdbcTemplate.update(insert,
                        UUID.randomUUID(),
                        document.getId(),
                        tenantId,
                        agentId,
                        index,
                        chunks.get(index),
                        chunks.get(index).length(),
                        embeddingService.vectorLiteral(embeddings.get(index)),
                        OffsetDateTime.now()
                );
            }
            return document;
        } catch (Exception exception) {
            documentRepository.deleteById(document.getId());
            objectStorage.delete(storedObject.bucket(), storedObject.objectName());
            throw new IllegalStateException("Could not persist document embeddings", exception);
        }
    }

    @Transactional
    public void delete(UUID tenantId, UUID agentId, UUID documentId) {
        requireAgent(tenantId, agentId);
        var document = documentRepository.findByIdAndTenantIdAndAgentId(documentId, tenantId, agentId)
                .orElseThrow(() -> new EntityNotFoundException("Knowledge document not found"));
        objectStorage.delete(document.getStorageBucket(), document.getStorageObjectName());
        documentRepository.delete(document);
    }

    private byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not read the selected document", exception);
        }
    }

    private String extract(byte[] content) {
        try (var input = new ByteArrayInputStream(content)) {
            return tika.parseToString(input).replace("\u0000", "").trim();
        } catch (org.apache.tika.exception.ZeroByteFileException exception) {
            throw new IllegalArgumentException("The selected document is empty", exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not read this document format", exception);
        }
    }

    private void requireAgent(UUID tenantId, UUID agentId) {
        if (agentRepository.findByIdAndTenantId(agentId, tenantId).isEmpty()) {
            throw new EntityNotFoundException("Agent not found");
        }
    }

    private String safeFileName(String value) {
        var name = value == null || value.isBlank() ? "knowledge-document" : Path.of(value).getFileName().toString();
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private boolean isPostgres(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
        } catch (Exception exception) {
            throw new IllegalStateException("Could not inspect the knowledge database", exception);
        }
    }
}
