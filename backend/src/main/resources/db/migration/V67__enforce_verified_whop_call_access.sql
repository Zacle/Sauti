UPDATE billing_accounts
SET enforcement_mode = 'enforce'
WHERE EXISTS (
    SELECT 1
    FROM billing_subscriptions bs
    WHERE bs.tenant_id = billing_accounts.tenant_id
      AND bs.provider = 'whop'
);
