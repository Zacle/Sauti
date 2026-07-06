package db.migration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V27__seed_native_integration_tools extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var tools = List.of(
                new Tool("send_whatsapp_message", "Send an approved WhatsApp template to the caller.",
                        """
                        {"type":"object","properties":{"phone":{"type":"string","format":"phone","description":"Recipient phone number"}},"required":["phone"]}
                        """, 60),
                new Tool("lookup_google_sheet_row", "Look up a configured Google Sheets row.",
                        """
                        {"type":"object","properties":{"lookup_value":{"type":"string","description":"Value in the configured lookup column"}},"required":["lookup_value"]}
                        """, 70),
                new Tool("request_mpesa_payment", "Request a caller-confirmed M-Pesa STK Push.",
                        """
                        {"type":"object","properties":{"phone":{"type":"string","format":"phone"},"amount":{"type":"number"},"amount_confirmed":{"type":"boolean"},"account_reference":{"type":"string"},"description":{"type":"string"}},"required":["phone","amount","amount_confirmed","account_reference","description"]}
                        """, 80),
                new Tool("call_custom_webhook", "Send structured data to the configured HTTPS webhook.",
                        """
                        {"type":"object","properties":{"payload":{"type":"object","description":"Structured request payload"}},"required":["payload"]}
                        """, 90)
        );
        try (var agents = context.getConnection().prepareStatement("SELECT id FROM agents").executeQuery();
             var exists = context.getConnection().prepareStatement(
                     "SELECT COUNT(*) FROM agent_tools WHERE agent_id = ? AND tool_name = ?");
             var insert = context.getConnection().prepareStatement("""
                     INSERT INTO agent_tools
                       (id, agent_id, tool_name, tool_description, parameters_schema, fulfillment_type,
                        webhook_method, auth_type, is_active, display_order, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, 'sauti_integration', 'POST', 'none', FALSE, ?, ?, ?)
                     """)) {
            while (agents.next()) {
                var agentId = agents.getObject(1);
                for (var tool : tools) {
                    exists.setObject(1, agentId);
                    exists.setString(2, tool.name());
                    try (var result = exists.executeQuery()) {
                        result.next();
                        if (result.getInt(1) > 0) continue;
                    }
                    var now = OffsetDateTime.now();
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, agentId);
                    insert.setString(3, tool.name());
                    insert.setString(4, tool.description());
                    insert.setString(5, tool.schema().trim());
                    insert.setInt(6, tool.order());
                    insert.setObject(7, now);
                    insert.setObject(8, now);
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
    }

    private record Tool(String name, String description, String schema, int order) {}
}
