package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trigram indexes for searching calls by what was said ({@code lower(transcript) like '%…%'}) and
 * by their summary. Postgres only: local H2 has no trigram support and searches small data anyway.
 *
 * <p>Creating the {@code pg_trgm} extension can be refused on a managed database. The search
 * works without the index, only slower, so a refusal is logged and skipped instead of failing the
 * deploy. The work runs under a savepoint so a refusal does not abort the migration transaction.
 */
public class V84__call_search_index extends BaseJavaMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V84__call_search_index.class);

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        // Flyway runs this in a transaction; a savepoint keeps a refusal from aborting it.
        var savepoint = connection.getAutoCommit() ? null : connection.setSavepoint("call_search_index");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_calls_transcript_trgm "
                    + "ON calls USING gin (lower(transcript) gin_trgm_ops)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_calls_summary_trgm "
                    + "ON calls USING gin (lower(call_summary) gin_trgm_ops)");
            if (savepoint != null) connection.releaseSavepoint(savepoint);
        } catch (java.sql.SQLException refused) {
            if (savepoint != null) connection.rollback(savepoint);
            LOGGER.warn("Call search indexes were not created; search still works, without them: {}",
                    refused.getMessage());
        }
    }
}
