package db.migration;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V28__seed_mpesa_status_tool extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (var agents = context.getConnection().prepareStatement("""
                SELECT id FROM agents a WHERE NOT EXISTS (
                  SELECT 1 FROM agent_tools t WHERE t.agent_id = a.id AND t.tool_name = 'check_mpesa_payment'
                )
                """).executeQuery();
             var insert = context.getConnection().prepareStatement("""
                INSERT INTO agent_tools
                  (id, agent_id, tool_name, tool_description, parameters_schema, fulfillment_type,
                   webhook_method, auth_type, is_active, display_order, created_at, updated_at)
                VALUES (?, ?, 'check_mpesa_payment', ?, ?, 'sauti_integration',
                        'POST', 'none', FALSE, 85, ?, ?)
                """)) {
            while (agents.next()) {
                var now = OffsetDateTime.now();
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, agents.getObject(1));
                insert.setString(3, "Check whether an M-Pesa request from this call completed.");
                insert.setString(4, """
                        {"type":"object","properties":{"payment_request_id":{"type":"string","description":"ID returned by request_mpesa_payment"}},"required":["payment_request_id"]}
                        """.trim());
                insert.setObject(5, now);
                insert.setObject(6, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
