package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V18__document_rag_with_pgvector extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var postgres = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
        try (Statement statement = connection.createStatement()) {
            if (postgres) statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("""
                    CREATE TABLE knowledge_documents (
                        id UUID PRIMARY KEY,
                        tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                        agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
                        file_name VARCHAR(255) NOT NULL,
                        media_type VARCHAR(150),
                        status VARCHAR(30) NOT NULL,
                        character_count INT NOT NULL DEFAULT 0,
                        chunk_count INT NOT NULL DEFAULT 0,
                        error_message VARCHAR(1000),
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE knowledge_chunks (
                        id UUID PRIMARY KEY,
                        document_id UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
                        tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                        agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
                        chunk_index INT NOT NULL,
                        content TEXT NOT NULL,
                        character_count INT NOT NULL,
                        embedding %s NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        UNIQUE (document_id, chunk_index)
                    )
                    """.formatted(postgres ? "vector(768)" : "VARCHAR"));
            statement.execute("CREATE INDEX idx_knowledge_documents_agent ON knowledge_documents(tenant_id, agent_id)");
            statement.execute("CREATE INDEX idx_knowledge_chunks_agent ON knowledge_chunks(tenant_id, agent_id)");
            if (postgres) {
                statement.execute("""
                        CREATE INDEX idx_knowledge_chunks_embedding
                        ON knowledge_chunks USING hnsw (embedding vector_cosine_ops)
                        """);
            }
        }
    }
}
